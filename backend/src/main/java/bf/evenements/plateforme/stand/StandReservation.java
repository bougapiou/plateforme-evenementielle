package bf.evenements.plateforme.stand;

import bf.evenements.plateforme.common.domain.BaseEntity;
import bf.evenements.plateforme.common.money.Money;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.structure.Structure;
import bf.evenements.plateforme.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stand_reservations")
@Getter
@Setter
@NoArgsConstructor
public class StandReservation extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String reference;

    @Column(name = "numero_reservation", nullable = false, length = 40)
    private String numeroReservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stand_id", nullable = false)
    private Stand stand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stand_type_id", nullable = false)
    private StandType standType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structure_id")
    private Structure structure;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal montant = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String devise = Money.DEFAULT_CURRENCY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StandReservationStatus statut = StandReservationStatus.RESERVE_TEMP;

    @Column(columnDefinition = "text")
    private String informations;

    @Column(name = "hold_expire_le")
    private Instant holdExpireLe;

    @Column(name = "date_limite_paiement")
    private Instant dateLimitePaiement;

    @Column(name = "paye_le")
    private Instant payeLe;

    public Money amount() {
        return Money.of(montant, devise);
    }

    public boolean belongsTo(UUID userId) {
        return user != null && user.getId().equals(userId);
    }
}
