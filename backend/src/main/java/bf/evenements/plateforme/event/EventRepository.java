package bf.evenements.plateforme.event;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EventRepository extends JpaRepository<Event, UUID>,
        JpaSpecificationExecutor<Event> {

    Optional<Event> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countByOrganizerId(UUID organizerId);

    long countByCategoryId(UUID categoryId);
}
