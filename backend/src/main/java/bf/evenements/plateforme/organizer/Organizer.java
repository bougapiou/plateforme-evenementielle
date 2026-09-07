package bf.evenements.plateforme.organizer;

import bf.evenements.plateforme.common.domain.BaseEntity;
import bf.evenements.plateforme.structure.Structure;
import bf.evenements.plateforme.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An account authorised to create and manage events. Backed by exactly one user
 * and optionally attached to a {@link Structure} it represents.
 */
@Entity
@Table(name = "organizers")
@Getter
@Setter
@NoArgsConstructor
public class Organizer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structure_id")
    private Structure structure;

    @Column(name = "nom_affichage", nullable = false, length = 200)
    private String nomAffichage;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "contact_email", length = 180)
    private String contactEmail;

    @Column(name = "contact_telephone", length = 30)
    private String contactTelephone;

    @Column(name = "site_web", length = 255)
    private String siteWeb;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizerStatus statut = OrganizerStatus.EN_ATTENTE;

    @Column(name = "approuve_par")
    private UUID approuvePar;

    @Column(name = "approuve_le")
    private Instant approuveLe;

    public boolean isActive() {
        return statut == OrganizerStatus.ACTIF;
    }
}
