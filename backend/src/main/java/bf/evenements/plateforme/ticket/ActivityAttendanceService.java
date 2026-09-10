package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.event.ActivityAccess;
import bf.evenements.plateforme.event.EventActivity;
import bf.evenements.plateforme.event.EventActivityRepository;
import bf.evenements.plateforme.ticket.dto.CreateOrderRequest;
import bf.evenements.plateforme.ticket.dto.TicketResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Participer" to a free activity: issues one free electronic ticket (QR) for the
 * current session by placing a zero-cost order on the activity's auto-managed
 * category. Guests are allowed (they carry {@code TICKET_PURCHASE}).
 */
@Service
@RequiredArgsConstructor
public class ActivityAttendanceService {

    private final EventActivityRepository activityRepository;
    private final TicketOrderService ticketOrderService;
    private final TicketRepository ticketRepository;

    @Transactional
    public TicketResponse attend(UUID activityId) {
        EventActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> ResourceNotFoundException.of("Activité", activityId));
        if (activity.getAcces() != ActivityAccess.GRATUIT || activity.getFreeTicketId() == null) {
            throw new BusinessException("ACTIVITY_NOT_FREE",
                    "Cette activité n'est pas en accès gratuit sur billet.");
        }

        var order = ticketOrderService.createOrderInternal(new CreateOrderRequest(
                activity.getEvent().getId(), null, null, null,
                List.of(new CreateOrderRequest.Line(activity.getFreeTicketId(), 1))));

        // A zero-cost order is confirmed synchronously → exactly one ticket exists.
        return ticketRepository.findByOrderId(order.getId()).stream().findFirst()
                .map(TicketResponse::from)
                .orElseThrow(() -> new BusinessException("TICKET_NOT_ISSUED",
                        "Le billet n'a pas pu être émis. Réessayez."));
    }
}
