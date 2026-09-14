package bf.evenements.plateforme.payment;

import bf.evenements.plateforme.common.domain.BaseEntity;
import bf.evenements.plateforme.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String reference;

    /** Confirmed transaction id from the provider (unique per provider). */
    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;

    /** Provider-side handle returned at initiation. */
    @Column(name = "provider_ref", length = 100)
    private String providerRef;

    @Column(nullable = false, length = 40)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod moyen = PaymentMethod.SANDBOX;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private PaymentTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "ticket_order_id")
    private UUID ticketOrderId;

    @Column(name = "stand_reservation_id")
    private UUID standReservationId;

    @Column(name = "registration_id")
    private UUID registrationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false, length = 3)
    private String devise = Money.DEFAULT_CURRENCY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus statut = PaymentStatus.EN_ATTENTE;

    @Column(name = "payment_url", columnDefinition = "text")
    private String paymentUrl;

    @Column(name = "echec_motif", length = 500)
    private String echecMotif;

    @Column(name = "paid_at")
    private Instant paidAt;

    public Money amount() {
        return Money.of(montant, devise);
    }

    public boolean isPending() {
        return statut == PaymentStatus.EN_ATTENTE;
    }
}
