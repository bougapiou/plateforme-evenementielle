package bf.evenements.plateforme.event;

/** How the public gains access to a programmed activity. */
public enum ActivityAccess {

    /** No dedicated ticket — access is governed by the event-level ticket (default). */
    SANS_BILLET,

    /** A free electronic ticket (QR) is required; the visitor requests it via "Participer". */
    GRATUIT,

    /** Paid ticket categories are scoped to this activity. */
    PAYANT
}
