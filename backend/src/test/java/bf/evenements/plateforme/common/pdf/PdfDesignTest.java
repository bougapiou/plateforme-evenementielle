package bf.evenements.plateforme.common.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import bf.evenements.plateforme.accreditation.Accreditation;
import bf.evenements.plateforme.accreditation.AccreditationRole;
import bf.evenements.plateforme.accreditation.BadgePdfService;
import bf.evenements.plateforme.common.config.AppProperties;
import bf.evenements.plateforme.common.storage.FileStorageService;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventCategory;
import bf.evenements.plateforme.invoice.ConfirmationPdfService;
import bf.evenements.plateforme.registration.Participant;
import bf.evenements.plateforme.registration.Registration;
import bf.evenements.plateforme.registration.RegistrationStatus;
import bf.evenements.plateforme.registration.RegistrationType;
import bf.evenements.plateforme.ticket.EventTicket;
import bf.evenements.plateforme.ticket.Ticket;
import bf.evenements.plateforme.ticket.TicketOrder;
import bf.evenements.plateforme.ticket.TicketPdfService;
import bf.evenements.plateforme.ticket.TicketStatus;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The PDFs share one design (cover photo as background); they must stay valid, complete and reasonably light. */
class PdfDesignTest {

    private static FileStorageService storage;
    private static final String PHOTO_URL = "http://localhost:8080/files/events/photo.jpg";

