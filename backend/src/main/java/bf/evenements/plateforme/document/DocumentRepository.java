package bf.evenements.plateforme.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByOwnerTypeAndOwnerIdOrderByCreatedAtAsc(String ownerType, UUID ownerId);
}
