package bf.evenements.plateforme.ticket;

/**
 * Which identity field(s) the public form asks for when {@link
 * EventTicket#isFormulaireRequis()} is true. Ignored otherwise (phone-only)
 * or for a paid category, which always collects both. The phone number is
 * never optional regardless of this setting — it's how a claimed ticket is
 * found again.
 */
public enum IdentiteRequise {
    NOM_ET_PRENOM,
    NOM_SEUL,
    PRENOM_SEUL
}