    @BeforeAll
    static void setUp() throws Exception {
        File root = Files.createTempDirectory("pdf-design-test").toFile();
        File dir = new File(root, "events");
        dir.mkdirs();
        // a large photo-like cover: the PDF must not embed it as is
        BufferedImage img = new BufferedImage(3600, 2200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0x0c1c4d), 0, 2200, new Color(0xe2793c)));
        g.fillRect(0, 0, 3600, 2200);
        g.setColor(new Color(0x0a0f24));
        for (int x = 0; x < 3600; x += 30) {
            g.fillOval(x, 1600 + (int) (Math.sin(x * 0.05) * 60), 90, 500);
        }
        g.dispose();
        ImageIO.write(img, "jpg", new File(dir, "photo.jpg"));
        Files.write(new File(dir, "broken.jpg").toPath(), "not an image".getBytes(StandardCharsets.UTF_8));

        storage = new FileStorageService(new AppProperties(null, null, null,
                new AppProperties.Storage("local", new AppProperties.Storage.Local(
                        root.getAbsolutePath(), "http://localhost:8080/files")), null, null));
    }

    private static Event event(String nom, String coverUrl) {
        EventCategory category = new EventCategory();
        category.setNom("Culture");
        Event e = new Event();
        e.setNom(nom);
        e.setLieu("Palais des Sports");
        e.setVille("Ouagadougou");
        e.setDateDebut(Instant.parse("2026-10-25T09:00:00Z"));
        e.setDateFin(Instant.parse("2026-10-25T20:00:00Z"));
        e.setCoverUrl(coverUrl);
        e.setCategory(category);
        return e;
    }

    private static Ticket ticket(Event event, String participant) {
        EventTicket category = new EventTicket();
        category.setNom("Billet d'accès");
        TicketOrder order = new TicketOrder();
        order.setReference("CMD-8F4A92C1");
        Ticket t = new Ticket();
        t.setEvent(event);
        t.setEventTicket(category);
        t.setOrder(order);
        t.setNumero("PNE-2026-000123");
        t.setParticipantNom(participant);
        t.setStatut(TicketStatus.EMISE);
        return t;
    }

    /** Extracted text without any whitespace: letter-spaced labels come out as "F A C T U R E". */
    private static String text(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc).replaceAll("\s+", "");
        }
    }

    private static String[] compact(String... needles) {
        String[] out = new String[needles.length];
        for (int i = 0; i < needles.length; i++) {
            out[i] = needles[i].replaceAll("\s+", "");
        }
        return out;
    }

    @Test
    void ticket_is_a_landscape_page_with_the_photo_as_background_and_stays_light() throws Exception {
        byte[] pdf = new TicketPdfService(storage).render(ticket(event("Faso Culture", PHOTO_URL), "Kanihe Anderson"),
                "abcdefghijklmnop");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            assertThat(doc.getPage(0).getMediaBox().getWidth()).isGreaterThan(doc.getPage(0).getMediaBox().getHeight());
        }
        String t = text(pdf);
        assertThat(t).contains(compact("FASO", "CULTURE", "KANIHE ANDERSON", "PNE-2026-000123", "CMD-8F4A92C1",
                "Billet d'accès", "25 octobre 2026", "SCAN POUR CONTRÔLE"));
        assertThat(pdf.length).as("the 3600x2200 photo is downscaled, not embedded as is").isLessThan(400_000);
    }

    @Test
    void ticket_works_without_a_photo_with_a_broken_photo_and_with_odd_data() throws Exception {
        for (String cover : new String[] {null, "http://localhost:8080/files/events/broken.jpg",
                "http://localhost:8080/files/events/missing.jpg", "https://elsewhere.example/x.jpg"}) {
            byte[] pdf = new TicketPdfService(storage).render(
                    ticket(event("Forum des Jeunes Entrepreneurs du Burkina Faso — édition très très longue 2026", cover),
                            null), "abcdefghijklmnop");
            assertThat(text(pdf)).contains(compact("Visiteur", "PNE-2026-000123"));
        }
        // characters the font has no glyph for (emoji, CJK) are dropped instead of failing the whole PDF
        byte[] pdf = new TicketPdfService(storage).render(
                ticket(event("Zoë Ångström 😀 中文 Festival", null), "Awa Ouédraogo 😀"), "abcdefghijklmnop");
        assertThat(text(pdf)).contains(compact("AWA OUÉDRAOGO"));
    }

    @Test
    void badge_is_a_portrait_a6_page_with_the_person_and_the_event() throws Exception {
        Accreditation accr = new Accreditation();
        accr.setEvent(event("Faso Culture", PHOTO_URL));
        accr.setPersonneNom("Dr. Fatimata Kaboré");
        accr.setOrganisation("Ministère du Commerce");
        accr.setFonction(AccreditationRole.CONFERENCIER);
        accr.setNumero("BDG-000045");
        accr.setQrToken("badge-token-xyz");
        byte[] pdf = new BadgePdfService(storage).render(accr);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            assertThat(doc.getPage(0).getMediaBox().getHeight()).isGreaterThan(doc.getPage(0).getMediaBox().getWidth());
        }
        assertThat(text(pdf)).contains(compact("FASO", "Dr. Fatimata Kaboré", "Ministère du Commerce", "CONFÉRENCIER",
                "BDG-000045"));
    }

    @Test
    void confirmation_lists_every_participant_over_as_many_pages_as_needed() throws Exception {
        Registration reg = new Registration();
        reg.setEvent(event("Faso Culture", PHOTO_URL));
        reg.setReference("INS-000789");
        reg.setType(RegistrationType.PARTICULIER);
        reg.setContactNom("Boureima Sawadogo");
        reg.setNombreParticipants(60);
        reg.setStatut(RegistrationStatus.CONFIRMEE);
        List<Participant> people = new java.util.ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            Participant p = new Participant();
            p.setPrenom("Prénom" + i);
            p.setNom("Nom" + i);
            people.add(p);
        }
        reg.setParticipants(people);

        byte[] pdf = new ConfirmationPdfService(storage).registration(reg);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
        }
        String t = text(pdf);
        assertThat(t).contains(compact("INS-000789", "Prénom1 Nom1", "Prénom60 Nom60", "(suite)"));
        // the photo background is one shared image, not one per page
        assertThat(pdf.length).isLessThan(600_000);
    }

    @Test
    void document_shows_the_amount_and_wraps_long_values() throws Exception {
        byte[] pdf = DocumentPdf.create()
                .event(storage, PHOTO_URL, "Faso Culture", "Faso Culture", "Ouagadougou · Palais des Sports")
                .kicker("Facture").title("N° FAC-2026-00042").subtitle("Émis le 25 septembre 2026")
                .section("Client")
                .row("Détails", "RCCM BF-OUA-01-2024-B12-00456 · IFU 00123456A · Une adresse particulièrement "
                        + "longue qui doit passer à la ligne au lieu de dépasser du cadre de la page")
                .amount("Montant total", "125 000 FCFA")
                .note("Facture acquittée.")
                .build();
        String t = text(pdf);
        assertThat(t).contains(compact("FACTURE", "N° FAC-2026-00042", "125 000 FCFA", "Facture acquittée.", "dépasser du cadre"));
    }
}
