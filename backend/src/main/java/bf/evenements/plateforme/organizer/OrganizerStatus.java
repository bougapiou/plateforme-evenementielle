package bf.evenements.plateforme.organizer;

public enum OrganizerStatus {
    /** Demande soumise, en attente d'approbation par un administrateur. */
    EN_ATTENTE,
    /** Approuvé : peut créer et gérer des événements. */
    ACTIF,
    /** Suspendu par un administrateur. */
    SUSPENDU
}
