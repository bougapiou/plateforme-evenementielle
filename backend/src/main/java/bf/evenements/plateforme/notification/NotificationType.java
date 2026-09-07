package bf.evenements.plateforme.notification;

/** Business events that trigger a notification (matches the spec, section 14). */
public enum NotificationType {
    INSCRIPTION_CONFIRMEE,
    PAIEMENT_CONFIRME,
    PAIEMENT_ECHOUE,
    RESERVATION_CONFIRMEE,
    RESERVATION_EXPIREE,
    BILLET_DISPONIBLE,
    EVENEMENT_VALIDE,
    EVENEMENT_REFUSE,
    EVENEMENT_PUBLIE,
    EVENEMENT_MODIFIE,
    EVENEMENT_ANNULE,
    RAPPEL_EVENEMENT,
    MESSAGE_ORGANISATEUR
}
