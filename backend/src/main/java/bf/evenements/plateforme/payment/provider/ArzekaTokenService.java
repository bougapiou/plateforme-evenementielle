package bf.evenements.plateforme.payment.provider;

import bf.evenements.plateforme.common.config.ArzekaProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Caches the OAuth-style access token FasoArzeka issues on
 * {@code /auth/getToken} (form-urlencoded, not JSON — a JSON body returns
 * HTTP 500 on their side). Refreshed a little ahead of its JWT {@code exp}.
 */
@Slf4j
@Component
public class ArzekaTokenService {

    private final ArzekaProperties props;
    private final RestTemplate restTemplate;
    private volatile String cachedToken;
    private volatile Instant expiresAt;

    public ArzekaTokenService(ArzekaProperties props,
                              @org.springframework.beans.factory.annotation.Qualifier("arzekaRestTemplate")
                              RestTemplate restTemplate) {
        this.props = props;
        this.restTemplate = restTemplate;
    }

    public synchronized String getValidToken() {
        if (props.testStaticToken() != null && !props.testStaticToken().isBlank()) {
            log.warn("FasoArzeka : jeton statique de test utilisé — à ne jamais garder en production.");
            return props.testStaticToken();
        }
        if (cachedToken == null || expiresAt == null || Instant.now().isAfter(expiresAt.minusSeconds(30))) {
            cachedToken = fetchToken();
            expiresAt = expiryOf(cachedToken);
            log.info("FasoArzeka : jeton obtenu, expire le {}", expiresAt);
        }
        return cachedToken;
    }

    private String fetchToken() {
        String url = props.baseUrl() + props.authUri();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("username", props.username());
        body.add("password", props.password());
        body.add("grant_type", props.grantType());

        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers),
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Map<String, Object> payload = resp.getBody();
            if (resp.getStatusCode() == HttpStatus.OK && payload != null) {
                Object token = payload.getOrDefault("access_token", payload.get("token"));
                if (token != null) {
                    return token.toString();
                }
            }
            log.error("FasoArzeka : réponse inattendue de /getToken — status={}, body={}",
                    resp.getStatusCode(), payload);
        } catch (RestClientException e) {
            log.error("FasoArzeka : échec de l'appel à /getToken (url={}) : {}", url, e.getMessage());
        }
        throw new IllegalStateException("Authentification FasoArzeka impossible.");
    }

    /** Best-effort read of the JWT's {@code exp} claim; a short default TTL if that fails. */
    private Instant expiryOf(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return Instant.now().plusSeconds(300);
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            int idx = payload.indexOf("\"exp\":");
            if (idx == -1) {
                return Instant.now().plusSeconds(300);
            }
            String tail = payload.substring(idx + 6);
            long exp = Long.parseLong(tail.split("[,}]")[0].trim());
            return Instant.ofEpochSecond(exp);
        } catch (Exception e) {
            log.warn("FasoArzeka : lecture de l'expiration du jeton impossible ({}), 5 min par défaut.",
                    e.getMessage());
            return Instant.now().plusSeconds(300);
        }
    }
}
