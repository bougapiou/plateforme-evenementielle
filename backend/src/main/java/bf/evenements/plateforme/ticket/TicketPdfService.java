package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.common.pdf.PdfArt;
import bf.evenements.plateforme.common.pdf.PdfArt.Icon;
import bf.evenements.plateforme.common.pdf.PdfBackdrop;
import bf.evenements.plateforme.common.pdf.PdfFonts;
import bf.evenements.plateforme.common.storage.FileStorageService;
import bf.evenements.plateforme.common.web.QrImages;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
 * Renders an e-ticket as a landscape one-page PDF: the event's cover photo is the FULL background
 * (darkened on the left for the info panel), with the PNE emblem, the event name in big type, a glass
 * panel with the ticket's details and a large QR code on the right. Same layout as the design mockup.
 */
@Service
@RequiredArgsConstructor
public class TicketPdfService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Ouagadougou");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
            .withZone(ZONE);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRENCH)
            .withZone(ZONE);

    // Same ratio as the design mockup (1120 x 504 px), in points.
    private static final float W = 842f;
    private static final float H = 379f;
    private static final float S = W / 1120f;

    private static final float QR_SIZE = 176f;

    private final FileStorageService fileStorage;

    /** Design-mockup pixels to points. */
    private static float px(float v) {
        return v * S;
    }

    /** y (PDF, up) of a point given its distance from the TOP of the page in points. */
    private static float top(float fromTop) {
        return H - fromTop;
    }

    public byte[] render(Ticket ticket, String qrToken) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(W, H));
            doc.addPage(page);
            PdfFonts f = PdfFonts.load(doc);

            var event = ticket.getEvent();
            PDImageXObject background = PdfBackdrop.create(doc, fileStorage, event.getCoverUrl(), event.getNom(),
                    1800, Math.round(1800 * H / W), PdfBackdrop.Veil.LANDSCAPE);
            PDImageXObject qr = PDImageXObject.createFromByteArray(doc, QrImages.png(qrToken, 700), "qr");

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // The ticket itself is a rounded card on a white page.
                cs.saveGraphicsState();
                PdfArt.roundedRect(cs, 0, 0, W, H, px(26));
                cs.clip();

                cs.drawImage(background, 0, 0, W, H);
                PdfArt.frame(cs, px(14), px(14), W - 2 * px(14), H - 2 * px(14), px(20), 0.8f, 0.55f);
                PdfArt.swoosh(cs, 0, 0, px(300), px(120));
                PdfArt.patternCorner(cs, W, px(96));

                drawBrand(cs, f);
                drawTitle(cs, f, event.getNom(), subtitle(event));
                drawPanel(cs, f, ticket, event);
                drawQr(cs, f, qr);

                cs.restoreGraphicsState();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Génération du billet PDF impossible", e);
        }
    }

    // ---------- pieces ----------

    private void drawBrand(PDPageContentStream cs, PdfFonts f) throws IOException {
        PdfArt.emblem(cs, px(90), top(px(76)), px(37.7f));
        cs.setNonStrokingColor(Color.WHITE);
        f.drawSpaced(cs, f.bold, px(50), px(148), top(px(71)), "PNE", px(1));
        f.draw(cs, f.regular, px(13.5f), px(148), top(px(96.5f)), "Plateforme Nationale");
        f.draw(cs, f.regular, px(13.5f), px(148), top(px(113)), "de Gestion des Événements");
    }

    /** "MUSIQUE · OUAGADOUGOU": the event's category and city, in capitals. */
    private static String subtitle(bf.evenements.plateforme.event.Event event) {
        List<String> parts = new ArrayList<>();
        if (event.getCategory() != null && event.getCategory().getNom() != null) {
            parts.add(event.getCategory().getNom());
        }
        if (event.getVille() != null && !event.getVille().isBlank()) {
            parts.add(event.getVille());
        }
        return String.join(" · ", parts).toUpperCase(Locale.FRENCH);
    }

    /** Event name, first word white and the rest gold, shrinking (then wrapping) to fit; category line under it. */
    private void drawTitle(PDPageContentStream cs, PdfFonts f, String name, String subtitle) throws IOException {
        float x = px(318);
        float maxW = W - px(46) - x;
        String title = (name == null ? "" : name).toUpperCase(Locale.FRENCH);
        float max = px(104);
        float min = 34f;
        float size = f.fitSize(f.display, title, maxW, max, min);

        List<String> lines = size > min || f.width(f.display, size, title) <= maxW
                ? List.of(title)
                : limit(f.wrap(f.display, size, maxW, title), 2);
        float baseline = px(24) + size * 0.80f;
        int wordIndex = 0;
        for (int i = 0; i < lines.size(); i++) {
            float cx = x;
            for (String word : lines.get(i).split(" ")) {
                if (word.isEmpty()) {
                    continue;
                }
                cs.setNonStrokingColor(wordIndex++ == 0 ? Color.WHITE : PdfArt.GOLD);
                f.draw(cs, f.display, size, cx, top(baseline + i * size * 0.95f), word);
                cx += f.width(f.display, size, word + " ");
            }
        }

        if (!subtitle.isBlank()) {
            float lastBaseline = baseline + (lines.size() - 1) * size * 0.95f;
            float subMax = px(1120 - 46 - 214 - 24) - x;
            String text = f.ellipsize(f.regular, px(20), subMax, subtitle);
            cs.setNonStrokingColor(Color.WHITE);
            f.drawSpaced(cs, f.regular, px(20), x, top(lastBaseline + Math.max(16, size * 0.28f)), text, px(3));
        }
    }

    private static List<String> limit(List<String> lines, int max) {
        return lines.size() <= max ? lines : new ArrayList<>(lines.subList(0, max));
    }

    private void drawPanel(PDPageContentStream cs, PdfFonts f, Ticket ticket,
                           bf.evenements.plateforme.event.Event event) throws IOException {
        float panelX = px(62);
        float panelW = px(596);
        PdfArt.glassPanel(cs, panelX, top(px(150) + px(314)), panelW, px(314), px(26), 0.58f);

        float x0 = panelX + px(26);
        float col2 = x0 + px(268 + 30);
        float full = panelX + panelW - px(26);
        float[] rowTop = {px(168), px(225.3f), px(282.6f), px(339.9f), px(397.2f)};

        String participant = ticket.getParticipantNom() == null || ticket.getParticipantNom().isBlank()
                ? "Visiteur" : ticket.getParticipantNom().toUpperCase(Locale.FRENCH);
        String lieu = join(" – ", event.getVille(), event.getLieu());

        row(cs, f, Icon.PERSON, "Nom du participant", participant, x0, rowTop[0], full);
        row(cs, f, Icon.TICKET, "Type de billet", ticket.getEventTicket().getNom(), x0, rowTop[1], full);
        row(cs, f, Icon.CALENDAR, "Date", DATE.format(event.getDateDebut()), x0, rowTop[2], col2 - px(30));
        row(cs, f, Icon.CLOCK, "Heure", TIME.format(event.getDateDebut()), col2, rowTop[2], full);
        row(cs, f, Icon.PIN, "Lieu", lieu.isEmpty() ? "—" : lieu, x0, rowTop[3], full);
        row(cs, f, Icon.NUMBER, "N° de billet", ticket.getNumero(), x0, rowTop[4], col2 - px(30));
        row(cs, f, Icon.SHIELD, "Référence", ticket.getOrder().getReference(), col2, rowTop[4], full);

        // thin separators between the two-column rows
        cs.saveGraphicsState();
        PdfArt.alpha(cs, 0.45f);
        cs.setStrokingColor(PdfArt.GOLD);
        cs.setLineWidth(0.6f);
        for (int r : new int[] {2, 4}) {
            cs.moveTo(col2 - px(15), top(rowTop[r] + px(2)));
            cs.lineTo(col2 - px(15), top(rowTop[r] + px(46)));
            cs.stroke();
        }
        cs.restoreGraphicsState();
    }

    /** One icon + gold label + white bold value. {@code fromTop} = top of the row; text fits {@code rightEdge}. */
    private void row(PDPageContentStream cs, PdfFonts f, Icon icon, String label, String value, float x,
                     float fromTop, float rightEdge) throws IOException {
        float iconSize = px(34);
        float rowH = px(48.7f);
        PdfArt.icon(cs, icon, x, top(fromTop + rowH / 2 + iconSize / 2), iconSize, PdfArt.GOLD, PdfArt.PANEL);

        float textX = x + px(34 + 16);
        cs.setNonStrokingColor(PdfArt.GOLD);
        f.draw(cs, f.regular, px(16), textX, top(fromTop + px(14.2f)), label);

        String v = value == null || value.isBlank() ? "—" : value;
        float size = f.fitSize(f.bold, v, rightEdge - textX, px(27), px(15));
        cs.setNonStrokingColor(Color.WHITE);
        f.draw(cs, f.bold, size, textX, top(fromTop + px(40.8f)), f.ellipsize(f.bold, size, rightEdge - textX, v));
    }

    private void drawQr(PDPageContentStream cs, PdfFonts f, PDImageXObject qr) throws IOException {
        float pad = 12f;
        float cardW = QR_SIZE + 2 * pad;
        float cardX = W - px(46) - cardW;
        float cardTop = top(100);
        PdfArt.qrCard(cs, f, qr, cardX, cardTop, QR_SIZE, pad, 30f, px(22), "SCAN POUR CONTRÔLE");

        // Hint under the card: phone icon + two italic lines.
        String l1 = "Présentez ce QR Code à l'entrée";
        String l2 = "pour accéder à l'événement.";
        float size = 9.6f;
        float textW = Math.max(f.width(f.italic, size, l1), f.width(f.italic, size, l2));
        float icon = 20f;
        float gx = cardX + (cardW - (icon + 6 + textW)) / 2;
        PdfArt.icon(cs, Icon.PHONE, gx, top(362), icon, Color.WHITE, Color.WHITE);
        cs.setNonStrokingColor(Color.WHITE);
        f.draw(cs, f.italic, size, gx + icon + 6, top(351), l1);
        f.draw(cs, f.italic, size, gx + icon + 6, top(363), l2);
    }

    private static String join(String sep, String... parts) {
        List<String> ok = new ArrayList<>();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                ok.add(p);
            }
        }
        return String.join(sep, ok);
    }
}
