package bf.evenements.plateforme.event;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventActivityRepository extends JpaRepository<EventActivity, UUID> {

    List<EventActivity> findByEventIdOrderByDateDebutAscOrdreAsc(UUID eventId);

    long countByEventId(UUID eventId);
}
