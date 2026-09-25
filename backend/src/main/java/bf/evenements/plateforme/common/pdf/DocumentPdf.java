package bf.evenements.plateforme.common.pdf;

import bf.evenements.plateforme.common.storage.FileStorageService;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Paginated A4 document builder for invoices, receipts and confirmations. The look comes from the EVENT
 * ({@link EventTheme}): its cover (or a generated one) is the hero of the first page with the event name on
 * it, and the cover's dominant color tints the chips, section ticks and the highlighted amount card.
 * Long values wrap instead of being cut off, and content that overflows a page flows onto the next one
 * (nothing is silently dropped).
 */
public final class DocumentPdf {

    private static final float PAGE_W = PDRectangle.A4.getWidth();
    private static final float PAGE_H = PDRectangle.A4.getHeight();
    private static final float MARGIN = 50f;
    private static final float BAR_H = 5f;
    private static final float HERO_H = 190f;
    private static final float LABEL_W = 130f;
    private static final float CONTENT_W = PAGE_W - 2 * MARGIN;
    private static final float LINE_H = 15f;

    private final PDDocument doc = new PDDocument();
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
    private final List<Block> blocks = new ArrayList<>();

    private FileStorageService fileStorage;
    private String coverUrl;
    private String themeSeed;
    private String heroTitle = "Plateforme Nationale des Événements";
    private String heroSubtitle = "";
    private String kicker = "";
    private String title = "";
    private String subtitle = "";
    private int pageNumber = 0;
    private EventTheme theme;

    private DocumentPdf() {
    }

    public static DocumentPdf create() {
        return new DocumentPdf();
    }

    /**
     * The event this document is about: its cover (or a generated one) becomes the hero of the page, its
     * name is written on it, and its dominant color tints the whole document.
     */
    public DocumentPdf event(FileStorageService fileStorage, String coverUrl, String seed, String name,
                             String heroSubtitle) {
        this.fileStorage = fileStorage;
        this.coverUrl = coverUrl;
        this.themeSeed = seed;
        if (name != null && !name.isBlank()) {
            this.heroTitle = name;
        }
        this.heroSubtitle = heroSubtitle == null ? "" : heroSubtitle;
        return this;
    }

    public DocumentPdf kicker(String k) {
        this.kicker = k == null ? "" : k;
        return this;
    }

    public DocumentPdf title(String t) {
        this.title = t == null ? "" : t;
        return this;
    }

    public DocumentPdf subtitle(String s) {
        this.subtitle = s == null ? "" : s;
        return this;
    }

    public DocumentPdf section(String heading) {
        blocks.add(new SectionBlock(heading));
        return this;
    }

    public DocumentPdf row(String label, String value) {
        if (value != null && !value.isBlank()) {
            wrapChecked(() -> blocks.add(new RowBlock(label, value)));
        }
        return this;
    }

    public DocumentPdf paragraph(String text) {
        if (text != null && !text.isBlank()) {
            wrapChecked(() -> blocks.add(new ParagraphBlock(text, regular, PdfBrand.SLATE_900)));
        }
        return this;
    }

    /** A muted, oblique aside — a closing remark ("Facture acquittée.") rather than data. */
    public DocumentPdf note(String text) {
        if (text != null && !text.isBlank()) {
            wrapChecked(() -> blocks.add(new ParagraphBlock(text, italic, PdfBrand.SLATE_500)));
        }
        return this;
    }

    private interface CheckedAction {
        void run() throws IOException;
    }

