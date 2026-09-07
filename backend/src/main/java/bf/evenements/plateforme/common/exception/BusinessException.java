package bf.evenements.plateforme.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A business rule was violated (invalid state transition, quota exceeded, etc.).
 */
public class BusinessException extends ApiException {

    public BusinessException(String message) {
        this("BUSINESS_RULE_VIOLATION", message);
    }

    public BusinessException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
