package bf.evenements.plateforme.stand;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StandTypeRepository extends JpaRepository<StandType, UUID> {

    List<StandType> findByEventIdOrderByOrdreAscPrixMontantAsc(UUID eventId);

    long countByEventId(UUID eventId);
}
