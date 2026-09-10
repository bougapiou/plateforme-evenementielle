package bf.evenements.plateforme.checkin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinRepository extends JpaRepository<Checkin, UUID> {

    Optional<Checkin> findFirstByTicketIdAndResultatOrderByScannedAtAsc(UUID ticketId,
                                                                       CheckinResult resultat);

    Optional<Checkin> findFirstByTicketIdAndActivityIdIsNullAndResultatOrderByScannedAtAsc(
            UUID ticketId, CheckinResult resultat);

    Optional<Checkin> findFirstByTicketIdAndActivityIdAndResultatOrderByScannedAtAsc(
            UUID ticketId, UUID activityId, CheckinResult resultat);

    Page<Checkin> findByEventIdOrderByScannedAtDesc(UUID eventId, Pageable pageable);

    long countByEventIdAndResultat(UUID eventId, CheckinResult resultat);

    List<Checkin> findByEventId(UUID eventId);
}
