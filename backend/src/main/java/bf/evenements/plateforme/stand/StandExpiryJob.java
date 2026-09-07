package bf.evenements.plateforme.stand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Frees stands whose temporary hold elapsed without payment. */
@Slf4j
@Component
@RequiredArgsConstructor
public class StandExpiryJob {

    private final StandReservationService reservationService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    public void run() {
        int expired = reservationService.expireStaleReservations();
        if (expired > 0) {
            log.info("Réservations de stands expirées : {}", expired);
        }
    }
}
