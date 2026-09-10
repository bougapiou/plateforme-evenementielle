package bf.evenements.plateforme.accreditation;

import bf.evenements.plateforme.common.domain.BaseEntity;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventActivity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A named accreditation badge issued by an organiser: grants a person a role
 * (speaker, exhibitor, moderator…) for one activity or for the whole event.
 * Carries its own QR code, checked at the entrance like a ticket.
 */
@Entity
@Table(name = "accreditations")
@Getter
@Setter
@NoArgsConstructor
public class Accreditation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    /** Null = valid for every activity of the event. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private EventActivity activity;

    @Column(nullable = false, length = 40)
    private String numero;

    @Column(name = "personne_nom", nullable = false, length = 200)
    private String personneNom;

    @Column(name = "personne_email", length = 180)
    private String personneEmail;

    @Column(length = 200)
    private String organisation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccreditationRole fonction = AccreditationRole.AUTRE;

    /** Free-text label shown when {@link #fonction} is {@code AUTRE}. */
    @Column(name = "fonction_libre", length = 120)
    private String fonctionLibre;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "qr_token", nullable = false, length = 80)
    private String qrToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccreditationStatus statut = AccreditationStatus.ACTIVE;

    /** Human label of the function (respects the free-text override). */
    public String fonctionLabel() {
        if (fonction == AccreditationRole.AUTRE && fonctionLibre != null && !fonctionLibre.isBlank()) {
            return fonctionLibre;
        }
        return fonction.libelle();
    }

    public enum AccreditationStatus {
        ACTIVE, REVOQUEE
    }
}
