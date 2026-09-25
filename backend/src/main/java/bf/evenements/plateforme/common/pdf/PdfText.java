package bf.evenements.plateforme.common.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

/** Small text helpers shared by every PDF generator: WinAnsi sanitizing, drawing and word-wrap. */
public final class PdfText {

    private PdfText() {
    }

    public static void draw(PDPageContentStream cs, PDType1Font font, float size, float x, float y,
                            String text) throws IOException {
        String s = sanitize(text);
        if (s.isEmpty()) {
            return;
        }
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    public static float width(PDType1Font font, float size, String text) throws IOException {
        return font.getStringWidth(sanitize(text)) / 1000 * size;
    }

    /** Breaks {@code text} into lines no wider than {@code maxWidth} at that font size. */
    public static List<String> wrap(PDType1Font font, float size, float maxWidth, String text)
            throws IOException {
        List<String> lines = new ArrayList<>();
        String sanitized = sanitize(text);
        for (String paragraph : sanitized.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (font.getStringWidth(candidate) / 1000 * size <= maxWidth || current.isEmpty()) {
                    current = new StringBuilder(candidate);
                } else {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                }
            }
            lines.add(current.toString());
        }
        return lines;
    }

    /** Keep only characters the standard Helvetica (WinAnsi) font can encode. */
    public static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String t = text
                .replace('’', '\'').replace('‘', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-')
                .replace('…', ' ').replace(' ', ' ');
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
