package bf.evenements.plateforme.notification;

import bf.evenements.plateforme.common.events.PaymentSucceededEvent;
import bf.evenements.plateforme.stand.StandReservationRepository;
import bf.evenements.plateforme.ticket.TicketOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Turns domain events into user notifications. */
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationService notificationService;
    private final TicketOrderRepository ticketOrderRepository;
    private final StandReservationRepository standReservationRepository;

    @EventListener
    @Transactional
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        if (PaymentSucceededEvent.TICKET_ORDER.equals(event.targetType())) {
            ticketOrderRepository.findById(event.targetId()).ifPresent(order ->
                    notificationService.notify(order.getUser().getId(),
                            NotificationType.BILLET_DISPONIBLE,
                            "Vos billets sont disponibles",
                            "Votre commande " + order.getReference() + " pour « "
                                    + order.getEvent().getNom() + " » est confirmée. "
                                    + "Vos billets électroniques et QR codes sont prêts.",
                            "/tableau-de-bord/billets"));
        } else if (PaymentSucceededEvent.STAND_RESERVATION.equals(event.targetType())) {
            standReservationRepository.findById(event.targetId()).ifPresent(res ->
                    notificationService.notify(res.getUser().getId(),
                            NotificationType.RESERVATION_CONFIRMEE,
                            "Réservation de stand confirmée",
                            "Le stand " + res.getStand().getNumero() + " pour « "
                                    + res.getEvent().getNom() + " » est confirmé (réservation "
                                    + res.getNumeroReservation() + ").",
                            "/tableau-de-bord/stands"));
        }
    }
}
