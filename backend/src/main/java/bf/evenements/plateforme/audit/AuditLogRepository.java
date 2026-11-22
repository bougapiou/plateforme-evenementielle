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

    /** Free text on author, details, object type / id, IP. */
    static Specification<AuditLog> matches(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String like = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(cb.coalesce(root.get("actorEmail"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("details"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("entityType"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("entityId"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("ipAddress"), "")), like));
    }

    static Specification<AuditLog> between(java.time.Instant from, java.time.Instant to) {
        return (root, query, cb) -> cb.and(
                from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                to == null ? cb.conjunction() : cb.lessThan(root.get("createdAt"), to));
    }
}
