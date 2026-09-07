package bf.evenements.plateforme.audit;

import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>,
        JpaSpecificationExecutor<AuditLog> {

    static Specification<AuditLog> hasAction(String action) {
        return action == null || action.isBlank()
                ? null
                : (root, query, cb) -> cb.equal(root.get("action"), action);
    }

    static Specification<AuditLog> hasActor(UUID actorId) {
        return actorId == null ? null : (root, query, cb) -> cb.equal(root.get("actorId"), actorId);
    }
}
