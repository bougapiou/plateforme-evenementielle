package bf.evenements.plateforme.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for expected, translatable application errors. Each subclass carries
 * an HTTP status and a stable {@code code} consumed by the frontend.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
