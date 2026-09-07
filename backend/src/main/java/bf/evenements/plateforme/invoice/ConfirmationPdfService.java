package bf.evenements.plateforme.invoice;

import bf.evenements.plateforme.common.pdf.SimplePdf;
import bf.evenements.plateforme.registration.Registration;
import bf.evenements.plateforme.stand.StandReservation;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Renders participation / stand-reservation confirmation PDFs. */
@Service
public class ConfirmationPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("EEEE d MMMM yyyy", Locale.FRENCH).withZone(ZoneId.of("Africa/Ouagadougou"));

    public byte[] registration(Registration r) {
        SimplePdf pdf = SimplePdf.create("CONFIRMATION D'INSCRIPTION")
                .text("Référence : " + r.getReference())
                .spacer()
                .heading("Événement")
                .text(r.getEvent().getNom())
                .text(DATE.format(r.getEvent().getDateDebut())
                        + (r.getEvent().getLieu() != null ? " - " + r.getEvent().getLieu() : ""))
                .spacer()
                .heading("Inscrit")
                .text((r.getStructure() != null ? r.getStructure().getRaisonSociale()
                        : r.getContactNom()) + " (" + r.getType() + ")")
                .text("Participants : " + r.getNombreParticipants())
                .spacer()
                .heading("Statut")
                .text(r.getStatut().name());
        r.getParticipants().forEach(p -> pdf.text("- " + p.getPrenom() + " " + p.getNom()
                + (p.getFonction() != null ? " (" + p.getFonction() + ")" : "")));
        return pdf.build();
    }

    public byte[] standReservation(StandReservation r) {
        return SimplePdf.create("CONFIRMATION DE RESERVATION DE STAND")
                .text("Référence : " + r.getReference() + "  -  N° " + r.getNumeroReservation())
                .spacer()
                .heading("Événement")
                .text(r.getEvent().getNom())
                .text(DATE.format(r.getEvent().getDateDebut()))
                .spacer()
                .heading("Stand")
                .text("Emplacement : " + r.getStand().getNumero() + " (" + r.getStandType().getNom() + ")")
                .text("Montant : " + r.amount().formatted())
                .spacer()
                .heading("Réservataire")
                .text(r.getStructure() != null ? r.getStructure().getRaisonSociale()
                        : r.getUser().getFullName())
                .spacer()
                .heading("Statut")
                .text(r.getStatut().name())
                .build();
    }
}
