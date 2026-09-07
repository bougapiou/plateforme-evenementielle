package bf.evenements.plateforme.user;

public enum UserStatus {
    /** Can authenticate and use the platform. */
    ACTIF,
    /** Registered but e-mail / account not yet confirmed or approved. */
    EN_ATTENTE,
    /** Blocked by an administrator. */
    DESACTIVE
}
