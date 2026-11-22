package bf.evenements.plateforme.event;

import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.event.dto.ActivityRequest;
import bf.evenements.plateforme.ticket.EventTicketRepository;
import bf.evenements.plateforme.ticket.EventTicketService;
import bf.evenements.plateforme.ticket.IdentiteRequise;
import bf.evenements.plateforme.ticket.TicketScope;
import bf.evenements.plateforme.ticket.dto.EventTicketRequest;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
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
import java.util.Set;
import java.util.UUID;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bulk import of a programme from the Excel / CSV template (ORG-01). A dry run
 * returns a line-by-line preview with errors; the real run creates the valid
 * lines and reports the invalid ones without blocking the others.
 */
@Service
public class ProgrammeImportService {

    static final ZoneId ZONE = ZoneId.of("Africa/Ouagadougou");

    /** Template columns, in order. */
    static final List<String> COLUMNS = List.of("Titre", "Type", "Début", "Fin", "Salle", "Intervenants",
            "Modérateur", "Description", "Thème", "Accès", "Prix (FCFA)", "Champs à remplir", "Capacité",
            "Format", "Lien de diffusion");

    /** Quota of an imported ticket category when the activity has no capacity. */
    private static final int OPEN_QUOTA = 100_000;

    private static final List<DateTimeFormatter> DATE_TIMES = List.of(
            DateTimeFormatter.ofPattern("d/M/yyyy H:mm"), DateTimeFormatter.ofPattern("d/M/yyyy H'h'mm"),
            DateTimeFormatter.ofPattern("d/M/yyyy H:mm:ss"), DateTimeFormatter.ofPattern("yyyy-M-d H:mm"),
            DateTimeFormatter.ofPattern("yyyy-M-d'T'H:mm"), DateTimeFormatter.ofPattern("d-M-yyyy H:mm"));

    private final EventActivityService activityService;
    private final EventTicketService ticketService;
    private final EventTicketRepository ticketRepository;
    private final TransactionTemplate tx;

    public ProgrammeImportService(EventActivityService activityService, EventTicketService ticketService,
                                  EventTicketRepository ticketRepository, PlatformTransactionManager txManager) {
        this.activityService = activityService;
        this.ticketService = ticketService;
        this.ticketRepository = ticketRepository;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * One preview line. {@code acces} summarises the access mode, the price and
     * the fields of the registration form (e.g. "Payant · 5000 FCFA").
     */
    public record Line(int ligne, String titre, @Nullable String debut, @Nullable String fin,
                       @Nullable String salle, @Nullable String acces, List<String> erreurs,
                       @Nullable UUID activityId) {
    }

    /** Ticketing columns of a line: price (paid only) and form fields (free only). */
    private record Ticketing(@Nullable BigDecimal prix, boolean formulaireRequis, IdentiteRequise identite,
                             String resume) {
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
            String debutRaw = debutRaw(row);
            String finRaw = finRaw(row);
            ActivityRequest request = toRequest(row, debutRaw, finRaw, errors);
            Ticketing ticketing = ticketing(row, request, errors);
            UUID id = null;
            if (errors.isEmpty()) {
                valid++;
                if (!dryRun) {
                    try {
                        id = tx.execute(s -> createLine(eventId, request, ticketing));
                        created++;
                    } catch (RuntimeException e) {
                        errors.add(e.getMessage());
                    }
                }
            }
            out.add(new Line(lineNo, get(row, "titre"), debutRaw, finRaw, get(row, "salle"),
                    ticketing.resume(), errors, id));
        }
        if (out.isEmpty()) {
            throw new BusinessException("IMPORT_EMPTY", "Le fichier ne contient aucune activité.");
        }
        return new Report(out.size(), valid, created, out);
    }

    /**
     * Creates the activity and its ticketing in one transaction: the auto-managed
     * free ticket gets the requested form fields; a paid activity gets its own
     * ACTIVITE-scoped category at the given price.
     */
    private UUID createLine(UUID eventId, ActivityRequest request, Ticketing t) {
        var activity = activityService.create(eventId, request);
        if (request.resolvedAcces() == ActivityAccess.GRATUIT && activity.freeTicketId() != null) {
            ticketRepository.findById(activity.freeTicketId()).ifPresent(ft -> {
                ft.setFormulaireRequis(t.formulaireRequis());
                ft.setIdentiteRequise(t.identite());
            });
        } else if (request.resolvedAcces() == ActivityAccess.PAYANT) {
            int quota = request.capacite() != null && request.capacite() > 0 ? request.capacite() : OPEN_QUOTA;
            ticketService.create(eventId, new EventTicketRequest(trunc("Billet — " + request.titre(), 120),
                    "Accès payant à l'activité « " + request.titre() + " »", t.prix(), null,
                    TicketScope.ACTIVITE, quota, null, null, null, true, null, true,
                    IdentiteRequise.NOM_ET_PRENOM, Set.of(activity.id())));
        }
        return activity.id();
    }

