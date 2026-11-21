package bf.evenements.plateforme.report;

import bf.evenements.plateforme.common.pdf.PdfFonts;
import bf.evenements.plateforme.report.ParticipantListService.Row;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.springframework.stereotype.Component;

/** Renders a participant list as .xlsx, .docx or .pdf (A4 landscape for the last two). */
@Component
public class ParticipantFileWriter {

    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH).withZone(ReportService.ZONE);
    private static final String BRAND = "1E3A8A";

    private static List<String> headers(boolean occurrences) {
        List<String> h = new ArrayList<>(List.of("N°", "Nom", "Prénom", "Téléphone", "E-mail",
                "Référence", "Catégorie", "Statut", "Date"));
        if (occurrences) {
            h.add("Occurrences");
        }
        return h;
    }

    private static List<String> cells(int index, Row r, boolean occurrences) {
        List<String> c = new ArrayList<>(List.of(String.valueOf(index), r.nom(), r.prenom(), r.telephone(),
                r.email(), r.reference(), r.categorie(), r.detail(), date(r.date())));
        if (occurrences) {
            c.add(String.valueOf(r.occurrences()));
        }
        return c;
    }

    private static String date(Instant i) {
        return i == null ? "" : DATE.format(i);
    }

    // ------------------------------------------------------------------ Excel

    public byte[] xlsx(String title, String filters, List<Row> rows, boolean occurrences) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("Participants");
            List<String> headers = headers(occurrences);

            XSSFFont titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            CellStyle titleStyle = wb.createCellStyle();
            titleStyle.setFont(titleFont);
            XSSFFont headFont = wb.createFont();
            headFont.setBold(true);
            headFont.setColor(IndexedColors.WHITE.getIndex());
            CellStyle head = wb.createCellStyle();
            head.setFont(headFont);
            head.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle body = wb.createCellStyle();
            body.setBorderBottom(BorderStyle.HAIR);
            body.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());

            sheet.createRow(0).createCell(0).setCellValue(title);
            sheet.getRow(0).getCell(0).setCellStyle(titleStyle);
            sheet.createRow(1).createCell(0).setCellValue(filters);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.size() - 1));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, headers.size() - 1));

            XSSFRow hr = sheet.createRow(3);
            for (int i = 0; i < headers.size(); i++) {
                XSSFCell cell = hr.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(head);
            }
            for (int r = 0; r < rows.size(); r++) {
                XSSFRow row = sheet.createRow(4 + r);
                List<String> values = cells(r + 1, rows.get(r), occurrences);
                for (int i = 0; i < values.size(); i++) {
                    XSSFCell cell = row.createCell(i);
                    boolean numeric = i == 0 || (occurrences && i == values.size() - 1);
                    if (numeric) {
                        cell.setCellValue(Double.parseDouble(values.get(i)));
                    } else {
                        cell.setCellValue(values.get(i));
                    }
                    cell.setCellStyle(body);
                }
            }
            sheet.createFreezePane(0, 4);
            sheet.setAutoFilter(new CellRangeAddress(3, 3 + Math.max(rows.size(), 1), 0, headers.size() - 1));
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 512, 60 * 256));
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------- Word

    public byte[] docx(String title, String filters, List<Row> rows, boolean occurrences) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            landscape(doc);
            XWPFParagraph t = doc.createParagraph();
            t.setAlignment(ParagraphAlignment.LEFT);
            XWPFRun tr = t.createRun();
            tr.setText(title);
            tr.setBold(true);
            tr.setFontSize(15);
            tr.setColor(BRAND);
            XWPFRun fr = doc.createParagraph().createRun();
            fr.setText(filters);
            fr.setFontSize(9);
            fr.setColor("64748B");

            List<String> headers = headers(occurrences);
            XWPFTable table = doc.createTable(rows.size() + 1, headers.size());
            table.setWidth("100%");
            fill(table.getRow(0), headers, true);
            for (int r = 0; r < rows.size(); r++) {
                fill(table.getRow(r + 1), cells(r + 1, rows.get(r), occurrences), false);
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void fill(XWPFTableRow row, List<String> values, boolean header) {
        if (header) {
            row.setRepeatHeader(true);
        }
        for (int i = 0; i < values.size(); i++) {
            XWPFTableCell cell = row.getCell(i);
            if (header) {
                cell.setColor(BRAND);
            }
            XWPFParagraph p = cell.getParagraphs().get(0);
            XWPFRun run = p.createRun();
            run.setText(values.get(i));
            run.setFontSize(8);
            if (header) {
                run.setBold(true);
                run.setColor("FFFFFF");
            }
        }
    }

    private static void landscape(XWPFDocument doc) {
        CTSectPr sect = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageSz size = sect.isSetPgSz() ? sect.getPgSz() : sect.addNewPgSz();
        size.setOrient(STPageOrientation.LANDSCAPE);
        size.setW(BigInteger.valueOf(16838));
        size.setH(BigInteger.valueOf(11906));
        var margins = sect.isSetPgMar() ? sect.getPgMar() : sect.addNewPgMar();
        BigInteger halfInch = BigInteger.valueOf(720);
        margins.setTop(halfInch);
        margins.setBottom(halfInch);
        margins.setLeft(halfInch);
        margins.setRight(halfInch);
    }

    // -------------------------------------------------------------------- PDF

    /** Relative widths of the PDF columns (the last one only with occurrences). */
    private static final float[] PDF_COLS = {0.04f, 0.15f, 0.11f, 0.10f, 0.17f, 0.10f, 0.11f, 0.07f, 0.10f, 0.05f};

    public byte[] pdf(String title, String filters, List<Row> rows, boolean occurrences) {
        PDRectangle size = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
        float margin = 32;
        float width = size.getWidth() - 2 * margin;
        float rowH = 15;
        float fontSize = 7.5f;
        List<String> headers = headers(occurrences);
        float[] cols = new float[headers.size()];
        float total = 0;
        for (int i = 0; i < cols.length; i++) {
            total += PDF_COLS[i];
        }
        for (int i = 0; i < cols.length; i++) {
            cols[i] = PDF_COLS[i] / total * width;
        }

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfFonts f = PdfFonts.load(doc);
            int perPage = (int) ((size.getHeight() - 2 * margin - 60) / rowH);
            int pages = Math.max(1, (rows.size() + perPage - 1) / perPage);
            for (int pg = 0; pg < pages; pg++) {
                PDPage page = new PDPage(size);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    float y = size.getHeight() - margin;
                    if (pg == 0) {
                        cs.setNonStrokingColor(0.12f, 0.23f, 0.54f);
                        f.draw(cs, f.bold, 14, margin, y - 12, f.ellipsize(f.bold, 14, width, title));
                        cs.setNonStrokingColor(0.39f, 0.45f, 0.55f);
                        f.draw(cs, f.regular, 8.5f, margin, y - 26, f.ellipsize(f.regular, 8.5f, width, filters));
                    }
                    y -= 44;
                    // header band
                    cs.setNonStrokingColor(0.12f, 0.23f, 0.54f);
                    cs.addRect(margin, y - rowH + 3, width, rowH);
                    cs.fill();
                    cs.setNonStrokingColor(1f, 1f, 1f);
                    float x = margin;
                    for (int i = 0; i < headers.size(); i++) {
                        f.draw(cs, f.bold, fontSize, x + 3, y - 8, f.ellipsize(f.bold, fontSize, cols[i] - 6,
                                headers.get(i)));
                        x += cols[i];
                    }
                    y -= rowH;
                    int end = Math.min(rows.size(), (pg + 1) * perPage);
                    for (int r = pg * perPage; r < end; r++) {
                        if (r % 2 == 1) {
                            cs.setNonStrokingColor(0.95f, 0.96f, 0.98f);
                            cs.addRect(margin, y - rowH + 3, width, rowH);
                            cs.fill();
                        }
                        cs.setNonStrokingColor(0.12f, 0.16f, 0.23f);
                        List<String> values = cells(r + 1, rows.get(r), occurrences);
                        x = margin;
                        for (int i = 0; i < values.size(); i++) {
                            f.draw(cs, f.regular, fontSize, x + 3, y - 8,
                                    f.ellipsize(f.regular, fontSize, cols[i] - 6, values.get(i)));
                            x += cols[i];
                        }
                        y -= rowH;
                    }
                    if (rows.isEmpty()) {
                        cs.setNonStrokingColor(0.39f, 0.45f, 0.55f);
                        f.draw(cs, f.italic, 9, margin, y - 10, "Aucun participant pour ces critères.");
                    }
                    cs.setNonStrokingColor(0.58f, 0.64f, 0.72f);
                    String footer = "Page " + (pg + 1) + " / " + pages;
                    f.draw(cs, f.regular, 7.5f, size.getWidth() - margin - f.width(f.regular, 7.5f, footer),
                            margin - 14, footer);
                }
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
