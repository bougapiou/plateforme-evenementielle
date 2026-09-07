package bf.evenements.plateforme.ticket;

public enum TicketStatus {
    /** Issued and valid for entry. */
    EMISE,
    /** Already scanned at the entrance. */
    UTILISE,
    /** Voided (order refunded / cancelled after issue). */
    ANNULE
}
