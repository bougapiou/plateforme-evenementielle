package bf.evenements.plateforme.event;

import bf.evenements.plateforme.common.domain.BaseEntity;
import bf.evenements.plateforme.organizer.Organizer;
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

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
public class Event extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private Organizer organizer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private EventCategory category;

    @Column(nullable = false, length = 200)
    private String nom;

    @Column(length = 40)
    private String sigle;

    @Column(nullable = false, length = 220)
    private String slug;

    @Column(name = "description_courte", length = 500)
    private String descriptionCourte;

    @Column(name = "description_detaillee", columnDefinition = "text")
    private String descriptionDetaillee;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(name = "date_debut", nullable = false)
    private Instant dateDebut;

    @Column(name = "date_fin", nullable = false)
    private Instant dateFin;

    @Column(length = 200)
    private String lieu;

    @Column(length = 255)
    private String adresse;

    @Column(length = 120)
    private String ville;

    @Column(nullable = false, length = 120)
    private String pays = "Burkina Faso";

    private Double latitude;
    private Double longitude;

    @Column(name = "capacite_max")
    private Integer capaciteMax;

    @Column(name = "contact_email", length = 180)
    private String contactEmail;

    @Column(name = "contact_telephone", length = 30)
    private String contactTelephone;

    @Column(name = "site_web", length = 255)
    private String siteWeb;

    @Column(name = "conditions_participation", columnDefinition = "text")
    private String conditionsParticipation;

    /** Whether this event is split into several activities that can be programmed. */
    @Column(name = "has_activities", nullable = false)
    private boolean hasActivities = false;

    @Column(name = "stands_actifs", nullable = false)
    private boolean standsActifs = false;

    /** When true, an individual (no structure) may also reserve a stand. */
    @Column(name = "stands_particuliers", nullable = false)
    private boolean standsParticuliers = false;

    /** When true, registrations stay pending until the organiser confirms them. */
    @Column(name = "validation_inscription", nullable = false)
    private boolean validationInscription = false;

    /** When true, the entrance staff also scans exits (attendance flow count). */
    @Column(name = "controle_sortie", nullable = false)
    private boolean controleSortie = false;

    @Column(name = "inscription_debut")
    private Instant inscriptionDebut;

    @Column(name = "inscription_fin")
    private Instant inscriptionFin;

    @Column(name = "reservation_debut")
    private Instant reservationDebut;

    @Column(name = "reservation_fin")
    private Instant reservationFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventStatus statut = EventStatus.BROUILLON;

    @Column(name = "motif_refus", length = 1000)
    private String motifRefus;

    @Column(name = "soumis_le")
    private Instant soumisLe;

    @Column(name = "valide_le")
    private Instant valideLe;

    @Column(name = "valide_par")
    private UUID validePar;

    @Column(name = "publie_le")
    private Instant publieLe;

    public boolean isOwnedBy(UUID userId) {
        return organizer != null && organizer.getUser() != null
                && organizer.getUser().getId().equals(userId);
    }

    /** True when now is within the configured registration window (or no window set). */
    public boolean withinRegistrationWindow(Instant now) {
        if (inscriptionDebut != null && now.isBefore(inscriptionDebut)) {
            return false;
        }
        return inscriptionFin == null || !now.isAfter(inscriptionFin);
    }
}
