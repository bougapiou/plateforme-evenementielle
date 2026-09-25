package bf.evenements.plateforme.common.pdf;

import java.awt.Color;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

/**
 * Vector art shared by every PDF: the flag-colored emblem, the ribbon and the fabric-pattern corner, the
 * gold-on-navy icons, glass panels, chips and the QR card. All coordinates are PDF points, y up.
 */
public final class PdfArt {

    public static final Color GOLD = new Color(0xF4C531);
    public static final Color NAVY = new Color(0x0A1226);
    public static final Color PANEL = new Color(5, 11, 30);
    public static final Color RED = new Color(0xD2, 0x10, 0x34);
    public static final Color GREEN = new Color(0x0E, 0x7A, 0x3C);
    /** White at 65 % over the dark veil: secondary text, without needing transparency for text. */
    public static final Color SOFT_WHITE = new Color(0xB4, 0xB8, 0xC2);

    public enum Icon {
        PERSON, TICKET, CALENDAR, CLOCK, PIN, NUMBER, SHIELD, PHONE, SCAN
    }

    private static final float K = 0.5522847498f;

    private PdfArt() {
    }

    // ---------- primitives ----------

    public static void alpha(PDPageContentStream cs, float a) throws IOException {
        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(a);
        gs.setStrokingAlphaConstant(a);
        cs.setGraphicsStateParameters(gs);
    }

    /** Path only (no paint): a rectangle with rounded corners. */
    public static void roundedRect(PDPageContentStream cs, float x, float y, float w, float h, float r)
            throws IOException {
        float k = K * r;
        cs.moveTo(x + r, y);
        cs.lineTo(x + w - r, y);
        cs.curveTo(x + w - r + k, y, x + w, y + r - k, x + w, y + r);
        cs.lineTo(x + w, y + h - r);
        cs.curveTo(x + w, y + h - r + k, x + w - r + k, y + h, x + w - r, y + h);
        cs.lineTo(x + r, y + h);
        cs.curveTo(x + r - k, y + h, x, y + h - r + k, x, y + h - r);
        cs.lineTo(x, y + r);
        cs.curveTo(x, y + r - k, x + r - k, y, x + r, y);
        cs.closePath();
    }

