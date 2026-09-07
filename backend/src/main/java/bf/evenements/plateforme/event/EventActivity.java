package bf.evenements.plateforme.event;

import bf.evenements.plateforme.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A programmed item of an event. When {@code event.hasActivities} is true these
 * make up the schedule and ticket categories can be scoped to them.
 */
@Entity
@Table(name = "event_activities")
@Getter
@Setter
@NoArgsConstructor
public class EventActivity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_activite", length = 30)
    private ActivityType typeActivite;

    @Column(name = "date_debut", nullable = false)
    private Instant dateDebut;

    @Column(name = "date_fin")
    private Instant dateFin;

    @Column(length = 120)
    private String salle;

    @Column(length = 200)
    private String lieu;

    @Column(length = 255)
    private String intervenant;

    @Column(length = 255)
    private String moderateur;

    /** Optional link to a detailed {@link Speaker} profile. */
    @Column(name = "speaker_id")
    private java.util.UUID speakerId;

    private Integer capacite;

    @Column(nullable = false)
    private int ordre = 0;
}
