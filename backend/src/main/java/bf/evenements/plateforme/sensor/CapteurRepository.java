package bf.evenements.plateforme.sensor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapteurRepository extends JpaRepository<Capteur, UUID> {

    Optional<Capteur> findByCleHash(String cleHash);

    List<Capteur> findByEventIdOrderByCreatedAtAsc(UUID eventId);

    Optional<Capteur> findByIdAndEventId(UUID id, UUID eventId);
}
