package bf.evenements.plateforme.auth;

import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.security.TokenHasher;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time SMS codes (6 digits, 10 minutes, 5 attempts, 3 codes per 15 minutes
 * and per number). Each operation commits on its own so a wrong attempt is
 * counted even when the calling request fails.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    static final Duration TTL = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_CODES_PER_WINDOW = 3;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final OtpCodeRepository repository;
    private final TokenHasher hasher;
    private final SecureRandom random = new SecureRandom();

    static String digits(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9]", "");
    }

    /** Creates a new code for this number and returns it (to be sent by SMS). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String issue(String phone) {
        String tel = digits(phone);
        if (repository.countByTelephoneAndCreatedAtAfter(tel, Instant.now().minus(WINDOW)) >= MAX_CODES_PER_WINDOW) {
            throw new BusinessException("OTP_TOO_MANY",
                    "Trop de codes demandés pour ce numéro. Réessayez dans quelques minutes.");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        OtpCode otp = new OtpCode();
        otp.setTelephone(tel);
        otp.setCodeHash(hasher.sha256(tel + ":" + code));
        otp.setExpireLe(Instant.now().plus(TTL));
        repository.save(otp);
        return code;
    }

    /** Checks (and consumes) the latest code of this number; counts failed attempts. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean consume(String phone, String code) {
        String tel = digits(phone);
        var otp = repository.findFirstByTelephoneAndUtiliseLeIsNullOrderByCreatedAtDesc(tel).orElse(null);
        if (otp == null || otp.getExpireLe().isBefore(Instant.now()) || otp.getTentatives() >= MAX_ATTEMPTS) {
            return false;
        }
        String clean = code == null ? "" : code.replaceAll("[^0-9]", "");
        if (!hasher.sha256(tel + ":" + clean).equals(otp.getCodeHash())) {
            otp.setTentatives(otp.getTentatives() + 1);
            return false;
        }
        otp.setUtiliseLe(Instant.now());
        return true;
    }
}
