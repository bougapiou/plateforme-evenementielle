package bf.evenements.plateforme.sensor;

import bf.evenements.plateforme.checkin.CheckinDirection;
import bf.evenements.plateforme.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One report from a sensor: {@code nombre} people crossed the beam in a given direction. */
@Entity
@Table(name = "passages_capteur")
@Getter
@Setter
@NoArgsConstructor
public class PassageCapteur extends BaseEntity {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "capteur_id", nullable = false)
    private UUID capteurId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CheckinDirection sens;

    @Column(nullable = false)
    private int nombre = 1;

    @Column(name = "passe_le", nullable = false)
    private Instant passeLe = Instant.now();
}
