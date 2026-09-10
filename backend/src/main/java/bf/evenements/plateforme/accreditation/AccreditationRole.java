package bf.evenements.plateforme.accreditation;

/** Function printed on an accreditation badge. */
public enum AccreditationRole {

    CONFERENCIER("Conférencier"),
    EXPOSANT("Exposant"),
    MODERATEUR("Modérateur"),
    MAITRE_CEREMONIE("Maître de cérémonie"),
    PANELISTE("Panéliste"),
    COMPETITEUR("Compétiteur"),
    INVITE("Invité"),
    PRESSE("Presse"),
    STAFF("Staff / organisation"),
    AUTRE("Autre");

    private final String libelle;

    AccreditationRole(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
