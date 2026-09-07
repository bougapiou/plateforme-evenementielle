package bf.evenements.plateforme.checkin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventStaffRepository extends JpaRepository<EventStaff, UUID> {

    List<EventStaff> findByEventId(UUID eventId);

    Optional<EventStaff> findByEventIdAndUserId(UUID eventId, UUID userId);

    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

    long countByUserId(UUID userId);
}
