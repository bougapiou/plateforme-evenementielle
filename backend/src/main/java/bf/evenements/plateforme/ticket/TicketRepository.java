package bf.evenements.plateforme.ticket;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByOrderId(UUID orderId);

    List<Ticket> findByOrderUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByOrderUserId(UUID userId);

    long countByEventIdAndStatut(UUID eventId, TicketStatus statut);

    boolean existsByEventId(UUID eventId);

    boolean existsByNumero(String numero);

    /**
     * Free event-wide tickets (price 0, scope EVENEMENT) already held by a user for an event.
     * Free ACTIVITE tickets (a pass covering some sessions, or one session) are not counted:
     * they are capped per category instead (one per person), so a visitor can combine an
     * entry ticket, a conference pass and individual sessions.
     */
    @Query("select count(t) from Ticket t where t.order.user.id = :userId "
            + "and t.event.id = :eventId and t.eventTicket.prixMontant = 0 "
            + "and t.eventTicket.portee = bf.evenements.plateforme.ticket.TicketScope.EVENEMENT "
            + "and t.statut <> bf.evenements.plateforme.ticket.TicketStatus.ANNULE")
    long countFreeEventWideTicketsForUserAndEvent(@Param("userId") UUID userId,
                                                  @Param("eventId") UUID eventId);
}
