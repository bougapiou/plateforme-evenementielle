package bf.evenements.plateforme.checkin;

public enum CheckinResult {
    /** Ticket valid — entry granted, ticket marked used. */
    VALIDE,
    /** Ticket already scanned earlier. */
    DEJA_UTILISE,
    /** Unknown, cancelled or revoked ticket. */
    INVALIDE
}
