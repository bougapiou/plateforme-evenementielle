package bf.evenements.plateforme.accreditation;

import bf.evenements.plateforme.common.pdf.PdfArt;
import bf.evenements.plateforme.common.pdf.PdfBackdrop;
import bf.evenements.plateforme.common.pdf.PdfFonts;
import bf.evenements.plateforme.common.storage.FileStorageService;
import bf.evenements.plateforme.common.web.QrImages;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

/**
 * Renders an accreditation badge as a one-page A6 PDF, in the same design as the ticket: the event's cover
 * photo as the full background, the PNE emblem, the event name in two-tone type, the function chip, the
 * person, and a large QR card whose size takes whatever room the text leaves.
 */
@Service
@RequiredArgsConstructor
public class BadgePdfService {

    private static final float W = PDRectangle.A6.getWidth();
    private static final float H = PDRectangle.A6.getHeight();
    /** Design-mockup pixels (397 x 559) to points. */
    private static final float S = W / 397f;

    private final FileStorageService fileStorage;

    private static float px(float v) {
        return v * S;
    }

    public byte[] render(Accreditation accr) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A6);
            doc.addPage(page);
            PdfFonts f = PdfFonts.load(doc);

            var event = accr.getEvent();
            PDImageXObject background = PdfBackdrop.create(doc, fileStorage, event.getCoverUrl(), event.getNom(),
                    900, Math.round(900 * H / W), PdfBackdrop.Veil.PORTRAIT);
            PDImageXObject qr = PDImageXObject.createFromByteArray(doc, QrImages.png(accr.getQrToken(), 600), "qr");

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.saveGraphicsState();
                PdfArt.roundedRect(cs, 0, 0, W, H, px(20));
                cs.clip();

                cs.drawImage(background, 0, 0, W, H);
                PdfArt.frame(cs, px(10), px(10), W - 2 * px(10), H - 2 * px(10), px(15), 0.7f, 0.5f);
                PdfArt.swoosh(cs, 0, 0, px(180), px(72));
                PdfArt.patternCorner(cs, W, px(60));

                // brand
                PdfArt.emblem(cs, px(26 + 22), H - px(26 + 22), px(44) * 0.4286f);
                cs.setNonStrokingColor(Color.WHITE);
                f.drawSpaced(cs, f.bold, px(24), px(26 + 44 + 9), H - px(26 + 20), "PNE", px(1));
                f.draw(cs, f.regular, px(8.5f), px(26 + 44 + 9), H - px(26 + 30), "Plateforme Nationale");
                f.draw(cs, f.regular, px(8.5f), px(26 + 44 + 9), H - px(26 + 40), "de Gestion des Événements");

                float y = drawTitle(cs, f, event.getNom());

                // function chip
                String role = up(accr.fonctionLabel());
                float chipSize = px(14);
                float chipH = chipSize + 2 * px(6);
                y += px(12);
                PdfArt.chip(cs, f, px(26), H - y - chipH, role, chipSize, px(16), px(6), px(1.6f), PdfArt.GOLD,
                        PdfArt.NAVY);
                y += chipH + px(10);

                // person
                String name = accr.getPersonneNom() == null ? "" : accr.getPersonneNom();
                float nameSize = f.fitSize(f.bold, name, W - 2 * px(26), px(27), px(16));
                y += nameSize;
                cs.setNonStrokingColor(Color.WHITE);
                f.draw(cs, f.bold, nameSize, px(26), H - y, name);
                if (accr.getOrganisation() != null && !accr.getOrganisation().isBlank()) {
                    y += px(21);
                    cs.setNonStrokingColor(PdfArt.SOFT_WHITE);
                    f.draw(cs, f.regular, px(15), px(26), H - y,
                            f.ellipsize(f.regular, px(15), W - 2 * px(26), accr.getOrganisation()));
                }
                y += px(19);
                String access = accr.getActivity() != null
                        ? "Activité : " + accr.getActivity().getTitre() : "Accès : toutes les activités";
                cs.setNonStrokingColor(PdfArt.SOFT_WHITE);
                f.draw(cs, f.regular, px(13), px(26), H - y,
                        f.ellipsize(f.regular, px(13), W - 2 * px(26), access + " · N° " + accr.getNumero()));

                // QR card: as large as the remaining room allows
                float pad = px(12);
                float strip = px(30);
                float gap = pad * 0.8f;
                float cardTop = y + px(16);
                float room = H - cardTop - px(22);
                float qrSize = Math.max(96f, Math.min(170f, room - pad - gap - strip));
                float cardW = qrSize + 2 * pad;
                PdfArt.qrCard(cs, f, qr, (W - cardW) / 2, H - cardTop, qrSize, pad, strip, px(20),
                        "SCAN POUR CONTRÔLE");

                cs.restoreGraphicsState();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Génération du badge PDF impossible", e);
        }
    }

    /** Event name in two-tone display type (max two lines); returns the distance from the top of its baseline. */
    private float drawTitle(PDPageContentStream cs, PdfFonts f, String name) throws IOException {
        String title = (name == null ? "" : name).toUpperCase(Locale.FRENCH);
        float maxW = W - 2 * px(26);
        float min = px(28);
        float size = f.fitSize(f.display, title, maxW, px(58), min);
        List<String> lines = size > min || f.width(f.display, size, title) <= maxW
                ? List.of(title)
                : first(f.wrap(f.display, size, maxW, title), 2);
        float baseline = px(96) + size * 0.80f;
        int word = 0;
        for (int i = 0; i < lines.size(); i++) {
            float cx = px(26);
            for (String w : lines.get(i).split(" ")) {
                if (w.isEmpty()) {
                    continue;
                }
                cs.setNonStrokingColor(word++ == 0 ? Color.WHITE : PdfArt.GOLD);
                f.draw(cs, f.display, size, cx, H - (baseline + i * size * 0.95f), w);
                cx += f.width(f.display, size, w + " ");
            }
        }
        return baseline + (lines.size() - 1) * size * 0.95f + size * 0.12f;
    }

    private static List<String> first(List<String> lines, int max) {
        return lines.size() <= max ? lines : new ArrayList<>(lines.subList(0, max));
    }

    private static String up(String s) {
        return s == null ? "" : s.toUpperCase(Locale.FRENCH);
    }
}
