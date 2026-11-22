package bf.evenements.plateforme.auth;

import bf.evenements.plateforme.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts failed log-ins per account and locks it temporarily after too many.
 * Runs in its own transaction so a failure is recorded even though the login
 * request itself rolls back.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;

    @Value("${app.security.login-max-failures:5}")
    private int maxFailures = 5;

    @Value("${app.security.login-lock-duration:PT15M}")
    private Duration lockDuration = Duration.ofMinutes(15);

    /** Records a failure; returns true when this failure locked the account. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(String email) {
        return userRepository.findByEmailIgnoreCase(email).map(u -> {
            int n = u.getEchecsConnexion() + 1;
            if (maxFailures > 0 && n >= maxFailures) {
                u.setEchecsConnexion(0);
                u.setVerrouilleJusqu(Instant.now().plus(lockDuration));
                return true;
            }
            u.setEchecsConnexion(n);
            return false;
        }).orElse(false);
    }

    public Duration lockDuration() {
        return lockDuration;
    }
}
