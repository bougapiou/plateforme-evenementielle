package bf.evenements.plateforme.common.exception;

import org.springframework.http.HttpStatus;

/** Account temporarily locked after too many failed log-ins (HTTP 423). */
public class AccountLockedException extends ApiException {

    public AccountLockedException(String message) {
        super(HttpStatus.LOCKED, "ACCOUNT_LOCKED", message);
    }
}
