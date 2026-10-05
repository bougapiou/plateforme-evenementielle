package bf.evenements.plateforme.standvisit;

import bf.evenements.plateforme.common.domain.BaseEntity;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.stand.Stand;
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
 * Un visiteur qui signale son passage à un stand, depuis la page publique propre à ce stand (un
 * lien/QR par stand). L'identité est facultative : un nom et un numéro de téléphone, conservés
 * seulement si le visiteur a choisi de les donner. Indépendant du contrôle d'accès billetterie et
 * des capteurs laser — ceci compte la fréquentation par stand, pas l'entrée à l'événement.
 */
@Entity
@Table(name = "visites_stand")
@Getter
@Setter
@NoArgsConstructor
public class VisiteStand extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stand_id", nullable = false)
    private Stand stand;

    @Column(length = 120)
    private String nom;

    @Column(length = 120)
    private String prenom;

    @Column(length = 30)
    private String telephone;
}
