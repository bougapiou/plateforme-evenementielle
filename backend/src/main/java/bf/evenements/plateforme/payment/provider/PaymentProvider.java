package bf.evenements.plateforme.payment.provider;

import bf.evenements.plateforme.common.exception.ApiException;
import bf.evenements.plateforme.common.money.Money;
import org.springframework.http.HttpStatus;

/**
 * Payment gateway abstraction. Implementations: {@code sandbox} (bundled) and,
 * later, FasoArzeka / mobile-money aggregators. The active provider is selected
 * via {@code app.payment.provider}.
 */
public interface PaymentProvider {

    String name();

    /** Starts a payment; returns a provider handle and the URL to redirect the payer to. */
    Initiation initiate(Context context);

    /**
     * Verifies a webhook callback (signature) and extracts its result.
     * @throws WebhookVerificationException if the signature is invalid
     */
    WebhookResult verifyWebhook(String rawBody, String signatureHeader);

    /**
     * Asks the provider directly for a payment's current status — a safety net
     * for a missed/delayed webhook, or the only trustworthy source when the
     * webhook itself is unsigned. Not every provider supports this.
     */
    default WebhookResult checkStatus(String reference) {
        throw new UnsupportedOperationException(name() + " ne permet pas de vérifier un paiement à la demande.");
    }

    record Context(String reference, Money amount, String customerEmail, String customerPhone,
                   String description, String returnUrl) {
    }

    record Initiation(String providerRef, String redirectUrl) {
    }

    record WebhookResult(String reference, String providerRef, String transactionRef,
                         Outcome outcome, String reason) {
    }

    enum Outcome {
        SUCCESS, FAILED, CANCELLED,
        /** The provider reports the payment is still being processed — no state change yet. */
        PENDING
    }

    class WebhookVerificationException extends ApiException {
        public WebhookVerificationException(String message) {
            super(HttpStatus.BAD_REQUEST, "WEBHOOK_SIGNATURE_INVALID", message);
        }
    }
}
