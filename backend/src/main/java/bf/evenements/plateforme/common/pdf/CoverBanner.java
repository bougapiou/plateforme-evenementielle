package bf.evenements.plateforme.common.pdf;

import bf.evenements.plateforme.common.storage.FileStorageService;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Loads and draws an event's cover photo as a banner, shared by every PDF that shows one (tickets,
 * invoices, confirmations, badges) so they all crop and position it the same way.
 */
public final class CoverBanner {

    private CoverBanner() {
    }

    /** Best-effort: a missing, unreadable or corrupt cover image never breaks the PDF (returns null). */
    public static PDImageXObject load(PDDocument doc, FileStorageService fileStorage, String coverUrl) {
        if (coverUrl == null) {
            return null;
        }
        byte[] bytes = fileStorage.readIfLocal(coverUrl);
        if (bytes == null) {
            return null;
        }
        try {
            return PDImageXObject.createFromByteArray(doc, bytes, "cover");
        } catch (Exception e) {
            return null;
        }
    }

    /** Crops the image to fill the banner (like CSS {@code object-fit: cover}), anchored at the top. */
    public static void draw(PDPageContentStream cs, PDImageXObject cover, float pageWidth, float pageHeight,
                            float bannerHeight) throws IOException {
        float bannerY = pageHeight - bannerHeight;
        float scale = Math.max(pageWidth / cover.getWidth(), bannerHeight / cover.getHeight());
        float drawW = cover.getWidth() * scale;
        float drawH = cover.getHeight() * scale;
        float x = (pageWidth - drawW) / 2;
        float y = bannerY - (drawH - bannerHeight) / 2;

        cs.saveGraphicsState();
        cs.addRect(0, bannerY, pageWidth, bannerHeight);
        cs.clip();
        cs.drawImage(cover, x, y, drawW, drawH);
        cs.restoreGraphicsState();
    }
}
