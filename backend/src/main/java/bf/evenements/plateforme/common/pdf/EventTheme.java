package bf.evenements.plateforme.common.pdf;

import bf.evenements.plateforme.common.storage.FileStorageService;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

/**
 * The look of a document is decided by the EVENT's cover, not by the platform:
 * <ul>
 *   <li>the accent color (chips, section ticks, amount card…) is the dominant color of the cover photo;</li>
 *   <li>the top of the page is a hero made of that photo, darkened at the bottom, with the event's name on it.</li>
 * </ul>
 * An event with no photo (or an unreadable one) gets a generated cover instead: a colored backdrop with a
 * pattern, its color picked from a curated palette by the event, so two events don't look alike.
 */
public final class EventTheme {

    /** Curated palette for events without a photo: first entry is the platform green. */
    private static final Color[] PALETTE = {
            new Color(0x0E, 0x7A, 0x3C), // vert
            new Color(0x1D, 0x4E, 0x9E), // bleu
            new Color(0x6B, 0x3F, 0xA0), // violet
            new Color(0x0F, 0x76, 0x6E), // turquoise
            new Color(0x9F, 0x12, 0x39), // bordeaux
            new Color(0xB4, 0x53, 0x09), // ocre
            new Color(0x43, 0x38, 0xCA), // indigo
    };

    public final Color accent;
    public final Color accentDark;
    public final Color accentTint;
    private final PDImageXObject photo;

    private EventTheme(Color accent, PDImageXObject photo) {
        this.accent = accent;
        this.photo = photo;
        float[] hsb = Color.RGBtoHSB(accent.getRed(), accent.getGreen(), accent.getBlue(), null);
        this.accentDark = Color.getHSBColor(hsb[0], Math.min(1f, hsb[1] + 0.05f), hsb[2] * 0.72f);
        this.accentTint = Color.getHSBColor(hsb[0], 0.07f, 0.985f);
    }

    /**
     * @param coverUrl the event's cover URL (may be null)
     * @param seed     anything stable about the event (its name / id): picks the generated cover's color
     */
    public static EventTheme of(PDDocument doc, FileStorageService fileStorage, String coverUrl, String seed) {
        PDImageXObject photo = null;
        Color accent = null;
        byte[] bytes = fileStorage == null || coverUrl == null ? null : fileStorage.readIfLocal(coverUrl);
        if (bytes != null) {
            try {
                photo = PDImageXObject.createFromByteArray(doc, bytes, "cover");
                accent = dominantColor(bytes);
            } catch (Exception e) {
                photo = null;
            }
        }
        if (accent == null) {
            accent = seed == null ? PALETTE[0] : PALETTE[Math.floorMod(seed.hashCode(), PALETTE.length)];
        }
        return new EventTheme(accent, photo);
    }

    public boolean hasPhoto() {
        return photo != null;
    }

    /**
     * Draws the hero at the top of the page and returns the y just below it.
     *
     * @param kicker   small chip in the top-left corner (document type), may be empty
     * @param title    event name, over the bottom of the cover
     * @param subtitle e.g. date and place, under the title
     */
    public float drawHero(PDPageContentStream cs, float pageW, float pageH, float heroH, float margin,
                          String kicker, String title, String subtitle) throws IOException {
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float heroY = pageH - heroH;

        if (photo != null) {
            CoverBanner.draw(cs, photo, pageW, pageH, heroH);
        } else {
            drawGeneratedBackdrop(cs, pageW, heroY, heroH);
        }
        darkenBottom(cs, pageW, heroY, heroH);

        // Title (up to 2 lines, shrinking to fit) and subtitle, bottom-left, white on the darkened cover.
        float maxW = pageW - 2 * margin;
        float[] sizes = heroH < 120 ? new float[] {16f, 14f, 12f, 10f} : new float[] {24f, 20f, 17f, 14f};
        List<String> lines = null;
        float size = sizes[sizes.length - 1];
        for (float s : sizes) {
            List<String> l = PdfText.wrap(bold, s, maxW, title);
            if (l.size() <= 2) {
                lines = l;
                size = s;
                break;
            }
        }
        if (lines == null) {
            lines = PdfText.wrap(bold, size, maxW, title).subList(0, 2);
            String last = lines.get(1);
            lines = List.of(lines.get(0), last.length() > 3 ? last.substring(0, last.length() - 3) + "..." : last);
        }
        float lineH = size * 1.2f;
        boolean hasSub = subtitle != null && !subtitle.isBlank();
        float baseY = heroY + (hasSub ? 40 : 22);
        cs.setNonStrokingColor(Color.WHITE);
        for (int i = 0; i < lines.size(); i++) {
            PdfText.draw(cs, bold, size, margin, baseY + (lines.size() - 1 - i) * lineH, lines.get(i));
        }
        if (hasSub) {
            PdfText.draw(cs, regular, 10.5f, margin, heroY + 18, subtitle);
        }

        // Document-type chip, top-left.
        if (kicker != null && !kicker.isBlank()) {
            String k = PdfText.sanitize(kicker.toUpperCase(Locale.FRENCH));
            float w = PdfText.width(bold, 9f, k) + 20;
            cs.setNonStrokingColor(Color.WHITE);
            cs.addRect(margin, pageH - 20 - 20, w, 20);
            cs.fill();
            cs.setNonStrokingColor(accentDark);
            PdfText.draw(cs, bold, 9f, margin + 10, pageH - 20 - 20 + 6.5f, k);
        }

        // Accent line under the hero.
        cs.setNonStrokingColor(accent);
        cs.addRect(0, heroY - 5, pageW, 5);
        cs.fill();
        cs.setNonStrokingColor(Color.BLACK);
        return heroY - 5;
    }

