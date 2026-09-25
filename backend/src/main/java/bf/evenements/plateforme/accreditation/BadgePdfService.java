package bf.evenements.plateforme.accreditation;

import bf.evenements.plateforme.common.pdf.CoverBanner;
import bf.evenements.plateforme.common.pdf.PdfBrand;
import bf.evenements.plateforme.common.pdf.PdfText;
import bf.evenements.plateforme.common.storage.FileStorageService;
import bf.evenements.plateforme.common.web.QrImages;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
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

/** Renders an accreditation badge as a one-page PDF: event cover, role pill, name, QR code. */
@Service
@RequiredArgsConstructor
public class BadgePdfService {

    private static final float BAR_H = 4f;

    private final FileStorageService fileStorage;

    public byte[] render(Accreditation accr) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A6);
            doc.addPage(page);

            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            byte[] qr = QrImages.png(accr.getQrToken(), 200);
            PDImageXObject qrImage = PDImageXObject.createFromByteArray(doc, qr, "qr");
            PDImageXObject coverImage = CoverBanner.load(doc, fileStorage, accr.getEvent().getCoverUrl());

            float w = PDRectangle.A6.getWidth();
            float h = PDRectangle.A6.getHeight();

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y;
                if (coverImage != null) {
                    float bannerHeight = 76f;
                    CoverBanner.draw(cs, coverImage, w, h, bannerHeight);
                    PdfBrand.tricolorBar(cs, 0, h - bannerHeight - BAR_H, w, BAR_H);
                    y = h - bannerHeight - BAR_H - 20;
                } else {
                    PdfBrand.tricolorBar(cs, 0, h - BAR_H, w, BAR_H);
                    y = h - BAR_H - 26;
                }

                drawRolePill(cs, bold, 24, y - 15, up(accr.fonctionLabel()));
                y -= 40;

                cs.setNonStrokingColor(PdfBrand.SLATE_900);
                PdfText.draw(cs, bold, 15, 24, y, safe(accr.getPersonneNom()));
                cs.setNonStrokingColor(Color.BLACK);
                y -= 18;

                if (accr.getOrganisation() != null) {
                    cs.setNonStrokingColor(PdfBrand.SLATE_500);
                    PdfText.draw(cs, regular, 10, 24, y, safe(accr.getOrganisation()));
                    cs.setNonStrokingColor(Color.BLACK);
                    y -= 16;
                }

                cs.setNonStrokingColor(PdfBrand.SLATE_500);
                PdfText.draw(cs, regular, 9.5f, 24, y, safe(accr.getEvent().getNom()));
                y -= 14;
                PdfText.draw(cs, regular, 9, 24, y, accr.getActivity() != null
                        ? "Activite : " + safe(accr.getActivity().getTitre())
                        : "Acces : toutes les activites");
                y -= 14;
                PdfText.draw(cs, regular, 9, 24, y, "N° " + accr.getNumero());
                cs.setNonStrokingColor(Color.BLACK);
                y -= 20;

                cs.setStrokingColor(PdfBrand.SLATE_200);
                cs.setLineWidth(1);
                cs.moveTo(24, y);
                cs.lineTo(w - 24, y);
                cs.stroke();

                float qrSize = 130;
                float qrBoxSize = qrSize + 16;
                float qrY = y - 20 - qrBoxSize;
                cs.setNonStrokingColor(Color.WHITE);
                cs.addRect((w - qrBoxSize) / 2, qrY, qrBoxSize, qrBoxSize);
                cs.fill();
                cs.setStrokingColor(PdfBrand.SLATE_200);
                cs.addRect((w - qrBoxSize) / 2, qrY, qrBoxSize, qrBoxSize);
                cs.stroke();
                cs.drawImage(qrImage, (w - qrSize) / 2, qrY + 8, qrSize, qrSize);

                String caption = PdfText.sanitize("Presentez ce badge a l'accueil");
                float capW = regular.getStringWidth(caption) / 1000 * 8.5f;
                cs.setNonStrokingColor(PdfBrand.SLATE_500);
                PdfText.draw(cs, regular, 8.5f, (w - capW) / 2, qrY - 16, caption);
                cs.setNonStrokingColor(Color.BLACK);

                PdfBrand.tricolorBar(cs, 0, 0, w, BAR_H);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Génération du badge PDF impossible", e);
        }
    }

    private static void drawRolePill(PDPageContentStream cs, PDType1Font bold, float x, float y, String label)
            throws java.io.IOException {
        String s = PdfText.sanitize(label);
        float textWidth = bold.getStringWidth(s) / 1000 * 10f;
        float w = textWidth + 18, h = 19;
        cs.setNonStrokingColor(PdfBrand.GREEN);
        cs.addRect(x, y, w, h);
        cs.fill();
        cs.setNonStrokingColor(Color.WHITE);
        PdfText.draw(cs, bold, 10, x + 9, y + 6, s);
        cs.setNonStrokingColor(Color.BLACK);
    }

    private static String up(String s) {
        return s == null ? "" : s.toUpperCase(Locale.FRENCH);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
