package bf.evenements.plateforme.structure;

import bf.evenements.plateforme.common.domain.BaseEntity;
import bf.evenements.plateforme.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "structure_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_structure_member",
                columnNames = {"structure_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class StructureMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "structure_id", nullable = false)
    private Structure structure;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_interne", nullable = false, length = 20)
    private StructureMemberRole roleInterne = StructureMemberRole.MEMBRE;

    @Column(length = 120)
    private String fonction;

    @Column(nullable = false)
    private boolean active = true;

    public StructureMember(Structure structure, User user, StructureMemberRole roleInterne,
                           String fonction) {
        this.structure = structure;
        this.user = user;
        this.roleInterne = roleInterne;
        this.fonction = fonction;
    }

    public boolean canManage() {
        return roleInterne == StructureMemberRole.PROPRIETAIRE
                || roleInterne == StructureMemberRole.ADMINISTRATEUR;
    }
}