    // ---- generated cover ----

    private void drawGeneratedBackdrop(PDPageContentStream cs, float pageW, float heroY, float heroH)
            throws IOException {
        cs.saveGraphicsState();
        cs.addRect(0, heroY, pageW, heroH);
        cs.clip();

        cs.setNonStrokingColor(accentDark);
        cs.addRect(0, heroY, pageW, heroH);
        cs.fill();

        // Soft concentric circles, top-right.
        float cx = pageW * 0.86f;
        float cy = heroY + heroH * 0.9f;
        float[] radii = {heroH * 1.25f, heroH * 0.95f, heroH * 0.65f, heroH * 0.35f};
        float[] alphas = {0.20f, 0.16f, 0.14f, 0.12f};
        for (int i = 0; i < radii.length; i++) {
            cs.saveGraphicsState();
            alpha(cs, alphas[i]);
            cs.setNonStrokingColor(i % 2 == 0 ? accent : Color.WHITE);
            circle(cs, cx, cy, radii[i]);
            cs.fill();
            cs.restoreGraphicsState();
        }

        // Fine diagonal stripes.
        cs.saveGraphicsState();
        alpha(cs, 0.07f);
        cs.setStrokingColor(Color.WHITE);
        cs.setLineWidth(1f);
        for (float x = -heroH; x < pageW; x += 16) {
            cs.moveTo(x, heroY);
            cs.lineTo(x + heroH, heroY + heroH);
            cs.stroke();
        }
        cs.restoreGraphicsState();

        cs.restoreGraphicsState();
    }

    /** Layered black rectangles anchored at the bottom: a smooth-enough gradient so white text is legible. */
    private static void darkenBottom(PDPageContentStream cs, float pageW, float heroY, float heroH)
            throws IOException {
        int layers = 44;
        float span = heroH * 0.7f;
        for (int i = 0; i < layers; i++) {
            float h = span * (layers - i) / layers;
            cs.saveGraphicsState();
            alpha(cs, 0.03f);
            cs.setNonStrokingColor(Color.BLACK);
            cs.addRect(0, heroY, pageW, h);
            cs.fill();
            cs.restoreGraphicsState();
        }
    }

    private static void alpha(PDPageContentStream cs, float a) throws IOException {
        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(a);
        gs.setStrokingAlphaConstant(a);
        cs.setGraphicsStateParameters(gs);
    }

    private static void circle(PDPageContentStream cs, float cx, float cy, float r) throws IOException {
        float k = 0.5522847498f * r;
        cs.moveTo(cx + r, cy);
        cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r);
        cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy);
        cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r);
        cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy);
        cs.closePath();
    }

    // ---- dominant color of a photo ----

    /**
     * The most "colorful" hue of the photo (weighting saturated, mid-bright pixels), normalised to a
     * vivid color dark enough for white text. Null for a photo with no real color (grayscale, etc.).
     */
    static Color dominantColor(byte[] imageBytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            BufferedImage img;
            try {
                reader.setInput(in);
                int sub = Math.max(1, Math.min(reader.getWidth(0), reader.getHeight(0)) / 64);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(sub, sub, 0, 0);
                img = reader.read(0, param);
            } finally {
                reader.dispose();
            }

            int bins = 12;
            double[] weight = new double[bins];
            double[] r = new double[bins];
            double[] g = new double[bins];
            double[] b = new double[bins];
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int rgb = img.getRGB(x, y);
                    int pr = (rgb >> 16) & 0xFF;
                    int pg = (rgb >> 8) & 0xFF;
                    int pb = rgb & 0xFF;
                    float[] hsb = Color.RGBtoHSB(pr, pg, pb, null);
                    if (hsb[1] < 0.18f || hsb[2] < 0.15f || hsb[2] > 0.97f) {
                        continue; // gray, near-black or near-white: no hue to speak of
                    }
                    double w = hsb[1] * hsb[1] * (0.4 + hsb[2]);
                    int bin = Math.min(bins - 1, (int) (hsb[0] * bins));
                    weight[bin] += w;
                    r[bin] += w * pr;
                    g[bin] += w * pg;
                    b[bin] += w * pb;
                }
            }
            int best = 0;
            for (int i = 1; i < bins; i++) {
                if (weight[i] > weight[best]) {
                    best = i;
                }
            }
            if (weight[best] < 3.0) {
                return null;
            }
            int cr = (int) (r[best] / weight[best]);
            int cg = (int) (g[best] / weight[best]);
            int cb = (int) (b[best] / weight[best]);
            float[] hsb = Color.RGBtoHSB(cr, cg, cb, null);
            float s = Math.max(0.5f, Math.min(0.9f, hsb[1]));
            float v = Math.max(0.34f, Math.min(0.58f, hsb[2]));
            return Color.getHSBColor(hsb[0], s, v);
        } catch (Exception e) {
            return null;
        }
    }
}
