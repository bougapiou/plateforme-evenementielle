package bf.evenements.plateforme.event;

import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.event.dto.ActivityRequest;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Bulk import of a programme from the Excel / CSV template (ORG-01). A dry run
 * returns a line-by-line preview with errors; the real run creates the valid
 * lines and reports the invalid ones without blocking the others.
 */
@Service
@RequiredArgsConstructor
public class ProgrammeImportService {

    static final ZoneId ZONE = ZoneId.of("Africa/Ouagadougou");

    /** Template columns, in order. */
    static final List<String> COLUMNS = List.of("Titre", "Type", "Début", "Fin", "Salle", "Intervenants",
            "Modérateur", "Description", "Thème", "Accès", "Capacité", "Format", "Lien de diffusion");

    private static final List<DateTimeFormatter> DATE_TIMES = List.of(
            DateTimeFormatter.ofPattern("d/M/yyyy H:mm"), DateTimeFormatter.ofPattern("d/M/yyyy H'h'mm"),
            DateTimeFormatter.ofPattern("d/M/yyyy H:mm:ss"), DateTimeFormatter.ofPattern("yyyy-M-d H:mm"),
            DateTimeFormatter.ofPattern("yyyy-M-d'T'H:mm"), DateTimeFormatter.ofPattern("d-M-yyyy H:mm"));

    private final EventActivityService activityService;

    public record Line(int ligne, String titre, @Nullable String debut, @Nullable String fin,
                       @Nullable String salle, List<String> erreurs, @Nullable UUID activityId) {
    }

    public record Report(int total, int valides, int importees, List<Line> lignes) {
    }

