package bf.evenements.plateforme.common.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * The platform's PDF typography — Barlow (SIL Open Font License, see {@code /pdf/fonts/OFL.txt}) embedded
 * as full Unicode fonts — plus the text helpers every document needs (glyph-safe drawing, width,
 * word-wrap, fit-to-width). One instance per {@link PDDocument}.
 */
public final class PdfFonts {

    private static final Map<String, byte[]> BYTES = new ConcurrentHashMap<>();

    public final PDType0Font regular;
    public final PDType0Font bold;
    public final PDType0Font italic;
    /** Condensed extra-bold italic: event titles. */
    public final PDType0Font display;

    private final Map<PDFont, TrueTypeFont> ttf = new IdentityHashMap<>();

    private PdfFonts(PDDocument doc) throws IOException {
        regular = load(doc, "Barlow-Regular.ttf");
        bold = load(doc, "Barlow-Bold.ttf");
        italic = load(doc, "Barlow-Italic.ttf");
        display = load(doc, "BarlowCondensed-ExtraBoldItalic.ttf");
    }

    public static PdfFonts load(PDDocument doc) throws IOException {
        return new PdfFonts(doc);
    }

    private PDType0Font load(PDDocument doc, String file) throws IOException {
        byte[] bytes = BYTES.computeIfAbsent(file, f -> {
            try (InputStream in = PdfFonts.class.getResourceAsStream("/pdf/fonts/" + f)) {
                if (in == null) {
                    throw new IllegalStateException("Police introuvable : " + f);
                }
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        TrueTypeFont font = new TTFParser().parse(new RandomAccessReadBuffer(bytes));
        PDType0Font pd = PDType0Font.load(doc, font, true);
        ttf.put(pd, font);
        return pd;
    }

    // ---- text ----

    /** Drops characters the font has no glyph for (drawing them would throw), and control characters. */
    public String safe(PDFont font, String text) {
        if (text == null) {
            return "";
        }
        TrueTypeFont t = ttf.get(font);
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            int c = cp;
            if (c == 0xA0 || c == '\t' || c == '\n' || c == '\r') {
                c = ' ';
            }
            if (c < 32) {
                return;
            }
            try {
                if (t == null || t.getUnicodeCmapLookup().getGlyphId(c) > 0) {
                    sb.appendCodePoint(c);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return sb.toString();
    }

    public float width(PDFont font, float size, String text) throws IOException {
        String s = safe(font, text);
        return s.isEmpty() ? 0 : font.getStringWidth(s) / 1000 * size;
    }

    public void draw(PDPageContentStream cs, PDFont font, float size, float x, float y, String text)
            throws IOException {
        String s = safe(font, text);
        if (s.isEmpty()) {
            return;
        }
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    /** Letter-spaced text (small caps labels, category lines). */
    public void drawSpaced(PDPageContentStream cs, PDFont font, float size, float x, float y, String text,
                           float spacing) throws IOException {
        String s = safe(font, text);
        if (s.isEmpty()) {
            return;
        }
        cs.beginText();
        cs.setFont(font, size);
        cs.setCharacterSpacing(spacing);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.setCharacterSpacing(0);
        cs.endText();
    }

    public float widthSpaced(PDFont font, float size, String text, float spacing) throws IOException {
        String s = safe(font, text);
        return width(font, size, s) + spacing * Math.max(0, s.length() - 1);
    }

    /** Breaks {@code text} into lines no wider than {@code maxWidth}; explicit newlines are kept. */
    public List<String> wrap(PDFont font, float size, float maxWidth, String text) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : (text == null ? "" : text).split("\n", -1)) {
            String p = safe(font, paragraph);
            if (p.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : p.split(" ")) {
                String candidate = current.length() == 0 ? word : current + " " + word;
                if (current.length() == 0 || font.getStringWidth(candidate) / 1000 * size <= maxWidth) {
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

    /** The largest size in [minSize, maxSize] at which {@code text} fits on one line of {@code maxWidth}. */
    public float fitSize(PDFont font, String text, float maxWidth, float maxSize, float minSize)
            throws IOException {
        float w = width(font, maxSize, text);
        if (w <= maxWidth || w == 0) {
            return maxSize;
        }
        return Math.max(minSize, maxSize * maxWidth / w);
    }

    /** Shortens {@code text} with "..." until it fits {@code maxWidth}. */
    public String ellipsize(PDFont font, float size, float maxWidth, String text) throws IOException {
        String s = safe(font, text);
        if (width(font, size, s) <= maxWidth) {
            return s;
        }
        while (s.length() > 1 && width(font, size, s + "...") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "...";
    }
}
