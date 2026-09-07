package bf.evenements.plateforme.organizer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OrganizerRepository extends JpaRepository<Organizer, UUID>,
        JpaSpecificationExecutor<Organizer> {

    Optional<Organizer> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
