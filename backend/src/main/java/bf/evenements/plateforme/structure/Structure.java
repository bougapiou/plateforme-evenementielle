package bf.evenements.plateforme.structure;

import bf.evenements.plateforme.common.domain.BaseEntity;
import bf.evenements.plateforme.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Professional account for a company, institution or association. It can
 * register for events and reserve stands, and its members act on its behalf.
 */
@Entity
@Table(name = "structures")
@Getter
@Setter
@NoArgsConstructor
public class Structure extends BaseEntity {

    @Column(name = "raison_sociale", nullable = false, length = 200)
    private String raisonSociale;

    @Column(length = 40)
    private String sigle;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_structure", nullable = false, length = 30)
    private StructureType typeStructure = StructureType.ENTREPRISE;

    @Column(name = "secteur_activite", length = 120)
    private String secteurActivite;

    @Column(length = 60)
    private String rccm;

    @Column(length = 60)
    private String ifu;

    @Column(length = 255)
    private String adresse;

    @Column(length = 120)
    private String ville;

    @Column(nullable = false, length = 120)
    private String pays = "Burkina Faso";

    @Column(length = 30)
    private String telephone;

    @Column(length = 180)
    private String email;

    @Column(name = "site_web", length = 255)
    private String siteWeb;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StructureStatus statut = StructureStatus.EN_ATTENTE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(name = "created_by")
    private UUID createdBy;

    @OneToMany(mappedBy = "structure", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<StructureMember> members = new LinkedHashSet<>();

    public void addMember(StructureMember member) {
        member.setStructure(this);
        members.add(member);
    }
}
