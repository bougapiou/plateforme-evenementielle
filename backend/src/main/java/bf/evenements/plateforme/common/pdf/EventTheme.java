package bf.evenements.plateforme.common.pdf;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;

/**
 * Cover for an event WITHOUT a photo: a colored backdrop with soft circles and fine stripes, its color
 * picked from a curated palette by the event (same palette and same pick rule as the web and mobile
 * apps, so an event has one color everywhere).
 */
public final class EventTheme {

    public record Palette(Color accent, Color dark) {
    }

    private static final Palette[] PALETTE = {
            new Palette(new Color(0x00843B), new Color(0x045527)), // vert
            new Palette(new Color(0x2A6D9B), new Color(0x173F5F)), // bleu
            new Palette(new Color(0xB07800), new Color(0x7D5500)), // ocre
            new Palette(new Color(0x026C31), new Color(0x04411F)), // vert foncé
            new Palette(new Color(0x635C55), new Color(0x332E2A)), // neutre
            new Palette(new Color(0x173F5F), new Color(0x0D2639)), // bleu nuit
            new Palette(new Color(0xA39300), new Color(0x5C5300)), // or
    };

    private EventTheme() {
    }

    /** Java's {@code String.hashCode()} floor-mod the palette size, like the web and mobile apps. */
    public static Palette paletteFor(String seed) {
        return seed == null ? PALETTE[0] : PALETTE[Math.floorMod(seed.hashCode(), PALETTE.length)];
    }

    /** Paints the generated cover over the whole {@code w} x {@code h} pixel area. */
    public static void paintGenerated(Graphics2D g, int w, int h, String seed) {
        Palette p = paletteFor(seed);
        g.setColor(p.dark());
        g.fillRect(0, 0, w, h);

        float base = Math.min(w, h);
        double cx = w * 0.86;
        double cy = h * 0.12;
        double[] radii = {base * 0.95, base * 0.72, base * 0.50, base * 0.28};
        float[] alphas = {0.20f, 0.16f, 0.14f, 0.12f};
        for (int i = 0; i < radii.length; i++) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphas[i]));
            g.setColor(i % 2 == 0 ? p.accent() : Color.WHITE);
            g.fill(new Ellipse2D.Double(cx - radii[i], cy - radii[i], radii[i] * 2, radii[i] * 2));
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.07f));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(Math.max(1f, w / 900f)));
        int step = Math.max(12, Math.round(w / 55f));
        for (int x = -h; x < w; x += step) {
            g.drawLine(x, h, x + h, 0);
        }
        g.setComposite(AlphaComposite.SrcOver);
    }
}
