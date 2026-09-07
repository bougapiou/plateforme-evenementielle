package bf.evenements.plateforme.rbac;

import bf.evenements.plateforme.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A fine-grained capability (e.g. {@code EVENT_VALIDATE}) that can be granted to
 * roles. Permission names are the authorities checked by {@code @PreAuthorize}.
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
public class Permission extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 255)
    private String description;

    public Permission(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
