package bf.evenements.plateforme.audit;

import bf.evenements.plateforme.common.web.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    /**
     * Persists an audit entry in its own transaction so that a rollback in the
     * caller does not erase the trail.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(@Nullable UUID actorId, @Nullable String actorEmail, String action,
                       @Nullable String entityType, @Nullable String entityId,
                       @Nullable String ipAddress, @Nullable String details) {
        AuditLog log = new AuditLog();
        log.setActorId(actorId);
        log.setActorEmail(actorEmail);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setIpAddress(ipAddress);
        log.setDetails(details);
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(@Nullable String action, @Nullable UUID actorId,
                                                 Pageable pageable) {
        var spec = org.springframework.data.jpa.domain.Specification.allOf(
                AuditLogRepository.hasAction(action),
                AuditLogRepository.hasActor(actorId));
        return PageResponse.of(repository.findAll(spec, pageable), AuditLogResponse::from);
    }

    public record AuditLogResponse(
            UUID id,
            UUID actorId,
            String actorEmail,
            String action,
            String entityType,
            String entityId,
            String ipAddress,
            String details,
            java.time.Instant createdAt) {

        static AuditLogResponse from(AuditLog a) {
            return new AuditLogResponse(a.getId(), a.getActorId(), a.getActorEmail(), a.getAction(),
                    a.getEntityType(), a.getEntityId(), a.getIpAddress(), a.getDetails(), a.getCreatedAt());
        }
    }
}
