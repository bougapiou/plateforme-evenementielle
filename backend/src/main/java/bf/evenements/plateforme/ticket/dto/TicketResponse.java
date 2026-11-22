package bf.evenements.plateforme.ticket.dto;

import bf.evenements.plateforme.event.EventActivity;
import bf.evenements.plateforme.ticket.Ticket;
import bf.evenements.plateforme.ticket.TicketScope;
import bf.evenements.plateforme.ticket.TicketStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        String numero,
        UUID eventId,
        String eventNom,
        Instant eventDateDebut,
        String lieu,
        String eventCoverUrl,
        String categorieNom,
        String participantNom,
        TicketStatus statut,
        String orderReference,
        String qrImageUrl,
        String pdfUrl,
        /** EVENEMENT (tout l'événement) ou ACTIVITE (seulement {@link #sessions}). */
        TicketScope portee,
        /** Description de la catégorie de billet (ce que le billet donne). */
        String categorieDescription,
        /** Sessions couvertes par un billet ACTIVITE (pass ou session unique), par ordre chronologique. */
        List<Session> sessions) {

    /** Une activité du programme couverte par le billet. */
    public record Session(UUID id, String titre, Instant dateDebut, Instant dateFin, String salle) {
        static Session from(EventActivity a) {
            return new Session(a.getId(), a.getTitre(), a.getDateDebut(), a.getDateFin(),
                    a.getSalle() != null ? a.getSalle() : a.getLieu());
        }
    }

    public static TicketResponse from(Ticket t) {
        var category = t.getEventTicket();
        List<Session> sessions = category.getPortee() == TicketScope.ACTIVITE
                ? category.getActivities().stream()
                        .sorted(Comparator.comparing(EventActivity::getDateDebut,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(Session::from)
                        .toList()
                : List.of();
        return new TicketResponse(
                t.getId(), t.getNumero(), t.getEvent().getId(), t.getEvent().getNom(),
                t.getEvent().getDateDebut(), t.getEvent().getLieu(), t.getEvent().getCoverUrl(),
                category.getNom(), t.getParticipantNom(), t.getStatut(),
                t.getOrder().getReference(),
                "/api/tickets/" + t.getId() + "/qr.png",
                "/api/tickets/" + t.getId() + "/pdf",
                category.getPortee(), category.getDescription(), sessions);
    }
}
