package bf.evenements.plateforme.event;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerRepository extends JpaRepository<Partner, UUID> {

    List<Partner> findByEventIdOrderByOrdreAscNomAsc(UUID eventId);
}
