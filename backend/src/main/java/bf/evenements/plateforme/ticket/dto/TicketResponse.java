package bf.evenements.plateforme.ticket.dto;

import bf.evenements.plateforme.ticket.Ticket;
import bf.evenements.plateforme.ticket.TicketStatus;
import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        String numero,
        UUID eventId,
        String eventNom,
        Instant eventDateDebut,
        String lieu,
        String categorieNom,
        String participantNom,
        TicketStatus statut,
        String orderReference) {

    public static TicketResponse from(Ticket t) {
        return new TicketResponse(
                t.getId(), t.getNumero(), t.getEvent().getId(), t.getEvent().getNom(),
                t.getEvent().getDateDebut(), t.getEvent().getLieu(),
                t.getEventTicket().getNom(), t.getParticipantNom(), t.getStatut(),
                t.getOrder().getReference());
    }
}
