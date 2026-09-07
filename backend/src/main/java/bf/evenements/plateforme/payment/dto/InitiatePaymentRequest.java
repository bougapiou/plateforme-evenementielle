package bf.evenements.plateforme.payment.dto;

import bf.evenements.plateforme.payment.PaymentMethod;
import bf.evenements.plateforme.payment.PaymentTargetType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InitiatePaymentRequest(
        @NotNull PaymentTargetType targetType,
        @NotNull UUID targetId,
        PaymentMethod moyen,
        String returnUrl) {
}