    private static void wrapChecked(CheckedAction action) {
        try {
            action.run();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The one number the reader is looking for, highlighted in a tinted card. */
    public DocumentPdf amount(String label, String value) {
        blocks.add(new AmountBlock(label, value));
        return this;
    }

    public DocumentPdf spacer() {
        blocks.add(new SpacerBlock());
        return this;
    }

    public byte[] build() {
        try (doc) {
            theme = EventTheme.of(doc, fileStorage, coverUrl, themeSeed);

            Page page = newPage();
            drawHeader(page);

            for (Block b : blocks) {
                float needed = b.height();
                if (page.y - needed < MARGIN + BAR_H + 16) {
                    finishPage(page);
                    page = newPage();
                    drawContinuationHeader(page);
                }
                b.draw(page);
            }
            finishPage(page);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Génération du PDF impossible", e);
        }
    }

    // ---- page plumbing ----

    private final class Page {
        final PDPageContentStream cs;
        final int number;
        float y;

        Page(PDPageContentStream cs, int number) {
            this.cs = cs;
            this.number = number;
            this.y = PAGE_H - MARGIN;
        }
    }

    private Page newPage() throws IOException {
        pageNumber++;
        PDPage pdPage = new PDPage(PDRectangle.A4);
        doc.addPage(pdPage);
        return new Page(new PDPageContentStream(doc, pdPage), pageNumber);
    }

    private void finishPage(Page page) throws IOException {
        page.cs.setNonStrokingColor(theme.accent);
        page.cs.addRect(0, 0, PAGE_W, BAR_H);
        page.cs.fill();
        page.cs.setNonStrokingColor(PdfBrand.SLATE_500);
        PdfText.draw(page.cs, regular, 8.5f, MARGIN, BAR_H + 14,
                "Plateforme Nationale de Gestion des Evenements");
        String p = "Page " + page.number;
        float pw = PdfText.width(regular, 8.5f, p);
        PdfText.draw(page.cs, regular, 8.5f, PAGE_W - MARGIN - pw, BAR_H + 14, p);
        page.cs.setNonStrokingColor(Color.BLACK);
        page.cs.close();
    }

    private void drawHeader(Page page) throws IOException {
        page.y = theme.drawHero(page.cs, PAGE_W, PAGE_H, HERO_H, MARGIN, kicker, heroTitle, heroSubtitle) - 30;
        if (!title.isEmpty()) {
            page.cs.setNonStrokingColor(PdfBrand.SLATE_900);
            PdfText.draw(page.cs, bold, 19, MARGIN, page.y, title);
            page.cs.setNonStrokingColor(Color.BLACK);
            page.y -= 21;
        }
        if (!subtitle.isEmpty()) {
            page.cs.setNonStrokingColor(PdfBrand.SLATE_500);
            PdfText.draw(page.cs, regular, 10.5f, MARGIN, page.y, subtitle);
            page.cs.setNonStrokingColor(Color.BLACK);
            page.y -= 22;
        }
        page.cs.setStrokingColor(PdfBrand.SLATE_200);
        page.cs.setLineWidth(1);
        page.cs.moveTo(MARGIN, page.y);
        page.cs.lineTo(PAGE_W - MARGIN, page.y);
        page.cs.stroke();
        page.y -= 22;
    }

    private void drawContinuationHeader(Page page) throws IOException {
        page.cs.setNonStrokingColor(theme.accent);
        page.cs.addRect(0, PAGE_H - BAR_H, PAGE_W, BAR_H);
        page.cs.fill();
        page.y = PAGE_H - BAR_H - 26;
        page.cs.setNonStrokingColor(PdfBrand.SLATE_500);
        String label = (title.isEmpty() ? kicker : title) + " (suite)";
        PdfText.draw(page.cs, bold, 11, MARGIN, page.y, label);
        page.cs.setNonStrokingColor(Color.BLACK);
        page.y -= 24;
    }

    // ---- blocks ----

    private interface Block {
        float height() throws IOException;

        void draw(Page page) throws IOException;
    }

    private final class SectionBlock implements Block {
        final String heading;

        SectionBlock(String heading) {
            this.heading = heading;
        }

        public float height() {
            return 34f;
        }

        public void draw(Page page) throws IOException {
            page.y -= 10;
            page.cs.setNonStrokingColor(theme.accent);
            page.cs.addRect(MARGIN, page.y - 9, 3, 12);
            page.cs.fill();
            page.cs.setNonStrokingColor(PdfBrand.SLATE_900);
            PdfText.draw(page.cs, bold, 11.5f, MARGIN + 10, page.y - 4, heading.toUpperCase(Locale.FRENCH));
            page.cs.setNonStrokingColor(Color.BLACK);
            page.y -= 24;
        }
    }

    private final class RowBlock implements Block {
        final String label;
        final List<String> lines;

        RowBlock(String label, String value) throws IOException {
            this.label = label;
            this.lines = PdfText.wrap(bold, 10.5f, CONTENT_W - LABEL_W, value);
        }

        public float height() {
            return lines.size() * LINE_H + 4;
        }

        public void draw(Page page) throws IOException {
            page.cs.setNonStrokingColor(PdfBrand.SLATE_500);
            PdfText.draw(page.cs, regular, 10, MARGIN, page.y, label);
            page.cs.setNonStrokingColor(PdfBrand.SLATE_900);
            for (String line : lines) {
                PdfText.draw(page.cs, bold, 10.5f, MARGIN + LABEL_W, page.y, line);
                page.y -= LINE_H;
            }
            page.cs.setNonStrokingColor(Color.BLACK);
            page.y -= 4;
        }
    }

    private final class ParagraphBlock implements Block {
        final List<String> lines;
        final PDType1Font font;
        final Color color;

        ParagraphBlock(String text, PDType1Font font, Color color) throws IOException {
            this.lines = PdfText.wrap(font, 10.5f, CONTENT_W, text);
            this.font = font;
            this.color = color;
        }

        public float height() {
            return lines.size() * LINE_H + 6;
        }

        public void draw(Page page) throws IOException {
            page.cs.setNonStrokingColor(color);
            for (String line : lines) {
                PdfText.draw(page.cs, font, 10.5f, MARGIN, page.y, line);
                page.y -= LINE_H;
            }
            page.cs.setNonStrokingColor(Color.BLACK);
            page.y -= 6;
        }
    }

    private final class AmountBlock implements Block {
        final String label;
        final String value;

        AmountBlock(String label, String value) {
            this.label = label;
            this.value = value;
        }

        public float height() {
            return 74f;
        }

        public void draw(Page page) throws IOException {
            page.y -= 8;
            float boxH = 60;
            float boxY = page.y - boxH;
            page.cs.setNonStrokingColor(theme.accentTint);
            page.cs.addRect(MARGIN, boxY, CONTENT_W, boxH);
            page.cs.fill();
            page.cs.setNonStrokingColor(theme.accent);
            page.cs.addRect(MARGIN, boxY, 4, boxH);
            page.cs.fill();

            page.cs.setNonStrokingColor(PdfBrand.SLATE_500);
            PdfText.draw(page.cs, bold, 9.5f, MARGIN + 18, boxY + boxH - 21,
                    label.toUpperCase(Locale.FRENCH));
            page.cs.setNonStrokingColor(theme.accentDark);
            PdfText.draw(page.cs, bold, 20, MARGIN + 18, boxY + 14, value);
            page.cs.setNonStrokingColor(Color.BLACK);
            page.y = boxY - 14;
        }
    }

    private final class SpacerBlock implements Block {
        public float height() {
            return 10f;
        }

        public void draw(Page page) {
            page.y -= 10f;
        }
    }
}
