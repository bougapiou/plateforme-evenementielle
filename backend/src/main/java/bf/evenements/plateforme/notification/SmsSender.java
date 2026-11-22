package bf.evenements.plateforme.notification;

import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Sends SMS through the configured provider ({@code app.sms.provider}):
 * <ul>
 *   <li>{@code log} (default, dev): the message is only written to the logs;</li>
 *   <li>{@code http}: POST {@code {"to", "from", "message"}} as JSON to
 *       {@code app.sms.http.url} with a Bearer token — adapt the gateway of the
 *       telecom operator / aggregator to this contract.</li>
 * </ul>
 * Failures are swallowed (returns false) so an SMS outage never breaks a transaction.
 */
@Slf4j
@Component
public class SmsSender {

    private final String provider;
    private final String url;
    private final String token;
    private final String from;
    private final RestClient http;

    public SmsSender(@Value("${app.sms.provider:log}") String provider,
                     @Value("${app.sms.http.url:}") String url,
                     @Value("${app.sms.http.token:}") String token,
                     @Value("${app.sms.from:PNE}") String from) {
        this.provider = provider;
        this.url = url;
        this.token = token;
        this.from = from;
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** True when a real gateway is configured (codes actually reach phones). */
    public boolean isReal() {
        return "http".equalsIgnoreCase(provider) && url != null && !url.isBlank();
    }

    public boolean send(String to, String message) {
        if (!isReal()) {
            log.info("SMS (fournisseur « log ») vers {} : {}", to, message);
            return true;
        }
        try {
            http.post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (token != null && !token.isBlank()) {
                            h.setBearerAuth(token);
                        }
                    })
                    .body(Map.of("to", to, "from", from, "message", message))
                    .retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            log.warn("Envoi SMS vers {} échoué : {}", to, e.getMessage());
            return false;
        }
    }
}
