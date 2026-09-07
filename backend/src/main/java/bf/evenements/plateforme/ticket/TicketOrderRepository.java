package bf.evenements.plateforme.ticket;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketOrderRepository extends JpaRepository<TicketOrder, UUID> {

    Page<TicketOrder> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<TicketOrder> findByEventIdOrderByCreatedAtDesc(UUID eventId, Pageable pageable);

    boolean existsByReference(String reference);

    List<TicketOrder> findByStatutAndExpireLeBefore(TicketOrderStatus statut, Instant cutoff);

    @org.springframework.data.jpa.repository.Query("""
            select coalesce(sum(l.quantite), 0)
            from TicketOrderLine l
            where l.eventTicket.id = :ticketId
              and l.order.user.id = :userId
              and l.order.statut in (bf.evenements.plateforme.ticket.TicketOrderStatus.EN_ATTENTE,
                                     bf.evenements.plateforme.ticket.TicketOrderStatus.PAYEE)
            """)
    int quantityAlreadyOrdered(@org.springframework.data.repository.query.Param("ticketId") UUID ticketId,
                               @org.springframework.data.repository.query.Param("userId") UUID userId);
}
