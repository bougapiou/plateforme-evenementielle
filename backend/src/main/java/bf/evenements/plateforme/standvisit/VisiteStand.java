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
 * A visitor signalling their passage at a stand, from that stand's own public page (one link/QR
 * per stand). Identity is optional: a name and a phone number, kept only if the visitor chose to
 * give them. Independent of ticket check-in and the laser sensors — this counts foot traffic per
 * stand, not entry to the event.
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
