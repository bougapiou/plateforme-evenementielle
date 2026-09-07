package bf.evenements.plateforme.event;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventCategoryRepository extends JpaRepository<EventCategory, UUID> {

    Optional<EventCategory> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<EventCategory> findAllByOrderByOrdreAscNomAsc();

    List<EventCategory> findByActifTrueOrderByOrdreAscNomAsc();
}
