package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.common.web.QrImages;
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

/** Renders an e-ticket as a one-page PDF with its QR code. */
@Service
@RequiredArgsConstructor
public class TicketPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("EEEE d MMMM yyyy 'à' HH'h'mm", Locale.FRENCH)
            .withZone(ZoneId.of("Africa/Ouagadougou"));

    public byte[] render(Ticket ticket, String qrToken) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A5);
            doc.addPage(page);

            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            byte[] qr = QrImages.png(qrToken, 220);
            PDImageXObject qrImage = PDImageXObject.createFromByteArray(doc, qr, "qr");

            var event = ticket.getEvent();
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float w = PDRectangle.A5.getWidth();
                float top = PDRectangle.A5.getHeight() - 40;

                line(cs, bold, 18, 40, top, "BILLET ÉLECTRONIQUE");
                line(cs, bold, 15, 40, top - 34, safe(event.getNom()));
                line(cs, regular, 10, 40, top - 54, DATE.format(event.getDateDebut()));
                line(cs, regular, 10, 40, top - 70,
                        safe(event.getLieu()) + (event.getVille() != null ? " · " + event.getVille() : ""));

                line(cs, regular, 11, 40, top - 110, "Participant : " + safe(ticket.getParticipantNom()));
                line(cs, regular, 11, 40, top - 128, "Catégorie : " + safe(ticket.getEventTicket().getNom()));
                line(cs, regular, 11, 40, top - 146, "N° billet : " + ticket.getNumero());
                line(cs, regular, 11, 40, top - 164, "Statut : " + ticket.getStatut());

                float qrSize = 150;
                cs.drawImage(qrImage, (w - qrSize) / 2, 40, qrSize, qrSize);
                line(cs, regular, 8, 40, 26,
                        "Presentez ce QR code a l'entree - ref " + qrToken.substring(0, 12));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Génération du billet PDF impossible", e);
        }
    }

    private static void line(PDPageContentStream cs, PDType1Font font, float sizePt, float x, float y,
                             String text) throws java.io.IOException {
        cs.beginText();
        cs.setFont(font, sizePt);
        cs.newLineAtOffset(x, y);
        cs.showText(winAnsi(text));
        cs.endText();
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