    // ------------------------------------------------------------------ mapping

    private ActivityRequest toRequest(Map<String, String> row, @Nullable String debutRaw, @Nullable String finRaw,
                                      List<String> errors) {
        String titre = get(row, "titre");
        if (titre == null) {
            errors.add("Titre manquant");
        } else if (titre.length() > 200) {
            errors.add("Titre trop long (200 caractères max)");
        }
        Instant debut = dateTime(debutRaw, "Début", errors, true);
        Instant fin = dateTime(finRaw, "Fin", errors, false);
        if (debut != null && fin != null && !fin.isAfter(debut)) {
            errors.add("La fin doit être après le début");
        }
        ActivityType type = parseType(get(row, "type"), errors);
        ActivityAccess acces = parseEnum(ActivityAccess.class, get(row, "acces"), "Accès", errors,
                defaultAccess(get(row, "prix")), Map.of("LIBRE", ActivityAccess.SANS_BILLET,
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
        return new ActivityRequest(titre == null ? "" : titre, description(row), type, acces,
                debut, fin, trunc(get(row, "salle"), 120), null, trunc(get(row, "intervenants"), 255),
                trunc(get(row, "moderateur"), 255), null, trunc(get(row, "theme"), 120), format,
                trunc(get(row, "lien"), 500), null, capacite, null);
    }

    /** Description, followed by the "Porteur" column when the file has one (no dedicated field). */
    @Nullable
    private static String description(Map<String, String> row) {
        String d = get(row, "description");
        String porteur = get(row, "porteur");
        if (porteur == null) {
            return d;
        }
        return (d == null ? "" : d + "\n\n") + "Porteur : " + porteur;
    }

    /** A blank "Accès" follows the price: a price above 0 means paid, 0 means free ticket. */
    private static ActivityAccess defaultAccess(@Nullable String prix) {
        BigDecimal p = prix == null ? null : amount(prix);
        if (p == null) {
            return ActivityAccess.SANS_BILLET;
        }
        return p.signum() > 0 ? ActivityAccess.PAYANT : ActivityAccess.GRATUIT;
    }

    private static Ticketing ticketing(Map<String, String> row, ActivityRequest request, List<String> errors) {
        ActivityAccess acces = request.resolvedAcces();
        String prixRaw = get(row, "prix");
        BigDecimal prix = null;
        if (prixRaw != null) {
            prix = amount(prixRaw);
            if (prix == null) {
                errors.add("Prix invalide : « " + prixRaw + " »");
            } else if (prix.signum() < 0) {
                errors.add("Prix négatif");
            }
        }
        boolean hasPrice = prix != null && prix.signum() > 0;
        if (acces == ActivityAccess.PAYANT && !hasPrice && prixRaw == null) {
            errors.add("Prix manquant pour une activité payante");
        } else if (acces == ActivityAccess.PAYANT && prix != null && prix.signum() == 0) {
            errors.add("Prix à 0 pour une activité payante : mettez l'accès « Gratuit »");
        } else if (acces != ActivityAccess.PAYANT && hasPrice) {
            errors.add("Prix renseigné mais l'accès n'est pas « Payant »");
        }

        // Phone is always asked (it's how a claimed ticket is found again); a paid
        // category always collects the full identity.
        boolean nom = true;
        boolean prenom = true;
        String champsRaw = get(row, "champs");
        if (champsRaw != null) {
            nom = false;
            prenom = false;
            for (String part : champsRaw.split("[,;/+&\\n]|\\s+et\\s+|\\s+ET\\s+")) {
                String k = fold(part).replaceAll("[^A-Z]", "");
                switch (k) {
                    case "" -> { }
                    case "NOM", "NOMS", "NOMDEFAMILLE" -> nom = true;
                    case "PRENOM", "PRENOMS" -> prenom = true;
                    case "NOMPRENOM", "NOMPRENOMS", "NOMETPRENOM", "NOMETPRENOMS", "NOMCOMPLET" -> {
                        nom = true;
                        prenom = true;
                    }
                    case "TELEPHONE", "TEL", "PHONE", "NUMERO", "NUMERODETELEPHONE", "CONTACT" -> { }
                    default -> errors.add("Champ inconnu : « " + part.trim()
                            + " » (champs possibles : Nom, Prénom, Téléphone)");
                }
            }
        }
        if (acces == ActivityAccess.PAYANT) {
            nom = true;
            prenom = true;
        }
        IdentiteRequise identite = nom && prenom ? IdentiteRequise.NOM_ET_PRENOM
                : nom ? IdentiteRequise.NOM_SEUL : prenom ? IdentiteRequise.PRENOM_SEUL : IdentiteRequise.NOM_ET_PRENOM;
        boolean formulaire = nom || prenom;

        String resume = switch (acces) {
            case SANS_BILLET -> "Sans billet";
            case GRATUIT -> "Gratuit · " + fields(formulaire, identite);
            case PAYANT -> "Payant · " + (prix == null ? "?" : prix.stripTrailingZeros().toPlainString())
                    + " FCFA · " + fields(true, IdentiteRequise.NOM_ET_PRENOM);
        };
        return new Ticketing(prix, formulaire, identite, resume);
    }

    private static String fields(boolean formulaire, IdentiteRequise identite) {
        if (!formulaire) {
            return "Téléphone";
        }
        return switch (identite) {
            case NOM_ET_PRENOM -> "Nom, Prénom, Téléphone";
            case NOM_SEUL -> "Nom, Téléphone";
            case PRENOM_SEUL -> "Prénom, Téléphone";
        };
    }

    /** "5 000", "5000 FCFA", "2500,50" → amount; null when unreadable. */
    @Nullable
    private static BigDecimal amount(String raw) {
        String v = fold(raw).replaceAll("FCFA|CFA|XOF|F", "").replaceAll("[\\s\\u00A0\\u202F]", "")
                .replace(",", ".");
        if (v.isEmpty() || "GRATUIT".equals(v)) {
            return v.isEmpty() ? null : BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Type, also read from a free-form category ("Panel officiel", "Média (RTB)", "Conférence / démo"). */
    private static ActivityType parseType(@Nullable String raw, List<String> errors) {
        if (raw == null) {
            return ActivityType.AUTRE;
        }
        String key = fold(raw).replaceAll("[^A-Z]", "");
        Map<String, ActivityType> aliases = Map.ofEntries(
                Map.entry("TABLERONDE", ActivityType.TABLE_RONDE), Map.entry("EMISSION", ActivityType.EMISSION_MEDIA),
                Map.entry("MEDIA", ActivityType.EMISSION_MEDIA), Map.entry("DEMO", ActivityType.DEMONSTRATION),
                Map.entry("CONFERENCEINAUGURALE", ActivityType.KEYNOTE),
                Map.entry("OUVERTURE", ActivityType.CEREMONIE), Map.entry("CLOTURE", ActivityType.CEREMONIE),
                Map.entry("ACCUEIL", ActivityType.AUTRE), Map.entry("ACTIVITEPERMANENTE", ActivityType.AUTRE),
                Map.entry("EXPO", ActivityType.EXPOSITION), Map.entry("SALON", ActivityType.EXPOSITION),
                Map.entry("CONCERT", ActivityType.SPECTACLE), Map.entry("DINER", ActivityType.GALA));
        Map<String, ActivityType> candidates = new HashMap<>(aliases);
        for (ActivityType t : ActivityType.values()) {
            candidates.put(t.name().replace("_", ""), t);
        }
        if (candidates.containsKey(key)) {
            return candidates.get(key);
        }
        // Longest known prefix: "PANELOFFICIEL" → PANEL, "CONFERENCEDEMONSTRATION" → CONFERENCE.
        String best = null;
        for (String c : candidates.keySet()) {
            if (key.startsWith(c) && (best == null || c.length() > best.length())) {
                best = c;
            }
        }
        if (best != null) {
            return candidates.get(best);
        }
        errors.add("Type inconnu : « " + raw + " »");
        return ActivityType.AUTRE;
    }

    // Start / end, given either as one date-time column or as a date column plus an hour column.

    @Nullable
    private static String debutRaw(Map<String, String> row) {
        String d = get(row, "debut");
        String h = get(row, "heuredebut");
        if (d == null) {
            return null;
        }
        return h != null ? datePart(d) + " " + hour(h) : dateOnly(d) ? datePart(d) + " 00:00" : d;
    }

    @Nullable
    private static String finRaw(Map<String, String> row) {
        String d = get(row, "fin");
        String h = get(row, "heurefin");
        String debut = get(row, "debut");
        boolean allDay = get(row, "heuredebut") == null && debut != null && dateOnly(debut);
        if (d == null && (h != null || allDay)) {
            d = debut; // same day
        }
        if (d == null) {
            return null;
        }
        if (h != null) {
            return datePart(d) + " " + hour(h);
        }
        return dateOnly(d) ? datePart(d) + " 23:59" : d;
    }

    private static String datePart(String v) {
        return v.trim().split("[\\sT]+")[0];
    }

    /** No time, or Excel's midnight of a date-only cell. */
    private static boolean dateOnly(String v) {
        String[] parts = v.trim().split("[\\sT]+");
        return parts.length == 1 || parts[1].matches("0?0[:h]00(:00)?");
    }

    /** "9h", "9h30", "09:30:00" → "9:30". */
    private static String hour(String h) {
        String v = h.trim().toLowerCase(Locale.ROOT).replace('h', ':').replace('.', ':');
        if (v.endsWith(":")) {
            v = v + "00";
        }
        String[] p = v.split(":");
        return p.length >= 2 ? p[0] + ":" + p[1] : v + ":00";
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
            case "TYPE", "TYPEDACTIVITE", "CATEGORIE" -> "type";
            case "DEBUT", "DATEDEBUT", "DATEDEDEBUT", "DATE", "JOUR", "DATEETHEUREDEDEBUT" -> "debut";
            case "FIN", "DATEFIN", "DATEDEFIN", "DATEETHEUREDEFIN" -> "fin";
            case "HEUREDEBUT", "HEUREDEDEBUT" -> "heuredebut";
            case "HEUREFIN", "HEUREDEFIN" -> "heurefin";
            case "PORTEUR", "PORTEURS", "STRUCTUREPORTEUSE" -> "porteur";
            case "PRIX", "PRIXFCFA", "PRIXXOF", "TARIF", "MONTANT" -> "prix";
            case "CHAMPSAREMPLIR", "CHAMPS", "CHAMPSDUFORMULAIRE", "FORMULAIRE", "INFORMATIONSDEMANDEES" -> "champs";
            case "SALLE", "LIEU" -> "salle";
            case "INTERVENANTS", "INTERVENANT", "PANELISTES", "ORATEURS" -> "intervenants";
            case "MODERATEUR", "MODERATRICE" -> "moderateur";
            case "DESCRIPTION", "RESUME" -> "description";
            case "THEME", "PISTE", "THEMEPISTE", "SOUSTHEME" -> "theme";
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

    private static final String[] ACCESS_CHOICES = {"Sans billet", "Gratuit", "Payant"};
    private static final String[] FIELD_CHOICES = {"Nom + Prénom + Téléphone", "Nom + Téléphone",
            "Prénom + Téléphone", "Téléphone"};

    /** Drop-down list on a column (rows 2–1000); free typing stays possible. */
    private static void dropdown(Sheet sheet, int col, String[] choices) {
        var helper = sheet.getDataValidationHelper();
        var v = helper.createValidation(helper.createExplicitListConstraint(choices),
                new org.apache.poi.ss.util.CellRangeAddressList(1, 999, col, col));
        v.setShowErrorBox(false);
        sheet.addValidationData(v);
    }

    /** Excel template with the expected columns, example lines and a help sheet. */
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
                            "", "", "Ouverture officielle de la 21e Semaine du Numérique", "", "Sans billet", "", "",
                            "", "Sur place", ""},
                    {"Conf'Day 3 : IA et services publics", "Panel", "26/11/2026 09:30", "26/11/2026 12:00", "Kilimandjaro",
                            "A. Ouédraogo ; B. Traoré", "C. Sawadogo", "Résumé du panel…", "IA", "Gratuit", "",
                            "Nom + Téléphone", "250", "Hybride", "https://youtube.com/…"},
                    {"Gala des lauréats", "Gala", "27/11/2026 19:00", "27/11/2026 23:00", "Salle des banquets",
                            "", "", "Soirée de remise des prix", "", "Payant", "10000", "", "300", "Sur place", ""},
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
            dropdown(sheet, COLUMNS.indexOf("Accès"), ACCESS_CHOICES);
            dropdown(sheet, COLUMNS.indexOf("Champs à remplir"), FIELD_CHOICES);
            Sheet help = wb.createSheet("Aide");
            String[] lines = {
                    "Une ligne par activité. Colonnes obligatoires : Titre, Début.",
                    "Dates : JJ/MM/AAAA HH:MM (ex. 26/11/2026 09:30), heure de Ouagadougou.",
                    "Type : Cérémonie, Keynote, Conférence, Panel, Table ronde, Forum, Atelier, Formation, "
                            + "Démonstration, Exposition, Émission média, Networking, Spectacle, Gala, Pause, Autre.",
                    "Accès : Sans billet (par défaut), Gratuit (billet gratuit créé automatiquement), Payant "
                            + "(billet payant créé automatiquement au prix indiqué).",
                    "Prix (FCFA) : obligatoire si l'accès est Payant (ex. 5000) ; laisser vide sinon. "
                            + "Accès vide + prix rempli = Payant.",
                    "Champs à remplir : informations demandées au participant pour un billet gratuit, parmi "
                            + "Nom, Prénom, Téléphone (ex. « Nom + Prénom + Téléphone » ou « Téléphone »). "
                            + "Le téléphone est toujours demandé ; vide = Nom, Prénom, Téléphone. "
                            + "Un billet payant demande toujours Nom, Prénom, Téléphone.",
                    "Capacité : nombre de places (quota du billet gratuit ou payant ; vide = illimité).",
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
