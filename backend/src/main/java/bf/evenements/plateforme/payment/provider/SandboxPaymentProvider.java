package bf.evenements.plateforme.payment.provider;

import bf.evenements.plateforme.common.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Fully functional development provider. {@code initiate} points the payer to a
 * sandbox page served by the frontend; {@code /api/payments/{ref}/simulate}
 * triggers a signed webhook so the whole confirmation chain is exercised.
 */
@Component
@RequiredArgsConstructor
public class SandboxPaymentProvider implements PaymentProvider {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "sandbox";
    }

    @Override
    public Initiation initiate(Context context) {
        String providerRef = "SBX-" + java.util.UUID.randomUUID();
        String url = context.returnUrl() != null
                ? context.returnUrl() + (context.returnUrl().contains("?") ? "&" : "?")
                        + "paiement=" + context.reference()
                : "/paiement/" + context.reference();
        return new Initiation(providerRef, url);
    }

    @Override
    public WebhookResult verifyWebhook(String rawBody, String signatureHeader) {
        String secret = appProperties.payment().webhookSecret();
        if (!HmacSignatures.matches(rawBody, secret, signatureHeader)) {
            throw new WebhookVerificationException("Signature du webhook invalide.");
        }
        try {
            JsonNode node = objectMapper.readTree(rawBody);
            Outcome outcome = switch (node.path("outcome").asText("FAILED").toUpperCase()) {
                case "SUCCESS", "REUSSI" -> Outcome.SUCCESS;
                case "CANCELLED", "ANNULE" -> Outcome.CANCELLED;
                case "PENDING" -> Outcome.PENDING;
                default -> Outcome.FAILED;
            };
            return new WebhookResult(
                    node.path("reference").asText(null),
                    node.path("providerRef").asText(null),
                    node.path("transactionRef").asText(null),
                    outcome,
                    node.path("reason").asText(null));
        } catch (Exception e) {
            throw new WebhookVerificationException("Corps du webhook illisible.");
        }
    }

    /** Builds the signed body the sandbox "provider" would POST to our webhook. */
    public String buildSignedWebhookBody(String reference, String providerRef, String outcome) {
        try {
            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "reference", reference,
                    "providerRef", providerRef == null ? "" : providerRef,
                    "transactionRef", "TXN-" + java.util.UUID.randomUUID(),
                    "outcome", outcome));
            return body;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String sign(String body) {
        return HmacSignatures.sign(body, appProperties.payment().webhookSecret());
    }
}
