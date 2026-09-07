package bf.evenements.plateforme.registration;

public enum RegistrationStatus {
    /** Awaiting payment and/or organiser validation. */
    EN_ATTENTE,
    /** Validated — the participant is registered. */
    CONFIRMEE,
    /** Cancelled by the participant. */
    ANNULEE,
    /** Rejected by the organiser. */
    REFUSEE
}