    /** Path only: a circle. */
    public static void circle(PDPageContentStream cs, float cx, float cy, float r) throws IOException {
        float k = K * r;
        cs.moveTo(cx + r, cy);
        cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r);
        cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy);
        cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r);
        cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy);
        cs.closePath();
    }

    /** Semi-transparent dark rounded panel ("glass") on top of the photo. */
    public static void glassPanel(PDPageContentStream cs, float x, float y, float w, float h, float r,
                                  float alpha) throws IOException {
        cs.saveGraphicsState();
        alpha(cs, alpha);
        cs.setNonStrokingColor(PANEL);
        roundedRect(cs, x, y, w, h, r);
        cs.fill();
        cs.restoreGraphicsState();
    }

    /** Thin white outline inset from the page edge. */
    public static void frame(PDPageContentStream cs, float x, float y, float w, float h, float r, float lineWidth,
                             float alpha) throws IOException {
        cs.saveGraphicsState();
        alpha(cs, alpha);
        cs.setStrokingColor(Color.WHITE);
        cs.setLineWidth(lineWidth);
        roundedRect(cs, x, y, w, h, r);
        cs.stroke();
        cs.restoreGraphicsState();
    }

    // ---------- brand art ----------

    /** Round emblem: flag colors (red over green) with a gold star and a white ring. */
    public static void emblem(PDPageContentStream cs, float cx, float cy, float r) throws IOException {
        cs.saveGraphicsState();
        circle(cs, cx, cy, r);
        cs.clip();
        cs.setNonStrokingColor(RED);
        cs.addRect(cx - r, cy, 2 * r, r);
        cs.fill();
        cs.setNonStrokingColor(GREEN);
        cs.addRect(cx - r, cy - r, 2 * r, r);
        cs.fill();
        cs.restoreGraphicsState();

        cs.setStrokingColor(Color.WHITE);
        cs.setLineWidth(r * 0.07f);
        circle(cs, cx, cy, r);
        cs.stroke();

        float outer = r * 0.47f;
        float inner = r * 0.19f;
        cs.setNonStrokingColor(GOLD);
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 2 + i * Math.PI / 5;
            float rad = i % 2 == 0 ? outer : inner;
            float px = cx + (float) (rad * Math.cos(a));
            float py = cy + (float) (rad * Math.sin(a));
            if (i == 0) {
                cs.moveTo(px, py);
            } else {
                cs.lineTo(px, py);
            }
        }
        cs.closePath();
        cs.fill();
    }

    /** Three curved bands (red, gold, green) rising from the bottom-left corner. (x, y) = bottom-left of the art. */
    public static void swoosh(PDPageContentStream cs, float x, float y, float w, float h) throws IOException {
        float sx = w / 300f;
        float sy = h / 120f;
        // SVG-like source coordinates (300 x 120, y down) converted on the fly.
        float[][][] bands = {
                {{0, 22}, {70, 34}, {130, 82}, {178, 120}, {120, 120}, {84, 92}, {42, 68}, {0, 60}},
                {{0, 60}, {42, 68}, {84, 92}, {120, 120}, {82, 120}, {52, 100}, {26, 88}, {0, 84}},
                {{0, 84}, {26, 88}, {52, 100}, {82, 120}, {0, 120}}
        };
        Color[] colors = {RED, GOLD, GREEN};
        for (int b = 0; b < 3; b++) {
            float[][] p = bands[b];
            cs.setNonStrokingColor(colors[b]);
            cs.moveTo(x + p[0][0] * sx, y + (120 - p[0][1]) * sy);
            if (b < 2) {
                cs.curveTo(x + p[1][0] * sx, y + (120 - p[1][1]) * sy, x + p[2][0] * sx, y + (120 - p[2][1]) * sy,
                        x + p[3][0] * sx, y + (120 - p[3][1]) * sy);
                cs.lineTo(x + p[4][0] * sx, y + (120 - p[4][1]) * sy);
                cs.curveTo(x + p[5][0] * sx, y + (120 - p[5][1]) * sy, x + p[6][0] * sx, y + (120 - p[6][1]) * sy,
                        x + p[7][0] * sx, y + (120 - p[7][1]) * sy);
            } else {
                cs.curveTo(x + p[1][0] * sx, y + (120 - p[1][1]) * sy, x + p[2][0] * sx, y + (120 - p[2][1]) * sy,
                        x + p[3][0] * sx, y + (120 - p[3][1]) * sy);
                cs.lineTo(x + p[4][0] * sx, y + (120 - p[4][1]) * sy);
            }
            cs.closePath();
            cs.fill();
        }
    }

    /** Small diamonds (fabric pattern) filling the bottom-right corner, as a triangle. */
    public static void patternCorner(PDPageContentStream cs, float pageWidth, float size) throws IOException {
        float step = size / 6.8f;
        Color[] colors = {RED, GOLD, GREEN, Color.WHITE};
        int k = 0;
        cs.saveGraphicsState();
        alpha(cs, 0.9f);
        int n = (int) Math.ceil(size / step);
        for (int iy = 0; iy < n; iy++) {
            for (int ix = 0; ix < n; ix++) {
                float lx = ix * step;
                float ly = iy * step;
                if (lx + ly < size * 0.9f) {
                    continue;
                }
                float cx = pageWidth - size + lx + step / 2;
                float cy = size - (ly + step / 2);
                float r = step / 2 - step * 0.11f;
                cs.setNonStrokingColor(colors[k++ % colors.length]);
                cs.moveTo(cx, cy + r);
                cs.lineTo(cx + r, cy);
                cs.lineTo(cx, cy - r);
                cs.lineTo(cx - r, cy);
                cs.closePath();
                cs.fill();
            }
        }
        cs.restoreGraphicsState();
    }

    // ---------- chips & QR card ----------

    /** A rounded gold (or any color) chip with bold text; returns its width. (x, yBottom) = bottom-left. */
    public static float chip(PDPageContentStream cs, PdfFonts f, float x, float yBottom, String label, float size,
                             float padX, float padY, float spacing, Color bg, Color fg) throws IOException {
        float textW = f.widthSpaced(f.bold, size, label, spacing);
        float w = textW + 2 * padX;
        float h = size + 2 * padY;
        cs.setNonStrokingColor(bg);
        roundedRect(cs, x, yBottom, w, h, Math.min(h * 0.28f, 8));
        cs.fill();
        cs.setNonStrokingColor(fg);
        f.drawSpaced(cs, f.bold, size, x + padX, yBottom + padY + size * 0.2f, label, spacing);
        return w;
    }

    /**
     * White rounded card holding the QR, with a gold "SCAN POUR CONTRÔLE" strip under it.
     * (x, yTop) = top-left; returns the card's bottom y.
     */
    public static float qrCard(PDPageContentStream cs, PdfFonts f, PDImageXObject qr, float x, float yTop,
                               float qrSize, float pad, float stripH, float radius, String label)
            throws IOException {
        float w = qrSize + 2 * pad;
        float qrGap = pad * 0.8f;
        float h = pad + qrSize + qrGap + stripH;
        float y = yTop - h;

        cs.saveGraphicsState();
        alpha(cs, 0.28f);
        cs.setNonStrokingColor(Color.BLACK);
        roundedRect(cs, x + 1.5f, y - 3, w, h, radius);
        cs.fill();
        cs.restoreGraphicsState();

        cs.setNonStrokingColor(Color.WHITE);
        roundedRect(cs, x, y, w, h, radius);
        cs.fill();

        cs.saveGraphicsState();
        roundedRect(cs, x, y, w, h, radius);
        cs.clip();
        cs.setNonStrokingColor(GOLD);
        cs.addRect(x, y, w, stripH);
        cs.fill();
        cs.restoreGraphicsState();

        cs.drawImage(qr, x + pad, y + stripH + qrGap, qrSize, qrSize);

        float iconSize = stripH * 0.52f;
        float maxText = w - 2 * pad * 0.6f - iconSize - 6;
        float size = f.fitSize(f.bold, label, maxText, stripH * 0.42f, 6f);
        float textW = f.width(f.bold, size, label);
        float groupW = iconSize + 6 + textW;
        float gx = x + (w - groupW) / 2;
        icon(cs, Icon.SCAN, gx, y + (stripH - iconSize) / 2, iconSize, NAVY, NAVY);
        cs.setNonStrokingColor(NAVY);
        f.draw(cs, f.bold, size, gx + iconSize + 6, y + stripH / 2 - size * 0.32f, label);
        cs.setNonStrokingColor(Color.BLACK);
        return y;
    }

    // ---------- icons (24 x 24 grid, y down, like the mockup) ----------

    private static final class Pen {
        final PDPageContentStream cs;
        final float x0;
        final float y0;
        final float s;

        Pen(PDPageContentStream cs, float x0, float y0, float s) {
            this.cs = cs;
            this.x0 = x0;
            this.y0 = y0;
            this.s = s;
        }

        float x(float gx) {
            return x0 + gx * s;
        }

        float y(float gy) {
            return y0 + (24 - gy) * s;
        }

        void m(float gx, float gy) throws IOException {
            cs.moveTo(x(gx), y(gy));
        }

        void l(float gx, float gy) throws IOException {
            cs.lineTo(x(gx), y(gy));
        }

        void c(float a, float b, float c, float d, float e, float f) throws IOException {
            cs.curveTo(x(a), y(b), x(c), y(d), x(e), y(f));
        }

        void circle(float gx, float gy, float r) throws IOException {
            PdfArt.circle(cs, x(gx), y(gy), r * s);
        }

        void rrect(float gx, float gy, float w, float h, float r) throws IOException {
            PdfArt.roundedRect(cs, x(gx), y(gy + h), w * s, h * s, r * s);
        }
    }

    /** Draws an icon in {@code size} points, its bottom-left at (x, y). {@code cut} = the color of "holes". */
    public static void icon(PDPageContentStream cs, Icon icon, float x, float y, float size, Color color,
                            Color cut) throws IOException {
        Pen p = new Pen(cs, x, y, size / 24f);
        float s = p.s;
        cs.saveGraphicsState();
        cs.setLineCapStyle(1);
        cs.setLineJoinStyle(1);
        cs.setNonStrokingColor(color);
        cs.setStrokingColor(color);
        switch (icon) {
            case PERSON -> {
                p.circle(12, 8, 4);
                cs.fill();
                p.m(4, 21);
                p.c(4, 16.6f, 7.6f, 13, 12, 13);
                p.c(16.4f, 13, 20, 16.6f, 20, 21);
                cs.closePath();
                cs.fill();
            }
            case TICKET -> {
                p.rrect(4, 6, 16, 12, 2);
                cs.fill();
                cs.setNonStrokingColor(cut);
                p.circle(4, 12, 2.3f);
                cs.fill();
                p.circle(20, 12, 2.3f);
                cs.fill();
                cs.setStrokingColor(cut);
                cs.setLineWidth(1.4f * s);
                cs.setLineDashPattern(new float[] {1.6f * s, 1.6f * s}, 0);
                p.m(14, 6.6f);
                p.l(14, 17.4f);
                cs.stroke();
                cs.setLineDashPattern(new float[] {}, 0);
            }
            case CALENDAR -> {
                p.rrect(3, 5, 18, 16, 2.5f);
                cs.fill();
                cs.setLineWidth(2f * s);
                p.m(8, 3);
                p.l(8, 7);
                cs.stroke();
                p.m(16, 3);
                p.l(16, 7);
                cs.stroke();
                cs.setNonStrokingColor(cut);
                float[][] cells = {{6.5f, 11}, {10.5f, 11}, {14.5f, 11}, {6.5f, 15.4f}, {10.5f, 15.4f}};
                for (float[] c : cells) {
                    p.rrect(c[0], c[1], 3, 2.6f, 0.6f);
                    cs.fill();
                }
            }
            case CLOCK -> {
                cs.setLineWidth(2.2f * s);
                p.circle(12, 12, 9);
                cs.stroke();
                p.m(12, 7);
                p.l(12, 12.2f);
                p.l(15.2f, 14.2f);
                cs.stroke();
            }
            case PIN -> {
                p.m(12, 22);
                p.c(12, 22, 19.5f, 15.5f, 19.5f, 10);
                p.c(19.5f, 5.86f, 16.14f, 2.5f, 12, 2.5f);
                p.c(7.86f, 2.5f, 4.5f, 5.86f, 4.5f, 10);
                p.c(4.5f, 15.5f, 12, 22, 12, 22);
                cs.closePath();
                cs.fill();
                cs.setNonStrokingColor(cut);
                p.circle(12, 10, 2.6f);
                cs.fill();
            }
            case NUMBER -> {
                p.rrect(3, 6, 18, 12, 2.5f);
                cs.fill();
                cs.setStrokingColor(cut);
                cs.setLineWidth(1.4f * s);
                cs.setLineDashPattern(new float[] {1.6f * s, 1.6f * s}, 0);
                p.m(9.5f, 6.6f);
                p.l(9.5f, 17.4f);
                cs.stroke();
                cs.setLineDashPattern(new float[] {}, 0);
                cs.setLineWidth(1.6f * s);
                p.m(13, 10.5f);
                p.l(18, 10.5f);
                cs.stroke();
                p.m(13, 13.5f);
                p.l(16.5f, 13.5f);
                cs.stroke();
            }
            case SHIELD -> {
                p.m(12, 2.5f);
                p.l(20, 5.5f);
                p.l(20, 11.5f);
                p.c(20, 16.7f, 16.6f, 20.1f, 12, 21.5f);
                p.c(7.4f, 20.1f, 4, 16.7f, 4, 11.5f);
                p.l(4, 5.5f);
                cs.closePath();
                cs.fill();
                cs.setStrokingColor(cut);
                cs.setLineWidth(2f * s);
                p.m(8.6f, 12.2f);
                p.l(11.1f, 14.7f);
                p.l(15.5f, 9.8f);
                cs.stroke();
            }
            case PHONE -> {
                cs.setLineWidth(1.8f * s);
                p.rrect(6.5f, 2.5f, 11, 19, 2.6f);
                cs.stroke();
                cs.setLineWidth(1.5f * s);
                p.m(9.6f, 9.4f);
                p.l(9.6f, 8.4f);
                p.l(10.6f, 8.4f);
                cs.stroke();
                p.m(13.4f, 8.4f);
                p.l(14.4f, 8.4f);
                p.l(14.4f, 9.4f);
                cs.stroke();
                p.m(14.4f, 13.6f);
                p.l(14.4f, 14.6f);
                p.l(13.4f, 14.6f);
                cs.stroke();
                p.m(10.4f, 14.6f);
                p.l(9.4f, 14.6f);
                p.l(9.4f, 13.6f);
                cs.stroke();
            }
            case SCAN -> {
                cs.setLineWidth(2.2f * s);
                p.m(4, 8);
                p.l(4, 6);
                p.c(4, 4.9f, 4.9f, 4, 6, 4);
                p.l(8, 4);
                cs.stroke();
                p.m(16, 4);
                p.l(18, 4);
                p.c(19.1f, 4, 20, 4.9f, 20, 6);
                p.l(20, 8);
                cs.stroke();
                p.m(20, 16);
                p.l(20, 18);
                p.c(20, 19.1f, 19.1f, 20, 18, 20);
                p.l(16, 20);
                cs.stroke();
                p.m(8, 20);
                p.l(6, 20);
                p.c(4.9f, 20, 4, 19.1f, 4, 18);
                p.l(4, 16);
                cs.stroke();
                p.m(4, 12);
                p.l(20, 12);
                cs.stroke();
            }
        }
        cs.restoreGraphicsState();
    }
}
