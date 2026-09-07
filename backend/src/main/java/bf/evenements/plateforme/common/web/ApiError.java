package bf.evenements.plateforme.common.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Normalised error payload returned by {@code GlobalExceptionHandler}.
 *
 * @param timestamp when the error occurred
 * @param status    HTTP status code
 * @param error     HTTP reason phrase
 * @param code      stable machine-readable error code
 * @param message   human-readable message (French)
 * @param path      request path
 * @param fieldErrors per-field validation messages, when applicable
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<Map<String, String>> fieldErrors) {
}
