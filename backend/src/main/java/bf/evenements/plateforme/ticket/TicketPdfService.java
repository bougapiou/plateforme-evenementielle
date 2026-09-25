package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.common.pdf.EventTheme;
import bf.evenements.plateforme.common.pdf.PdfBrand;
import bf.evenements.plateforme.common.pdf.PdfText;
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
    private static final float BAR_H = 4f;

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
            EventTheme theme = EventTheme.of(doc, fileStorage, event.getCoverUrl(), event.getNom());

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float w = PDRectangle.A5.getWidth();
                float h = PDRectangle.A5.getHeight();

                // La couverture de l'événement (ou une couverture générée) décore le billet.
                float y = theme.drawHero(cs, w, h, 160f, MARGIN, "Billet électronique", event.getNom(),
                        DATE.format(event.getDateDebut())) - 24;

                // QR encadré, centré — jamais recouvert par la couverture.
                float qrBoxSize = 170;
                float qrSize = 150;
                float qrBoxY = y - qrBoxSize;
                cs.setNonStrokingColor(Color.WHITE);
                cs.addRect((w - qrBoxSize) / 2, qrBoxY, qrBoxSize, qrBoxSize);
                cs.fill();
                cs.setStrokingColor(theme.accent);
                cs.setLineWidth(1.6f);
                cs.addRect((w - qrBoxSize) / 2, qrBoxY, qrBoxSize, qrBoxSize);
                cs.stroke();
                cs.drawImage(qrImage, (w - qrSize) / 2, qrBoxY + (qrBoxSize - qrSize) / 2, qrSize, qrSize);
                y = qrBoxY - 22;

                // Statut, en pastille aux couleurs de l'événement.
                drawStatusPill(cs, bold, w, y, ticket.getStatut().toString(), theme);
                y -= 34;

                // Informations du billet.
                y = row(cs, bold, regular, y, "Billet n°", ticket.getNumero());
                if (ticket.getEventTicket().getNom() != null) {
                    y = row(cs, bold, regular, y, "Catégorie", ticket.getEventTicket().getNom());
                }
                if (ticket.getParticipantNom() != null && !ticket.getParticipantNom().isBlank()) {
                    y = row(cs, bold, regular, y, "Participant", ticket.getParticipantNom());
                }
                if (event.getLieu() != null && !event.getLieu().isBlank()) {
                    y = row(cs, bold, regular, y, "Lieu", event.getLieu());
                }
                if (ticket.getOrder().getReference() != null) {
                    row(cs, bold, regular, y, "Commande", ticket.getOrder().getReference());
                }

                cs.setNonStrokingColor(theme.accent);
                cs.addRect(0, 0, w, BAR_H);
                cs.fill();
                cs.setNonStrokingColor(PdfBrand.SLATE_500);
                PdfText.draw(cs, regular, 8, MARGIN, BAR_H + 14,
                        "Presentez ce QR code a l'entree de l'evenement.");
                cs.setNonStrokingColor(Color.BLACK);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Génération du billet PDF impossible", e);
        }
    }

    private static void drawStatusPill(PDPageContentStream cs, PDType1Font bold, float pageWidth, float y,
                                       String status, EventTheme theme) throws java.io.IOException {
        String s = PdfText.sanitize(status);
        float textWidth = bold.getStringWidth(s) / 1000 * 9.5f;
        float w = textWidth + 22, h = 20;
        float x = (pageWidth - w) / 2;
        cs.setNonStrokingColor(theme.accentTint);
        cs.addRect(x, y - 14, w, h);
        cs.fill();
        cs.setNonStrokingColor(theme.accentDark);
        PdfText.draw(cs, bold, 9.5f, x + 11, y - 8, s);
        cs.setNonStrokingColor(Color.BLACK);
    }

    /** Draws one "label / value" row (label in gray, value in bold black) and returns the next y. */
    private static float row(PDPageContentStream cs, PDType1Font bold, PDType1Font regular, float y,
                              String label, String value) throws java.io.IOException {
        cs.setNonStrokingColor(PdfBrand.SLATE_500);
        PdfText.draw(cs, regular, 10, MARGIN, y, label);
        cs.setNonStrokingColor(PdfBrand.SLATE_900);
        PdfText.draw(cs, bold, 10, MARGIN + 100, y, value);
        cs.setNonStrokingColor(Color.BLACK);
        return y - 18;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
