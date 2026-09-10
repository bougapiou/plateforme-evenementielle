package bf.evenements.plateforme.ticket;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByOrderId(UUID orderId);

    List<Ticket> findByOrderUserIdOrderByCreatedAtDesc(UUID userId);

    long countByEventIdAndStatut(UUID eventId, TicketStatus statut);

    boolean existsByNumero(String numero);

    /** Free tickets (price 0) already held by a user for a given event. */
    @Query("select count(t) from Ticket t where t.order.user.id = :userId "
            + "and t.event.id = :eventId and t.eventTicket.prixMontant = 0 "
            + "and t.statut <> bf.evenements.plateforme.ticket.TicketStatus.ANNULE")
    long countFreeTicketsForUserAndEvent(@Param("userId") UUID userId,
                                         @Param("eventId") UUID eventId);
}
