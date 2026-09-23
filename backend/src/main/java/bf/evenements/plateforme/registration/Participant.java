package bf.evenements.plateforme.registration;

import bf.evenements.plateforme.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "participants")
@Getter
@Setter
@NoArgsConstructor
public class Participant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    private Registration registration;

    /**
     * Nullable since V22: a free ticket category can ask for the first name
     * only (see {@code IdentiteRequise.PRENOM_SEUL}). {@link
     * bf.evenements.plateforme.registration.RegistrationService} still
     * requires at least one of {@code nom}/{@code prenom} to be filled.
     */
    @Column(length = 120)
    private String nom;

    @Column(length = 120)
    private String prenom;

    @Column(length = 180)
    private String email;

    @Column(length = 30)
    private String telephone;

    @Column(length = 120)
    private String fonction;
}
