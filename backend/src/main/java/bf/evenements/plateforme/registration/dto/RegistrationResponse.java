package bf.evenements.plateforme.registration.dto;

import bf.evenements.plateforme.registration.Participant;
import bf.evenements.plateforme.registration.Registration;
import bf.evenements.plateforme.registration.RegistrationStatus;
import bf.evenements.plateforme.registration.RegistrationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RegistrationResponse(
        UUID id,
        String reference,
        UUID eventId,
        String eventNom,
        RegistrationType type,
        RegistrationStatus statut,
        UUID structureId,
        String structureNom,
        String contactNom,
        String contactEmail,
        String contactTelephone,
        int nombreParticipants,
        String informations,
        String motifRefus,
        UUID ticketOrderId,
        String ticketOrderReference,
        String ticketOrderStatut,
        List<ParticipantView> participants,
        Instant confirmeeLe,
        Instant createdAt) {

    public record ParticipantView(UUID id, String nom, String prenom, String email,
                                  String telephone, String fonction) {
        static ParticipantView from(Participant p) {
            return new ParticipantView(p.getId(), p.getNom(), p.getPrenom(), p.getEmail(),
                    p.getTelephone(), p.getFonction());
        }
    }

    public static RegistrationResponse from(Registration r) {
        return new RegistrationResponse(
                r.getId(), r.getReference(), r.getEvent().getId(), r.getEvent().getNom(),
                r.getType(), r.getStatut(),
                r.getStructure() != null ? r.getStructure().getId() : null,
                r.getStructure() != null ? r.getStructure().getRaisonSociale() : null,
                r.getContactNom(), r.getContactEmail(), r.getContactTelephone(),
                r.getNombreParticipants(), r.getInformations(), r.getMotifRefus(),
                r.getTicketOrder() != null ? r.getTicketOrder().getId() : null,
                r.getTicketOrder() != null ? r.getTicketOrder().getReference() : null,
                r.getTicketOrder() != null ? r.getTicketOrder().getStatut().name() : null,
                r.getParticipants().stream().map(ParticipantView::from).toList(),
                r.getConfirmeeLe(), r.getCreatedAt());
    }
}
