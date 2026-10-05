package bf.evenements.plateforme.pointage;

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

/**
 * Un visiteur qui signale son passage à un {@link PointageStand}. Identité facultative : un nom
 * et un numéro de téléphone, conservés seulement si le visiteur a choisi de les donner.
 */
@Entity
@Table(name = "pointage_passages")
@Getter
@Setter
@NoArgsConstructor
public class PointagePassage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stand_id", nullable = false)
    private PointageStand stand;

    @Column(length = 120)
    private String nom;

    @Column(length = 120)
    private String prenom;

    @Column(length = 30)
    private String telephone;
}
