package bf.evenements.plateforme.payment.provider;

import bf.evenements.plateforme.common.config.ArzekaProperties;
import bf.evenements.plateforme.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Real FasoArzeka gateway (AvePLUS "Avepay" API). Selected by setting
 * {@code app.payment.provider=arzeka} — the bundled {@code sandbox} provider
 * stays the default so local dev and the test suite are unaffected.
 *
 * <p>Security note: FasoArzeka's redirect callback ({@code linkBackToCallingWebsite}
 * / {@code linkForUpdateStatus}, doc §3.1) is a plain, unsigned GET with query
 * parameters — anyone could call that URL with a forged {@code status=SUCCESS}.
 * So {@link #verifyWebhook} never trusts it: it only reads the
 * {@code paymentRequestId} (our own payment reference) from it and then asks
 * FasoArzeka's authenticated "check payment" endpoint (doc §3.2, Bearer token)
 * for the real status. Only that server-to-server answer can confirm a payment.
 */
@Slf4j
@Component
public class ArzekaPaymentProvider implements PaymentProvider {

    /** Where FasoArzeka is told to redirect / notify — see {@link #initiate}. */
    public static final String CALLBACK_PATH = "/api/payments/arzeka/callback";

    private final ArzekaProperties props;
    private final ArzekaTokenService tokenService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ArzekaPaymentProvider(ArzekaProperties props, ArzekaTokenService tokenService,
                                 @org.springframework.beans.factory.annotation.Qualifier("arzekaRestTemplate")
                                 RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.props = props;
        this.tokenService = tokenService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "arzeka";
    }

    @Override
    public Initiation initiate(Context context) {
        String msisdn = normalizePhone(context.customerPhone());
        if (msisdn == null) {
            throw new BusinessException("PHONE_REQUIRED_FOR_PAYMENT",
                    "Un numéro de téléphone valide est requis pour payer via FasoArzeka.");
        }
        String amount = context.amount().amount().setScale(0, RoundingMode.HALF_UP).toPlainString();
        String callback = base64(props.callbackBaseUrl() + CALLBACK_PATH);

        String url = UriComponentsBuilder.fromHttpUrl(props.baseUrl() + props.initiatePaymentUri())
                .queryParam("amount", amount)
                .queryParam("msisdn", msisdn)
                .queryParam("merchantid", props.merchantId())
                .queryParam("securedAccessToken", tokenService.getValidToken())
                .queryParam("mappedOrderId", context.reference())
                .queryParam("linkForUpdateStatus", callback)
                .queryParam("linkBackToCallingWebsite", callback)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        // Our own payment reference doubles as FasoArzeka's mappedOrderId, so
        // the callback and the status check both key straight off it.
        return new Initiation(context.reference(), url);
    }

    @Override
    public WebhookResult verifyWebhook(String rawBody, String signatureHeader) {
        String mappedOrderId;
        try {
            Map<String, String> params = objectMapper.readValue(rawBody,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                    });
            mappedOrderId = params.get("paymentRequestId");
        } catch (Exception e) {
            throw new WebhookVerificationException("Retour FasoArzeka illisible.");
        }
        if (mappedOrderId == null || mappedOrderId.isBlank()) {
            throw new WebhookVerificationException("paymentRequestId manquant dans le retour FasoArzeka.");
        }
        return checkStatus(mappedOrderId);
    }

    @Override
    public WebhookResult checkStatus(String mappedOrderId) {
        String url = UriComponentsBuilder.fromHttpUrl(props.baseUrl() + props.checkOrderStatusUri())
                .queryParam("mappedOrderId", mappedOrderId)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenService.getValidToken());
        try {
            var resp = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Map<String, Object> body = resp.getBody();
            String status = body == null || body.get("status") == null
                    ? "" : body.get("status").toString().toUpperCase();
            Outcome outcome = switch (status) {
                case "COMPLETED", "SUCCESS" -> Outcome.SUCCESS;
                case "FAILED", "REJECTED", "CANCELLED", "ANNULE" -> Outcome.FAILED;
                default -> Outcome.PENDING;
            };
            String transId = body == null ? null
                    : String.valueOf(body.getOrDefault("third_party_trans_id", ""));
            return new WebhookResult(mappedOrderId, mappedOrderId, transId, outcome, null);
        } catch (HttpClientErrorException.NotFound e) {
            log.info("FasoArzeka : {} pas (encore) connu côté fournisseur.", mappedOrderId);
            return new WebhookResult(mappedOrderId, mappedOrderId, null, Outcome.PENDING, null);
        } catch (RestClientException e) {
            log.error("FasoArzeka : vérification du statut impossible pour {} : {}",
                    mappedOrderId, e.getMessage());
            return new WebhookResult(mappedOrderId, mappedOrderId, null, Outcome.PENDING,
                    "Vérification indisponible");
        }
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Best-effort conversion to the "2266XXXXXXXX" shape FasoArzeka expects. */
    private String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.length() == 8) { // local number without the 226 country code
            digits = "226" + digits;
        }
        return digits.isBlank() ? null : digits;
    }
}
