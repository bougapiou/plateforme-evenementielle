package bf.evenements.plateforme.ticket;

public enum TicketOrderStatus {
    /** Created, quota reserved, awaiting payment before {@code expireLe}. */
    EN_ATTENTE,
    /** Paid — tickets issued. */
    PAYEE,
    /** Cancelled by the buyer or organiser before payment. */
    ANNULEE,
    /** Hold elapsed without payment — quota released. */
    EXPIREE
}
