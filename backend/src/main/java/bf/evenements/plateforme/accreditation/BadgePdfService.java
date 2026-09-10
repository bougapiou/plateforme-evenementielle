package bf.evenements.plateforme.accreditation;

import bf.evenements.plateforme.common.web.QrImages;
import java.io.ByteArrayOutputStream;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

/** Renders an accreditation badge as a one-page PDF with its QR code. */
@Service
@RequiredArgsConstructor
public class BadgePdfService {

    public byte[] render(Accreditation accr) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A6);
            doc.addPage(page);

            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            byte[] qr = QrImages.png(accr.getQrToken(), 200);
            PDImageXObject qrImage = PDImageXObject.createFromByteArray(doc, qr, "qr");

            float w = PDRectangle.A6.getWidth();
            float top = PDRectangle.A6.getHeight() - 34;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                line(cs, bold, 16, 30, top, "BADGE - " + up(accr.fonctionLabel()));
                line(cs, bold, 14, 30, top - 30, safe(accr.getPersonneNom()));
                if (accr.getOrganisation() != null) {
                    line(cs, regular, 10, 30, top - 48, safe(accr.getOrganisation()));
                }
                line(cs, regular, 10, 30, top - 74, safe(accr.getEvent().getNom()));
                line(cs, regular, 9, 30, top - 90, accr.getActivity() != null
                        ? "Activité : " + safe(accr.getActivity().getTitre())
                        : "Accès : toutes les activités");
                line(cs, regular, 9, 30, top - 106, "N° " + accr.getNumero());

                float qrSize = 130;
                cs.drawImage(qrImage, (w - qrSize) / 2, 24, qrSize, qrSize);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Génération du badge PDF impossible", e);
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

    private static String up(String s) {
        return s == null ? "" : s.toUpperCase(java.util.Locale.FRENCH);
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
                .replace('…', ' ').replace(' ', ' ');
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
