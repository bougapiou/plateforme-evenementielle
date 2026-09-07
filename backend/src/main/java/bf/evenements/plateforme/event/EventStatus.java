package bf.evenements.plateforme.event;

import java.util.Set;

/**
 * Lifecycle of an event. Allowed transitions are enforced by {@code EventService}.
 */
public enum EventStatus {
    BROUILLON,
    SOUMIS,
    REFUSE,
    VALIDE,
    PUBLIE,
    INSCRIPTIONS_OUVERTES,
    INSCRIPTIONS_FERMEES,
    EN_COURS,
    TERMINE,
    SUSPENDU,
    ANNULE;

    /** States in which the organiser may still edit the event's core fields. */
    public boolean isEditable() {
        return this == BROUILLON || this == REFUSE || this == VALIDE;
    }

    /** States shown on the public site. */
    public boolean isPubliclyVisible() {
        return PUBLIC_STATES.contains(this);
    }

    /** States in which attendees may register (subject to the configured window). */
    public boolean acceptsRegistrations() {
        return this == PUBLIE || this == INSCRIPTIONS_OUVERTES;
    }

    private static final Set<EventStatus> PUBLIC_STATES = Set.of(
            PUBLIE, INSCRIPTIONS_OUVERTES, INSCRIPTIONS_FERMEES, EN_COURS, TERMINE);
}
