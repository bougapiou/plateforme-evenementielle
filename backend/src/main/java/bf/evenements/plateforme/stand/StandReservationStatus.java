package bf.evenements.plateforme.stand;

import java.util.Set;

public enum StandReservationStatus {
    EN_ATTENTE,
    RESERVE_TEMP,
    ATTENTE_PAIEMENT,
    PAYE,
    CONFIRME,
    ANNULE,
    EXPIRE;

    /** Statuses that hold a stand — matches the partial unique index in V5. */
    public static final Set<StandReservationStatus> ACTIVE =
            Set.of(RESERVE_TEMP, ATTENTE_PAIEMENT, PAYE, CONFIRME);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean isPending() {
        return this == RESERVE_TEMP || this == ATTENTE_PAIEMENT;
    }
}
