package bf.evenements.plateforme.common.pdf;

import bf.evenements.plateforme.common.storage.FileStorageService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * The event's cover photo as the FULL-PAGE background of a document, darkened so white and gold text stay
 * readable. The photo (or, without one, a generated cover) and the dark veil are composed into a single
 * JPEG sized for the page — so a 5 MB cover photo doesn't bloat every PDF, and the page is one image.
 */
public final class PdfBackdrop {

    /** Where the veil is darkest. LANDSCAPE: on the left (the ticket's info panel); PORTRAIT: at the bottom. */
    public enum Veil {
        LANDSCAPE, PORTRAIT
    }

    private PdfBackdrop() {
    }

    public static PDImageXObject create(PDDocument doc, FileStorageService fileStorage, String coverUrl,
                                        String seed, int pxW, int pxH, Veil veil) throws IOException {
        BufferedImage img = new BufferedImage(pxW, pxH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            BufferedImage photo = readCover(fileStorage, coverUrl, pxW, pxH);
            if (photo != null) {
                paintCover(g, photo, pxW, pxH);
            } else {
                EventTheme.paintGenerated(g, pxW, pxH, seed);
            }
            paintVeil(g, pxW, pxH, veil);
        } finally {
            g.dispose();
        }
        return JPEGFactory.createFromImage(doc, img, 0.86f);
    }

    /** Best-effort: null when there is no photo, or it can't be decoded (CMYK, corrupt…). */
    private static BufferedImage readCover(FileStorageService fileStorage, String coverUrl, int pxW, int pxH) {
        byte[] bytes = fileStorage == null || coverUrl == null ? null : fileStorage.readIfLocal(coverUrl);
        if (bytes == null) {
            return null;
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                // Decode at a reduced size (integer subsampling) while staying at least as large as the page.
                int sub = Math.max(1, Math.min(reader.getWidth(0) / pxW, reader.getHeight(0) / pxH));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(sub, sub, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Scales and crops to fill the page (like CSS {@code object-fit: cover}), centered. */
    private static void paintCover(Graphics2D g, BufferedImage photo, int pxW, int pxH) {
        double scale = Math.max((double) pxW / photo.getWidth(), (double) pxH / photo.getHeight());
        int w = (int) Math.ceil(photo.getWidth() * scale);
        int h = (int) Math.ceil(photo.getHeight() * scale);
        g.drawImage(photo, (pxW - w) / 2, (pxH - h) / 2, w, h, null);
    }

    private static Color navy(double alpha) {
        return new Color(4, 10, 28, (int) Math.round(alpha * 255));
    }

    private static void paintVeil(Graphics2D g, int w, int h, Veil veil) {
        if (veil == Veil.LANDSCAPE) {
            g.setPaint(new LinearGradientPaint(new Point2D.Float(0, 0), new Point2D.Float(w, 0),
                    new float[] {0f, 0.46f, 1f}, new Color[] {navy(0.82), navy(0.55), navy(0.12)}));
            g.fillRect(0, 0, w, h);
            g.setPaint(new LinearGradientPaint(new Point2D.Float(0, h), new Point2D.Float(0, h * 0.54f),
                    new float[] {0f, 1f}, new Color[] {navy(0.55), navy(0)}));
            g.fillRect(0, 0, w, h);
        } else {
            g.setPaint(new LinearGradientPaint(new Point2D.Float(0, 0), new Point2D.Float(0, h),
                    new float[] {0f, 0.34f, 1f}, new Color[] {navy(0.55), navy(0.78), navy(0.90)}));
            g.fillRect(0, 0, w, h);
        }
    }
}
