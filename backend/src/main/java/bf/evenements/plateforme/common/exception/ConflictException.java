package bf.evenements.plateforme.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The request conflicts with the current state of a resource (duplicate email,
 * concurrent reservation, already used ticket, ...).
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        this("CONFLICT", message);
    }

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
