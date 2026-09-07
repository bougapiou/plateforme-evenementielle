package bf.evenements.plateforme.user;

/**
 * Nature of the account holder. Drives which onboarding flow and dashboards apply.
 */
public enum UserType {
    /** Individual attendee. */
    PARTICULIER,
    /** Member / representative of a company or institution. */
    STRUCTURE,
    /** Event organiser. */
    ORGANISATEUR,
    /** Access-control / check-in staff. */
    PERSONNEL,
    /** Platform administrator. */
    ADMIN
}
