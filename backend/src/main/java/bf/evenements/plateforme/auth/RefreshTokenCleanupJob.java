package bf.evenements.plateforme.auth;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically removes refresh tokens that expired more than a day ago. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository repository;

    @Scheduled(cron = "0 30 3 * * *")
    public void purge() {
        int removed = repository.deleteExpired(Instant.now().minus(Duration.ofDays(1)));
        if (removed > 0) {
            log.info("Purge des refresh tokens expires: {} supprimes", removed);
        }
    }
}
