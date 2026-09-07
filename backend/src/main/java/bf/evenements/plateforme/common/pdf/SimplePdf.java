package bf.evenements.plateforme.common.pdf;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Tiny builder for one-page A4 text documents (invoices, receipts, confirmations).
 */
public final class SimplePdf {

    private final List<Line> lines = new ArrayList<>();
    private String title = "";

    private record Line(String text, float size, boolean bold, float spacingBefore) {
    }

    public static SimplePdf create(String title) {
        SimplePdf pdf = new SimplePdf();
        pdf.title = title;
        return pdf;
    }

    public SimplePdf heading(String text) {
        lines.add(new Line(text, 13, true, 14));
        return this;
    }

    public SimplePdf text(String text) {
        lines.add(new Line(text, 10.5f, false, 4));
        return this;
    }

    public SimplePdf spacer() {
        lines.add(new Line("", 6, false, 8));
        return this;
    }

    public byte[] build() {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PDRectangle.A4.getHeight() - 60;
                write(cs, bold, 18, 50, y, sanitize(title));
                y -= 30;

                for (Line l : lines) {
                    y -= l.spacingBefore();
                    if (y < 60) {
                        break;
                    }
                    write(cs, l.bold() ? bold : regular, l.size(), 50, y, sanitize(l.text()));
                    y -= l.size() + 3;
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Génération du PDF impossible", e);
        }
    }

    private static void write(PDPageContentStream cs, PDType1Font font, float size, float x, float y,
                              String s) throws java.io.IOException {
        if (s.isEmpty()) {
            return;
        }
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    /** Restrict to characters the Helvetica standard font can encode (WinAnsi). */
    public static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-').replace('…', ' ').replace(' ', ' ');
        StringBuilder sb = new StringBuilder(t.length());
        for (char c : t.toCharArray()) {
            if ((c >= 32 && c <= 126) || (c >= 160 && c <= 255)) {
                sb.append(c);
            } else if (c == '\t' || c == '\n') {
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
