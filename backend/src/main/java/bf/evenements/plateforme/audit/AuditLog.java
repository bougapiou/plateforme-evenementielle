package bf.evenements.plateforme.audit;

import bf.evenements.plateforme.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Immutable record of a security- or business-significant action.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends BaseEntity {

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_email", length = 180)
    private String actorEmail;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "entity_type", length = 80)
    private String entityType;

    @Column(name = "entity_id", length = 80)
    private String entityId;

    @Column(name = "ip_address", length = 60)
    private String ipAddress;

    @Column(columnDefinition = "text")
    private String details;
}
