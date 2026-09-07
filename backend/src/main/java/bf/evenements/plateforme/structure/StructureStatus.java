package bf.evenements.plateforme.structure;

public enum StructureStatus {
    /** Créée, en attente de vérification par un administrateur. */
    EN_ATTENTE,
    /** Vérifiée : peut s'inscrire et réserver des stands. */
    VERIFIEE,
    /** Bloquée par un administrateur. */
    SUSPENDUE
}
