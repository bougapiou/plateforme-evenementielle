package bf.evenements.plateforme.checkin;

/** How a presence was recorded. */
public enum CheckinCanal {
    /** QR scanned by a control agent (online). */
    SCAN,
    /** Scanned by an agent while offline, synchronised afterwards. */
    HORS_LIGNE,
    /** The participant confirmed his presence himself with the room's QR (backup mode). */
    AUTO_ENREGISTREMENT
}
