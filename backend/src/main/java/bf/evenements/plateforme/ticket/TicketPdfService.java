package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.common.storage.FileStorageService;
import bf.evenements.plateforme.common.web.QrImages;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

/**
 * Renders an e-ticket as a one-page PDF, dans le même ordre que l'écran
 * « détail du billet » de l'application : couverture de l'événement (si
 * disponible), QR code encadré, statut, nom de l'événement, puis les
 * informations du billet. Le QR est toujours dessiné, jamais recouvert par
 * la couverture.
 */
@Service
@RequiredArgsConstructor
public class TicketPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("EEEE d MMMM yyyy 'à' HH'h'mm", Locale.FRENCH)
            .withZone(ZoneId.of("Africa/Ouagadougou"));

    private static final float MARGIN = 40f;
    private static final Color LABEL_COLOR = new Color(0x64, 0x74, 0x8B);
    private static final Color BORDER_COLOR = new Color(0xE2, 0xE8, 0xF0);

    private final FileStorageService fileStorage;

    public byte[] render(Ticket ticket, String qrToken) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A5);
            doc.addPage(page);

            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            byte[] qr = QrImages.png(qrToken, 220);
            PDImageXObject qrImage = PDImageXObject.createFromByteArray(doc, qr, "qr");

            var event = ticket.getEvent();
            PDImageXObject coverImage = loadCover(doc, event.getCoverUrl());

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float w = PDRectangle.A5.getWidth();
                float h = PDRectangle.A5.getHeight();
                float y = h - MARGIN;

                if (coverImage != null) {
                    float bannerHeight = 130f;
                    drawCoverBanner(cs, coverImage, w, h, bannerHeight);
                    y = h - bannerHeight - 24;
                }

                // QR encadré, centré — comme sur l'écran de détail.
                float qrBoxSize = 170;
                float qrSize = 150;
                float qrBoxY = y - qrBoxSize;
                cs.setStrokingColor(BORDER_COLOR);
                cs.addRect((w - qrBoxSize) / 2, qrBoxY, qrBoxSize, qrBoxSize);
                cs.stroke();
                cs.drawImage(qrImage, (w - qrSize) / 2, qrBoxY + (qrBoxSize - qrSize) / 2, qrSize, qrSize);
                y = qrBoxY - 22;

                // Statut, centré.
                centeredLine(cs, regular, 10, w, y, ticket.getStatut().toString());
                y -= 26;

                // Nom de l'événement.
                line(cs, bold, 16, MARGIN, y, safe(event.getNom()));
                y -= 26;

                // Informations du billet, dans le même ordre que l'écran mobile.
                y = row(cs, bold, regular, y, "Billet n°", ticket.getNumero());
                if (ticket.getEventTicket().getNom() != null) {
                    y = row(cs, bold, regular, y, "Catégorie", ticket.getEventTicket().getNom());
                }
                if (ticket.getParticipantNom() != null && !ticket.getParticipantNom().isBlank()) {
                    y = row(cs, bold, regular, y, "Participant", ticket.getParticipantNom());
                }
                y = row(cs, bold, regular, y, "Date", DATE.format(event.getDateDebut()));
                if (event.getLieu() != null && !event.getLieu().isBlank()) {
                    y = row(cs, bold, regular, y, "Lieu", event.getLieu());
                }
                if (ticket.getOrder().getReference() != null) {
                    row(cs, bold, regular, y, "Commande", ticket.getOrder().getReference());
                }

                line(cs, regular, 8, MARGIN, 26, "Presentez ce QR code a l'entree de l'evenement.");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Génération du billet PDF impossible", e);
        }
    }

    /** Best-effort: a missing/unreadable/corrupt cover image never breaks the PDF. */
    private PDImageXObject loadCover(PDDocument doc, String coverUrl) {
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

    /** Crops the cover image to fill the banner width/height (like CSS object-fit: cover). */
    private static void drawCoverBanner(PDPageContentStream cs, PDImageXObject cover,
                                         float pageWidth, float pageHeight, float bannerHeight)
            throws java.io.IOException {
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

    private static void line(PDPageContentStream cs, PDType1Font font, float sizePt, float x, float y,
                             String text) throws java.io.IOException {
        cs.beginText();
        cs.setFont(font, sizePt);
        cs.newLineAtOffset(x, y);
        cs.showText(winAnsi(text));
        cs.endText();
    }

    private static void centeredLine(PDPageContentStream cs, PDType1Font font, float sizePt,
                                       float pageWidth, float y, String text) throws java.io.IOException {
        String safeText = winAnsi(text);
        float width = font.getStringWidth(safeText) / 1000 * sizePt;
        line(cs, font, sizePt, (pageWidth - width) / 2, y, text);
    }

    /** Draws one "label / value" row (label in gray, value in bold black) and returns the next y. */
    private static float row(PDPageContentStream cs, PDType1Font bold, PDType1Font regular, float y,
                              String label, String value) throws java.io.IOException {
        cs.setNonStrokingColor(LABEL_COLOR);
        line(cs, regular, 10, MARGIN, y, label);
        cs.setNonStrokingColor(Color.BLACK);
        line(cs, bold, 10, MARGIN + 100, y, value);
        return y - 18;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** Keep only characters the standard Helvetica (WinAnsi) font can encode. */
    private static String winAnsi(String text) {
        if (text == null) {
            return "";
        }
        String t = text
                .replace('’', '\'').replace('‘', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-')
                .replace('…', ' ').replace(' ', ' ');
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
