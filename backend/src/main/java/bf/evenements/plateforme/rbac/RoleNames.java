package bf.evenements.plateforme.rbac;

/**
 * Names of the seeded system roles.
 */
public final class RoleNames {

    private RoleNames() {
    }

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ORGANISATEUR = "ORGANISATEUR";
    public static final String PARTICIPANT = "PARTICIPANT";
    public static final String STRUCTURE = "STRUCTURE";
    public static final String PERSONNEL_CONTROLE = "PERSONNEL_CONTROLE";
    /** Validates / refuses submitted events, supervises the platform without technical settings. */
    public static final String VALIDEUR = "VALIDEUR";
    /** Delegated by an organiser to manage the programme of an event. */
    public static final String GESTIONNAIRE_PROGRAMME = "GESTIONNAIRE_PROGRAMME";
    /** Read-only access to the statistics of every event (e.g. ministry dashboards). */
    public static final String LECTEUR_STATISTIQUES = "LECTEUR_STATISTIQUES";
}
