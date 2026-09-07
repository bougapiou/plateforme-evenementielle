package bf.evenements.plateforme.ticket;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByOrderId(UUID orderId);

    List<Ticket> findByOrderUserIdOrderByCreatedAtDesc(UUID userId);

    long countByEventIdAndStatut(UUID eventId, TicketStatus statut);

    boolean existsByNumero(String numero);
}
