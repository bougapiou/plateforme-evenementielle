package bf.evenements.plateforme.common.events;

import java.util.UUID;

/**
 * Published when a payment is confirmed for a target entity. Other modules react
 * (e.g. registration confirmation, invoice generation) without a hard dependency.
 *
 * @param targetType e.g. {@code "TICKET_ORDER"}, {@code "STAND_RESERVATION"}
 * @param targetId    id of that entity
 */
public record PaymentSucceededEvent(String targetType, UUID targetId, UUID paymentId) {

    public static final String TICKET_ORDER = "TICKET_ORDER";
    public static final String STAND_RESERVATION = "STAND_RESERVATION";

    public PaymentSucceededEvent(String targetType, UUID targetId) {
        this(targetType, targetId, null);
    }
}
