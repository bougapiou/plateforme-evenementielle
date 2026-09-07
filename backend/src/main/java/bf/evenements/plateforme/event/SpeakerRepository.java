package bf.evenements.plateforme.event;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpeakerRepository extends JpaRepository<Speaker, UUID> {

    List<Speaker> findByEventIdOrderByOrdreAscNomAsc(UUID eventId);
}
