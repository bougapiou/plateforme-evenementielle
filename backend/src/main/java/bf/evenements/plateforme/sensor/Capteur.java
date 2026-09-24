package bf.evenements.plateforme.sensor;

import bf.evenements.plateforme.common.domain.BaseEntity;
import bf.evenements.plateforme.event.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A counting device (laser beam + Arduino/Raspberry) bound to one event by a secret key. */
@Entity
@Table(name = "capteurs")
@Getter
@Setter
@NoArgsConstructor
public class Capteur extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 120)
    private String nom;

    /** SHA-256 of the secret key; the key itself is shown once, at creation. */
    @Column(name = "cle_hash", nullable = false, unique = true, length = 64)
    private String cleHash;

    /** First characters of the key, to recognise a device in the UI. */
    @Column(name = "cle_prefixe", nullable = false, length = 12)
    private String clePrefixe;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "derniere_activite")
    private Instant derniereActivite;
}
