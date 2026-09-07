package bf.evenements.plateforme.ticket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Releases the reserved quota of ticket orders whose payment window elapsed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketExpiryJob {

    private final TicketOrderService ticketOrderService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void run() {
        int expired = ticketOrderService.expireStaleOrders();
        if (expired > 0) {
            log.info("Commandes de billets expirées : {}", expired);
        }
    }
}
