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

    /** How the public accesses this activity (schedule-only, free ticket, paid ticket). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivityAccess acces = ActivityAccess.SANS_BILLET;

    /**
     * Auto-managed free ticket category backing this activity while {@code acces}
     * is {@code GRATUIT}. Soft reference (no FK), kept when GRATUIT is toggled off
     * so its history and quota survive.
     */
    @Column(name = "free_ticket_id")
    private java.util.UUID freeTicketId;

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

    /** Optional illustration for the activity (URL to an uploaded image). */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** Optional link to a detailed {@link Speaker} profile. */
    @Column(name = "speaker_id")
    private java.util.UUID speakerId;

    private Integer capacite;

    @Column(nullable = false)
    private int ordre = 0;
}
