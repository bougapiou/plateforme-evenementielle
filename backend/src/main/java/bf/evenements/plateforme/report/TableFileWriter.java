package bf.evenements.plateforme.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Generic tabular exports: CSV that Excel opens with the right accents (UTF-8
 * BOM, « ; » separator as in French locales) and a plain .xlsx sheet. Cells
 * are strings; those holding a number are written as numbers in Excel.
 */
public final class TableFileWriter {

    private TableFileWriter() {
    }

    public static byte[] csv(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder("﻿");
        line(sb, headers);
        rows.forEach(r -> line(sb, r));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void line(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            String v = cells.get(i) == null ? "" : cells.get(i);
            // Neutralise spreadsheet formulas (CSV injection).
            if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0 && !v.matches("-?\\d+([.,]\\d+)?")) {
                v = "'" + v;
            }
            if (v.contains(";") || v.contains("\"") || v.contains("\n")) {
                v = "\"" + v.replace("\"", "\"\"") + "\"";
            }
            sb.append(v);
        }
        sb.append("\r\n");
    }

    public static byte[] xlsx(String sheetName, String title, List<String> headers, List<List<String>> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet(sheetName);
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

            sheet.createRow(0).createCell(0).setCellValue(title);
            sheet.getRow(0).getCell(0).setCellStyle(titleStyle);
            XSSFRow hr = sheet.createRow(2);
            for (int i = 0; i < headers.size(); i++) {
                XSSFCell c = hr.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(head);
            }
            for (int r = 0; r < rows.size(); r++) {
                XSSFRow row = sheet.createRow(3 + r);
                List<String> values = rows.get(r);
                for (int i = 0; i < values.size(); i++) {
                    String v = values.get(i) == null ? "" : values.get(i);
                    XSSFCell c = row.createCell(i);
                    if (v.matches("-?\\d+(\\.\\d+)?")) {
                        c.setCellValue(Double.parseDouble(v));
                    } else {
                        c.setCellValue(v);
                    }
                }
            }
            if (!headers.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(2, 2 + Math.max(rows.size(), 1), 0, headers.size() - 1));
                sheet.createFreezePane(1, 3);
                for (int i = 0; i < headers.size(); i++) {
                    sheet.autoSizeColumn(i);
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
