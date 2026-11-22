package bf.evenements.plateforme.checkin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventStaffRepository extends JpaRepository<EventStaff, UUID> {

    List<EventStaff> findByEventId(UUID eventId);

    List<EventStaff> findByUserId(UUID userId);

    Optional<EventStaff> findByEventIdAndUserId(UUID eventId, UUID userId);

    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

    boolean existsByEventIdAndUserIdAndFonction(UUID eventId, UUID userId, StaffFonction fonction);

    List<EventStaff> findByUserIdAndFonction(UUID userId, StaffFonction fonction);

    long countByUserId(UUID userId);
}
