package bf.evenements.plateforme.ticket;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventTicketRepository extends JpaRepository<EventTicket, UUID> {

    List<EventTicket> findByEventIdOrderByOrdreAscPrixMontantAsc(UUID eventId);

    long countByEventId(UUID eventId);

    /** Locks the row so concurrent purchases cannot oversell the quota. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EventTicket t where t.id = :id")
    Optional<EventTicket> findByIdForUpdate(@Param("id") UUID id);
}
