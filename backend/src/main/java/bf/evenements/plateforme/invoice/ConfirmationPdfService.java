package bf.evenements.plateforme.invoice;

import bf.evenements.plateforme.common.pdf.DocumentPdf;
import bf.evenements.plateforme.common.storage.FileStorageService;
import bf.evenements.plateforme.registration.Registration;
import bf.evenements.plateforme.stand.StandReservation;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Renders participation / stand-reservation confirmation PDFs, with the event's cover photo. */
@Service
@RequiredArgsConstructor
public class ConfirmationPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("EEEE d MMMM yyyy", Locale.FRENCH).withZone(ZoneId.of("Africa/Ouagadougou"));

    private final FileStorageService fileStorage;

    public byte[] registration(Registration r) {
        DocumentPdf pdf = DocumentPdf.create()
                .cover(fileStorage, r.getEvent().getCoverUrl())
                .kicker("Confirmation d'inscription")
                .title(r.getReference())
                .subtitle(DATE.format(r.getEvent().getDateDebut()))
                .section("Événement")
                .row("Nom", r.getEvent().getNom())
                .row("Date", DATE.format(r.getEvent().getDateDebut()))
                .row("Lieu", r.getEvent().getLieu())
                .section("Inscrit")
                .row("Nom", (r.getStructure() != null ? r.getStructure().getRaisonSociale()
                        : r.getContactNom()) + " (" + r.getType() + ")")
                .row("Participants", String.valueOf(r.getNombreParticipants()))
                .section("Statut")
                .row("Statut", r.getStatut().name());
        if (!r.getParticipants().isEmpty()) {
            pdf.section("Liste des participants");
            r.getParticipants().forEach(p -> pdf.paragraph("• " + p.getPrenom() + " " + p.getNom()
                    + (p.getFonction() != null ? " (" + p.getFonction() + ")" : "")));
        }
        return pdf.build();
    }

    public byte[] standReservation(StandReservation r) {
        return DocumentPdf.create()
                .cover(fileStorage, r.getEvent().getCoverUrl())
                .kicker("Confirmation de réservation de stand")
                .title(r.getReference())
                .subtitle("N° " + r.getNumeroReservation())
                .section("Événement")
                .row("Nom", r.getEvent().getNom())
                .row("Date", DATE.format(r.getEvent().getDateDebut()))
                .section("Stand")
                .row("Emplacement", r.getStand().getNumero() + " (" + r.getStandType().getNom() + ")")
                .amount("Montant", r.amount().formatted())
                .section("Réservataire")
                .row("Nom", r.getStructure() != null ? r.getStructure().getRaisonSociale()
                        : r.getUser().getFullName())
                .section("Statut")
                .row("Statut", r.getStatut().name())
                .build();
    }
}