    public Report importFile(UUID eventId, String filename, InputStream in, boolean dryRun) {
        List<Map<String, String>> rows = read(filename, in);
        List<Line> out = new ArrayList<>();
        int valid = 0;
        int created = 0;
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            if (row.values().stream().allMatch(v -> v == null || v.isBlank())) {
                continue;
            }
            int lineNo = i + 2; // header is line 1
            List<String> errors = new ArrayList<>();
            ActivityRequest request = toRequest(row, errors);
            UUID id = null;
            if (errors.isEmpty()) {
                valid++;
                if (!dryRun) {
                    try {
                        id = activityService.create(eventId, request).id();
                        created++;
                    } catch (RuntimeException e) {
                        errors.add(e.getMessage());
                    }
                }
            }
            out.add(new Line(lineNo, get(row, "titre"), get(row, "debut"), get(row, "fin"), get(row, "salle"),
                    errors, id));
        }
        if (out.isEmpty()) {
            throw new BusinessException("IMPORT_EMPTY", "Le fichier ne contient aucune activité.");
        }
        return new Report(out.size(), valid, created, out);
    }

    // ------------------------------------------------------------------ mapping

    private ActivityRequest toRequest(Map<String, String> row, List<String> errors) {
        String titre = get(row, "titre");
        if (titre == null) {
            errors.add("Titre manquant");
        } else if (titre.length() > 200) {
            errors.add("Titre trop long (200 caractères max)");
        }
        Instant debut = dateTime(get(row, "debut"), "Début", errors, true);
        Instant fin = dateTime(get(row, "fin"), "Fin", errors, false);
        if (debut != null && fin != null && !fin.isAfter(debut)) {
            errors.add("La fin doit être après le début");
        }
        ActivityType type = parseEnum(ActivityType.class, get(row, "type"), "Type", errors, ActivityType.AUTRE,
                Map.of("TABLERONDE", ActivityType.TABLE_RONDE, "EMISSIONMEDIA", ActivityType.EMISSION_MEDIA,
                        "EMISSION", ActivityType.EMISSION_MEDIA, "DEMO", ActivityType.DEMONSTRATION,
                        "CONFERENCEINAUGURALE", ActivityType.KEYNOTE, "OUVERTURE", ActivityType.CEREMONIE,
                        "CLOTURE", ActivityType.CEREMONIE));
        ActivityAccess acces = parseEnum(ActivityAccess.class, get(row, "acces"), "Accès", errors,
                ActivityAccess.SANS_BILLET, Map.of("LIBRE", ActivityAccess.SANS_BILLET,
                        "SANSBILLET", ActivityAccess.SANS_BILLET, "INSCRIPTION", ActivityAccess.GRATUIT));
        ActivityFormat format = parseEnum(ActivityFormat.class, get(row, "format"), "Format", errors,
                ActivityFormat.SUR_PLACE, Map.of("SURPLACE", ActivityFormat.SUR_PLACE,
                        "PRESENTIEL", ActivityFormat.SUR_PLACE, "ENLIGNE", ActivityFormat.EN_LIGNE));
        Integer capacite = null;
        String cap = get(row, "capacite");
        if (cap != null) {
            try {
                capacite = (int) Double.parseDouble(cap.replace(" ", "").replace(",", "."));
                if (capacite < 0) {
                    errors.add("Capacité négative");
                }
            } catch (NumberFormatException e) {
                errors.add("Capacité invalide : « " + cap + " »");
            }
        }
        return new ActivityRequest(titre == null ? "" : titre, get(row, "description"), type, acces,
                debut, fin, trunc(get(row, "salle"), 120), null, trunc(get(row, "intervenants"), 255),
                trunc(get(row, "moderateur"), 255), null, trunc(get(row, "theme"), 120), format,
                trunc(get(row, "lien"), 500), null, capacite, null);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, @Nullable String raw, String label,
                                                   List<String> errors, E fallback, Map<String, E> aliases) {
        if (raw == null) {
            return fallback;
        }
        String key = fold(raw).replaceAll("[^A-Z]", "");
        for (E e : type.getEnumConstants()) {
            if (e.name().replace("_", "").equals(key)) {
                return e;
            }
        }
        E alias = aliases.get(key);
        if (alias != null) {
            return alias;
        }
        errors.add(label + " inconnu : « " + raw + " »");
        return fallback;
    }

    @Nullable
    private static Instant dateTime(@Nullable String raw, String label, List<String> errors, boolean required) {
        if (raw == null) {
            if (required) {
                errors.add(label + " manquant");
            }
            return null;
        }
        String v = raw.trim().replaceAll("\\s+", " ");
        for (DateTimeFormatter f : DATE_TIMES) {
            try {
                return LocalDateTime.parse(v, f).atZone(ZONE).toInstant();
            } catch (DateTimeParseException ignored) {
                // next pattern
            }
        }
        errors.add(label + " invalide : « " + raw + " » (attendu JJ/MM/AAAA HH:MM)");
        return null;
    }

    @Nullable
    private static String trunc(@Nullable String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }

    // ------------------------------------------------------------------ reading

    /** Rows keyed by normalised column name (titre, type, debut, fin, salle…). */
    private List<Map<String, String>> read(String filename, InputStream in) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = in.readAllBytes();
            return name.endsWith(".csv") || name.endsWith(".txt") ? readCsv(bytes) : readExcel(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Map<String, String>> readExcel(byte[] bytes) throws IOException {
        try (Workbook wb = WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter(Locale.FRANCE);
            List<String> headers = null;
            List<Map<String, String>> rows = new ArrayList<>();
            for (Row r : sheet) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < Math.max(r.getLastCellNum(), 0); c++) {
                    cells.add(cellText(r.getCell(c), fmt));
                }
                if (headers == null) {
                    if (cells.stream().anyMatch(x -> "TITRE".equals(fold(x)))) {
                        headers = cells.stream().map(ProgrammeImportService::key).toList();
                    }
                    continue;
                }
                rows.add(toMap(headers, cells));
            }
            if (headers == null) {
                throw new BusinessException("IMPORT_NO_HEADER",
                        "Ligne d'en-tête introuvable : utilisez le modèle (colonne « Titre » obligatoire).");
            }
            return rows;
        } catch (org.apache.poi.EmptyFileException | org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException e) {
            throw new BusinessException("IMPORT_BAD_FILE", "Fichier illisible : envoyez un .xlsx ou un .csv.");
        }
    }

    private static String cellText(@Nullable Cell cell, DataFormatter fmt) {
        if (cell == null) {
            return "";
        }
        CellType t = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (t == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime d = cell.getLocalDateTimeCellValue();
            return d.toLocalTime().equals(LocalTime.MIDNIGHT) && d.toLocalDate().isBefore(LocalDate.of(1900, 2, 1))
                    ? d.toLocalTime().toString()
                    : d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        }
        return fmt.formatCellValue(cell).trim();
    }

    private List<Map<String, String>> readCsv(byte[] bytes) throws IOException {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(text))) {
            String first = reader.readLine();
            if (first == null) {
                return List.of();
            }
            char sep = first.chars().filter(ch -> ch == ';').count() >= first.chars().filter(ch -> ch == ',').count()
                    ? ';' : ',';
            lines.add(splitCsv(first, sep));
            StringBuilder pending = new StringBuilder();
            String l;
            while ((l = reader.readLine()) != null) {
                pending.append(pending.isEmpty() ? "" : "\n").append(l);
                if (pending.chars().filter(ch -> ch == '"').count() % 2 == 0) {
                    lines.add(splitCsv(pending.toString(), sep));
                    pending.setLength(0);
                }
            }
        }
        List<String> headers = lines.get(0).stream().map(ProgrammeImportService::key).toList();
        if (!headers.contains("titre")) {
            throw new BusinessException("IMPORT_NO_HEADER",
                    "Ligne d'en-tête introuvable : utilisez le modèle (colonne « Titre » obligatoire).");
        }
        return lines.subList(1, lines.size()).stream().map(cells -> toMap(headers, cells)).toList();
    }

    private static List<String> splitCsv(String line, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == sep) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }

    private static Map<String, String> toMap(List<String> headers, List<String> cells) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String v = i < cells.size() ? cells.get(i) : "";
            m.put(headers.get(i), v == null || v.isBlank() ? null : v.trim());
        }
        return m;
    }

    @Nullable
    private static String get(Map<String, String> row, String key) {
        return row.get(key);
    }

    /** Column header → key (accents, case and spacing ignored; a few synonyms). */
    private static String key(String header) {
        String h = fold(header).replaceAll("[^A-Z]", "");
        return switch (h) {
            case "TITRE", "INTITULE", "ACTIVITE", "SESSION" -> "titre";
            case "TYPE", "TYPEDACTIVITE" -> "type";
            case "DEBUT", "DATEDEBUT", "DATEETHEUREDEDEBUT", "HEUREDEBUT" -> "debut";
            case "FIN", "DATEFIN", "DATEETHEUREDEFIN", "HEUREFIN" -> "fin";
            case "SALLE", "LIEU" -> "salle";
            case "INTERVENANTS", "INTERVENANT", "PANELISTES", "ORATEURS" -> "intervenants";
            case "MODERATEUR", "MODERATRICE" -> "moderateur";
            case "DESCRIPTION", "RESUME" -> "description";
            case "THEME", "PISTE", "THEMEPISTE" -> "theme";
            case "ACCES", "MODEDACCES" -> "acces";
            case "CAPACITE", "PLACES" -> "capacite";
            case "FORMAT" -> "format";
            case "LIENDEDIFFUSION", "LIEN", "LIENDIFFUSION" -> "lien";
            default -> h.toLowerCase(Locale.ROOT);
        };
    }

    private static String fold(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT).trim();
    }

    // ------------------------------------------------------------------ template

    /** Excel template with the expected columns, two example lines and a help sheet. */
    public byte[] template() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Programme");
            var bold = wb.createFont();
            bold.setBold(true);
            var head = wb.createCellStyle();
            head.setFont(bold);
            Row h = sheet.createRow(0);
            for (int i = 0; i < COLUMNS.size(); i++) {
                Cell c = h.createCell(i);
                c.setCellValue(COLUMNS.get(i));
                c.setCellStyle(head);
            }
            String[][] examples = {
                    {"Cérémonie d'ouverture", "Cérémonie", "24/11/2026 09:00", "24/11/2026 11:00", "Salle des banquets",
                            "", "", "Ouverture officielle de la 21e Semaine du Numérique", "", "Sans billet", "", "Sur place", ""},
                    {"Conf'Day 3 : IA et services publics", "Panel", "26/11/2026 09:30", "26/11/2026 12:00", "Kilimandjaro",
                            "A. Ouédraogo ; B. Traoré", "C. Sawadogo", "Résumé du panel…", "IA", "Gratuit", "250",
                            "Hybride", "https://youtube.com/…"},
            };
            for (int r = 0; r < examples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int i = 0; i < examples[r].length; i++) {
                    row.createCell(i).setCellValue(examples[r][i]);
                }
            }
            for (int i = 0; i < COLUMNS.size(); i++) {
                sheet.setColumnWidth(i, 22 * 256);
            }
            Sheet help = wb.createSheet("Aide");
            String[] lines = {
                    "Une ligne par activité. Colonnes obligatoires : Titre, Début.",
                    "Dates : JJ/MM/AAAA HH:MM (ex. 26/11/2026 09:30), heure de Ouagadougou.",
                    "Type : Cérémonie, Keynote, Conférence, Panel, Table ronde, Forum, Atelier, Formation, "
                            + "Démonstration, Exposition, Émission média, Networking, Spectacle, Gala, Pause, Autre.",
                    "Accès : Sans billet (par défaut), Gratuit (billet gratuit créé automatiquement), Payant.",
                    "Format : Sur place (par défaut), En ligne, Hybride ; Lien de diffusion pour les deux derniers.",
                    "Une activité sur plusieurs jours (exposition) : Début le 1er jour, Fin le dernier jour.",
                    "Les lignes en erreur sont signalées et ignorées ; les autres sont importées.",
            };
            for (int i = 0; i < lines.length; i++) {
                help.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            help.setColumnWidth(0, 120 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
