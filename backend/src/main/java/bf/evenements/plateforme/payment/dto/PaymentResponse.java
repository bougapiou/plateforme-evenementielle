package bf.evenements.plateforme.payment.dto;

import bf.evenements.plateforme.common.money.Money;
import bf.evenements.plateforme.payment.Payment;
import bf.evenements.plateforme.payment.PaymentMethod;
import bf.evenements.plateforme.payment.PaymentStatus;
import bf.evenements.plateforme.payment.PaymentTargetType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String reference,
        String transactionRef,
        String provider,
        PaymentMethod moyen,
        PaymentTargetType targetType,
        UUID targetId,
        UUID eventId,
        BigDecimal montant,
        String devise,
        String montantFormatte,
        PaymentStatus statut,
        String paymentUrl,
        String echecMotif,
        Instant paidAt,
        Instant createdAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getReference(), p.getTransactionRef(), p.getProvider(), p.getMoyen(),
                p.getTargetType(), p.getTargetId(), p.getEventId(), p.getMontant(), p.getDevise(),
                Money.of(p.getMontant(), p.getDevise()).formatted(), p.getStatut(), p.getPaymentUrl(),
                p.getEchecMotif(), p.getPaidAt(), p.getCreatedAt());
    }
}
