package bf.evenements.plateforme.checkin;

/** Role of a member of an event's team (delegation by the organiser). */
public enum StaffFonction {
    /** Control agent: scans tickets (possibly restricted to some rooms). */
    CONTROLE,
    /** Programme manager: edits the programme, speakers, partners and tickets of the event. */
    PROGRAMME
}
