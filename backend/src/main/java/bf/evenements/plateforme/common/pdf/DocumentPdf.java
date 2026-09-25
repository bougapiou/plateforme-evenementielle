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
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Paginated A4 document builder for invoices, receipts and confirmations, in the same design as the ticket:
 * the event's cover photo is the FULL-PAGE background (darkened), with the PNE emblem, the event name in big
 * two-tone type, a dark glass panel holding the details and, when needed, a gold amount card. Long values
 * wrap instead of being cut off, and content that overflows a page flows onto the next one.
 */
public final class DocumentPdf {

    private static final float W = PDRectangle.A4.getWidth();
    private static final float H = PDRectangle.A4.getHeight();
    /** Design-mockup pixels (794 x 1123) to points. */
    private static final float S = W / 794f;
    private static final float MARGIN = 52f * S;
    private static final float CONTENT_W = W - 2 * MARGIN;
    private static final float PAD_H = 30f * S;
    private static final float PAD_V = 26f * S;
    private static final float BOTTOM = 58f;
    private static final float LABEL_W = 150f * S;
    private static final float TEXT = 18f * S;
    private static final float LINE_H = 24f * S;

    private final PDDocument doc = new PDDocument();
    private final PdfFonts f;
    private final List<Block> blocks = new ArrayList<>();

    private FileStorageService fileStorage;
    private String coverUrl;
    private String seed;
    private String heroTitle = "Plateforme Nationale des Événements";
    private String heroSubtitle = "";
    private String kicker = "";
    private String title = "";
    private String subtitle = "";

    private DocumentPdf() {
        try {
            this.f = PdfFonts.load(doc);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static DocumentPdf create() {
        return new DocumentPdf();
    }

    private static float px(float v) {
        return v * S;
    }

    // ---------- builder ----------

    /** The event this document is about: its cover is the page background, its name the big title. */
    public DocumentPdf event(FileStorageService fileStorage, String coverUrl, String seed, String name,
                             String heroSubtitle) {
        this.fileStorage = fileStorage;
        this.coverUrl = coverUrl;
        this.seed = seed;
        if (name != null && !name.isBlank()) {
            this.heroTitle = name;
        }
        this.heroSubtitle = heroSubtitle == null ? "" : heroSubtitle;
        return this;
    }

    /** Document type, in the gold chip at the top right (FACTURE, REÇU DE PAIEMENT…). */
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
            blocks.add(new RowBlock(label, value));
        }
        return this;
    }

    public DocumentPdf paragraph(String text) {
        if (text != null && !text.isBlank()) {
            blocks.add(new ParagraphBlock(text));
        }
        return this;
    }

    /** A muted, italic aside under the panel — a closing remark ("Facture acquittée.") rather than data. */
    public DocumentPdf note(String text) {
        if (text != null && !text.isBlank()) {
            blocks.add(new NoteBlock(text));
        }
        return this;
    }

    /** The one number the reader is looking for, in a gold card. */
    public DocumentPdf amount(String label, String value) {
        blocks.add(new AmountBlock(label, value));
        return this;
    }

    public DocumentPdf spacer() {
        blocks.add(new SpacerBlock());
        return this;
    }

    // ---------- build ----------

