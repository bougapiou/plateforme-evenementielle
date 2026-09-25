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
            new Palette(new Color(0x0E7A3C), new Color(0x065829)), // vert
            new Palette(new Color(0x1D4E9E), new Color(0x0F3572)), // bleu
            new Palette(new Color(0x6B3FA0), new Color(0x4A2873)), // violet
            new Palette(new Color(0x0F766E), new Color(0x07554F)), // turquoise
            new Palette(new Color(0x9F1239), new Color(0x720725)), // bordeaux
            new Palette(new Color(0xB45309), new Color(0x823800)), // ocre
            new Palette(new Color(0x4338CA), new Color(0x2A2191)), // indigo
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
