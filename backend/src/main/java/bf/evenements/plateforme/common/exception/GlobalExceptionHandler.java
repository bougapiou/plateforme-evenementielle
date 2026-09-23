package bf.evenements.plateforme.common.exception;

import bf.evenements.plateforme.common.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        List<Map<String, String>> fieldErrors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.add(Map.of(
                    "field", fe.getField(),
                    "message", fe.getDefaultMessage() == null ? "invalide" : fe.getDefaultMessage()));
        }
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Certains champs sont invalides.", request, fieldErrors);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "Authentification requise ou invalide.", request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        log.warn("Accès refusé sur {} pour {} : {}",
                request != null ? request.getRequestURI() : "?",
                auth != null ? auth.getName() : "anonyme", ex.getMessage());

        // A missing/expired/invalid bearer token leaves the request "anonyme"
        // (JwtAuthenticationFilter clears the context rather than rejecting
        // outright, so that public endpoints keep working). @PreAuthorize then
        // fails with this same AccessDeniedException regardless of the reason —
        // but for an anonymous caller the real problem is "not logged in", not
        // "logged in without the right". Answering 401 here (instead of 403)
        // lets the frontend's interceptor refresh the token and retry
        // transparently; it only listens for 401.
        if (auth == null || TRUST_RESOLVER.isAnonymous(auth)) {
            return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                    "Authentification requise ou invalide.", request, List.of());
        }
        String message = StringUtils.hasText(ex.getMessage())
                ? ex.getMessage() : "Vous n'avez pas les droits pour cette action.";
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message, request, List.of());
    }

    /**
     * Missing file under /files/** (or any other static resource) — a normal
     * 404, not a server error. Without this, the catch-all below turned every
     * missing upload into a 500, masking the real problem.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "Fichier introuvable.", request, List.of());
    }

    /**
     * A path that exists but not for this HTTP method (e.g. a plain POST to
     * an update endpoint that only maps PUT) — a normal 405, not a server
     * error. Same class of bug as {@link #handleNoResource}: without this,
     * the catch-all below turned it into a 500.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Méthode non autorisée pour cette ressource.", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erreur non gérée sur {} : {}",
                request != null ? request.getRequestURI() : "?", ex.toString(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Une erreur interne est survenue.", request, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           HttpServletRequest request,
                                           List<Map<String, String>> fieldErrors) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request != null ? request.getRequestURI() : null,
                fieldErrors.isEmpty() ? null : fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