    public byte[] build() {
        try (doc) {
            PDImageXObject background = PdfBackdrop.create(doc, fileStorage, coverUrl, seed, 1240,
                    Math.round(1240 * H / W), PdfBackdrop.Veil.PORTRAIT);
            Header header = layoutHeader();

            List<List<Block>> pages = paginate(H - header.bodyTop - BOTTOM, H - px(120) - BOTTOM);
            int number = 0;
            for (List<Block> pageBlocks : pages) {
                number++;
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(background, 0, 0, W, H);
                    PdfArt.frame(cs, px(16), px(16), W - 2 * px(16), H - 2 * px(16), px(22), 0.8f, 0.5f);
                    PdfArt.swoosh(cs, 0, 0, px(280), px(112));
                    PdfArt.patternCorner(cs, W, px(90));

                    float bodyTop;
                    if (number == 1) {
                        drawHeader(cs, header);
                        bodyTop = header.bodyTop;
                    } else {
                        bodyTop = drawContinuationHeader(cs);
                    }
                    drawBlocks(cs, pageBlocks, bodyTop);
                    drawFooter(cs, number);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Génération du PDF impossible", e);
        }
    }

    // ---------- header ----------

    private record Header(List<String> titleLines, float titleSize, float titleBaseline, float subBaseline,
                          float docBaseline, float bodyTop) {
    }

    /** Where everything of page 1's header goes (distances from the top of the page, in points). */
    private Header layoutHeader() throws IOException {
        String name = heroTitle.toUpperCase(Locale.FRENCH);
        float maxW = CONTENT_W;
        float size = f.fitSize(f.display, name, maxW, px(92), 30f);
        List<String> lines = size > 30f || f.width(f.display, size, name) <= maxW
                ? List.of(name)
                : firstTwo(f.wrap(f.display, size, maxW, name));
        float baseline = px(150) + size * 0.80f;
        float lastBaseline = baseline + (lines.size() - 1) * size * 0.95f;
        float subBaseline = lastBaseline + Math.max(20f, size * 0.30f);
        float docBaseline = (heroSubtitle.isBlank() ? lastBaseline + 12 : subBaseline) + 34f;
        float bodyTop = docBaseline + 34f;
        return new Header(lines, size, baseline, subBaseline, docBaseline, bodyTop);
    }

    private static List<String> firstTwo(List<String> lines) {
        return lines.size() <= 2 ? lines : new ArrayList<>(lines.subList(0, 2));
    }

    private void drawHeader(PDPageContentStream cs, Header h) throws IOException {
        drawBrand(cs);

        if (!kicker.isBlank()) {
            String label = kicker.toUpperCase(Locale.FRENCH);
            float size = px(17);
            float spacing = px(2);
            float w = f.widthSpaced(f.bold, size, label, spacing) + 2 * px(22);
            PdfArt.chip(cs, f, W - MARGIN - w, H - px(46) - px(64) / 2f - (size + 2 * px(9)) / 2f, label, size,
                    px(22), px(9), spacing, PdfArt.GOLD, PdfArt.NAVY);
        }

        int word = 0;
        for (int i = 0; i < h.titleLines.size(); i++) {
            float cx = MARGIN;
            for (String w : h.titleLines.get(i).split(" ")) {
                if (w.isEmpty()) {
                    continue;
                }
                cs.setNonStrokingColor(word++ == 0 ? Color.WHITE : PdfArt.GOLD);
                f.draw(cs, f.display, h.titleSize, cx, H - (h.titleBaseline + i * h.titleSize * 0.95f), w);
                cx += f.width(f.display, h.titleSize, w + " ");
            }
        }
        if (!heroSubtitle.isBlank()) {
            cs.setNonStrokingColor(new Color(0xE3, 0xE6, 0xEC));
            f.drawSpaced(cs, f.regular, px(18), MARGIN, H - h.subBaseline,
                    f.ellipsize(f.regular, px(18), CONTENT_W, heroSubtitle.toUpperCase(Locale.FRENCH)), px(2.5f));
        }
        if (!title.isBlank()) {
            cs.setNonStrokingColor(Color.WHITE);
            float tsize = px(30);
            f.draw(cs, f.bold, tsize, MARGIN, H - h.docBaseline, title);
            if (!subtitle.isBlank()) {
                cs.setNonStrokingColor(PdfArt.SOFT_WHITE);
                f.draw(cs, f.regular, px(17), MARGIN + f.width(f.bold, tsize, title) + px(16), H - h.docBaseline,
                        subtitle);
            }
        }
        cs.setNonStrokingColor(Color.BLACK);
    }

    private void drawBrand(PDPageContentStream cs) throws IOException {
        float r = px(64) * 0.4286f;
        PdfArt.emblem(cs, MARGIN + px(32), H - px(46) - px(32), r);
        float tx = MARGIN + px(64) + px(12);
        cs.setNonStrokingColor(Color.WHITE);
        f.drawSpaced(cs, f.bold, px(36), tx, H - px(46) - px(30), "PNE", px(1));
        f.draw(cs, f.regular, px(11.5f), tx, H - px(46) - px(45), "Plateforme Nationale");
        f.draw(cs, f.regular, px(11.5f), tx, H - px(46) - px(59), "de Gestion des Événements");
    }

    /** Small header of the following pages; returns the distance from the top where the body starts. */
    private float drawContinuationHeader(PDPageContentStream cs) throws IOException {
        PdfArt.emblem(cs, MARGIN + px(20), H - px(46) - px(20), px(20));
        cs.setNonStrokingColor(Color.WHITE);
        String label = (title.isBlank() ? heroTitle : title) + " (suite)";
        f.draw(cs, f.bold, px(20), MARGIN + px(52), H - px(46) - px(26), f.ellipsize(f.bold, px(20), CONTENT_W - px(52), label));
        cs.setNonStrokingColor(Color.BLACK);
        return px(120);
    }

    private void drawFooter(PDPageContentStream cs, int number) throws IOException {
        cs.setNonStrokingColor(PdfArt.SOFT_WHITE);
        String text = "Plateforme Nationale de Gestion des Événements · Page " + number;
        float size = px(13);
        f.draw(cs, f.regular, size, W - px(120) - f.width(f.regular, size, text), px(34), text);
        cs.setNonStrokingColor(Color.BLACK);
    }

    // ---------- pagination ----------

    private List<List<Block>> paginate(float firstPageRoom, float otherPagesRoom) throws IOException {
        List<List<Block>> pages = new ArrayList<>();
        List<Block> current = new ArrayList<>();
        float room = firstPageRoom;
        float used = 0;
        boolean panelOpen = false;

        for (Block b : blocks) {
            float need = b.height() + (b.inPanel() && !panelOpen ? 2 * PAD_V : 0);
            if (used + need > room && !current.isEmpty()) {
                pages.add(current);
                current = new ArrayList<>();
                room = otherPagesRoom;
                used = 0;
                panelOpen = false;
                need = b.height() + (b.inPanel() ? 2 * PAD_V : 0);
            }
            current.add(b);
            used += need;
            panelOpen = b.inPanel();
        }
        pages.add(current);
        return pages;
    }

    private void drawBlocks(PDPageContentStream cs, List<Block> pageBlocks, float top) throws IOException {
        float y = top;
        int i = 0;
        while (i < pageBlocks.size()) {
            if (pageBlocks.get(i).inPanel()) {
                int j = i;
                float inner = 0;
                while (j < pageBlocks.size() && pageBlocks.get(j).inPanel()) {
                    inner += pageBlocks.get(j).height();
                    j++;
                }
                float panelH = inner + 2 * PAD_V;
                PdfArt.glassPanel(cs, MARGIN, H - y - panelH, CONTENT_W, panelH, px(24), 0.62f);
                float by = y + PAD_V;
                for (int k = i; k < j; k++) {
                    pageBlocks.get(k).draw(cs, MARGIN + PAD_H, by);
                    by += pageBlocks.get(k).height();
                }
                y += panelH + px(20);
                i = j;
            } else {
                pageBlocks.get(i).draw(cs, MARGIN, y);
                y += pageBlocks.get(i).height();
                i++;
            }
        }
    }

    // ---------- blocks ----------

    private interface Block {
        boolean inPanel();

        /** Height in points. */
        float height() throws IOException;

        /** Draws with its top-left at (x, distance-from-page-top). */
        void draw(PDPageContentStream cs, float x, float fromTop) throws IOException;
    }

    private final class SectionBlock implements Block {
        final String heading;

        SectionBlock(String heading) {
            this.heading = heading.toUpperCase(Locale.FRENCH);
        }

        public boolean inPanel() {
            return true;
        }

        public float height() {
            return 34f;
        }

        public void draw(PDPageContentStream cs, float x, float fromTop) throws IOException {
            cs.setNonStrokingColor(PdfArt.GOLD);
            cs.addRect(x, H - fromTop - 21, 3, 14);
            cs.fill();
            f.drawSpaced(cs, f.bold, px(15), x + 11, H - fromTop - 17.5f, heading, px(1.6f));
            cs.setNonStrokingColor(Color.BLACK);
        }
    }

    private final class RowBlock implements Block {
        final String label;
        final List<String> lines;

        RowBlock(String label, String value) {
            this.label = label;
            try {
                this.lines = f.wrap(f.bold, TEXT, CONTENT_W - 2 * PAD_H - LABEL_W, value);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        public boolean inPanel() {
            return true;
        }

        public float height() {
            return lines.size() * LINE_H + 6;
        }

        public void draw(PDPageContentStream cs, float x, float fromTop) throws IOException {
            cs.setNonStrokingColor(PdfArt.SOFT_WHITE);
            f.draw(cs, f.regular, TEXT, x, H - fromTop - TEXT, label);
            cs.setNonStrokingColor(Color.WHITE);
            for (int i = 0; i < lines.size(); i++) {
                f.draw(cs, f.bold, TEXT, x + LABEL_W, H - fromTop - TEXT - i * LINE_H, lines.get(i));
            }
            cs.setNonStrokingColor(Color.BLACK);
        }
    }

    private final class ParagraphBlock implements Block {
        final List<String> lines;

        ParagraphBlock(String text) {
            try {
                this.lines = f.wrap(f.regular, TEXT, CONTENT_W - 2 * PAD_H, text);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        public boolean inPanel() {
            return true;
        }

        public float height() {
            return lines.size() * LINE_H + 6;
        }

        public void draw(PDPageContentStream cs, float x, float fromTop) throws IOException {
            cs.setNonStrokingColor(new Color(0xEA, 0xEC, 0xF1));
            for (int i = 0; i < lines.size(); i++) {
                f.draw(cs, f.regular, TEXT, x, H - fromTop - TEXT - i * LINE_H, lines.get(i));
            }
            cs.setNonStrokingColor(Color.BLACK);
        }
    }

    private final class NoteBlock implements Block {
        final List<String> lines;

        NoteBlock(String text) {
            try {
                this.lines = f.wrap(f.italic, px(16), CONTENT_W, text);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        public boolean inPanel() {
            return false;
        }

        public float height() {
            return lines.size() * px(25) + px(6);
        }

        public void draw(PDPageContentStream cs, float x, float fromTop) throws IOException {
            cs.setNonStrokingColor(PdfArt.SOFT_WHITE);
            for (int i = 0; i < lines.size(); i++) {
                f.draw(cs, f.italic, px(16), x, H - fromTop - px(16) - i * px(25), lines.get(i));
            }
            cs.setNonStrokingColor(Color.BLACK);
        }
    }

    private final class AmountBlock implements Block {
        final String label;
        final String value;

        AmountBlock(String label, String value) {
            this.label = label.toUpperCase(Locale.FRENCH);
            this.value = value;
        }

        public boolean inPanel() {
            return false;
        }

        public float height() {
            return px(22) * 2 + px(15) + px(52) * 1.1f + px(28);
        }

        public void draw(PDPageContentStream cs, float x, float fromTop) throws IOException {
            float h = height() - px(28);
            float y = H - fromTop - h;
            cs.setNonStrokingColor(PdfArt.GOLD);
            PdfArt.roundedRect(cs, x, y, CONTENT_W, h, px(22));
            cs.fill();
            cs.setNonStrokingColor(PdfArt.NAVY);
            f.drawSpaced(cs, f.bold, px(15), x + px(30), y + h - px(22) - px(11), label, px(2));
            float size = f.fitSize(f.bold, value, CONTENT_W - 2 * px(30), px(52), px(24));
            f.draw(cs, f.bold, size, x + px(30), y + px(22), value);
            cs.setNonStrokingColor(Color.BLACK);
        }
    }

    private final class SpacerBlock implements Block {
        public boolean inPanel() {
            return false;
        }

        public float height() {
            return 10f;
        }

        public void draw(PDPageContentStream cs, float x, float fromTop) {
            // nothing to draw
        }
    }
}
