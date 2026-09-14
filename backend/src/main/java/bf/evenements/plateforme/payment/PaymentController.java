package bf.evenements.plateforme.payment;

import bf.evenements.plateforme.common.config.AppProperties;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.payment.dto.InitiatePaymentRequest;
import bf.evenements.plateforme.payment.dto.PaymentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Paiements")
public class PaymentController {

    private final PaymentService paymentService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Initier un paiement pour une commande de billets ou une réservation de stand")
    public PaymentResponse initiate(@Valid @RequestBody InitiatePaymentRequest request) {
        return paymentService.initiate(request);
    }

    @GetMapping("/my")
    @Operation(summary = "Mes paiements")
    public PageResponse<PaymentResponse> mine(@PageableDefault(size = 20) Pageable pageable) {
        return paymentService.myPayments(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un paiement")
    public PaymentResponse get(@PathVariable UUID id) {
        return paymentService.get(id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    @Operation(summary = "Tous les paiements (admin)")
    public PageResponse<PaymentResponse> list(@RequestParam(required = false) PaymentStatus statut,
                                              @PageableDefault(size = 20) Pageable pageable) {
        return paymentService.adminList(statut, pageable);
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('PAYMENT_MANAGE')")
    @Operation(summary = "Rembourser un paiement (admin)")
    public PaymentResponse refund(@PathVariable UUID id) {
        return paymentService.refund(id);
    }

    @PostMapping("/{reference}/simulate")
    @Operation(summary = "[SANDBOX] Simuler le retour du fournisseur de paiement")
    public PaymentResponse simulate(@PathVariable String reference,
                                    @RequestParam(defaultValue = "SUCCESS") String outcome) {
        return paymentService.simulate(reference, outcome);
    }

    @PostMapping("/{reference}/recheck")
    @Operation(summary = "Revérifier un paiement auprès du fournisseur (filet de sécurité)")
    public PaymentResponse recheck(@PathVariable String reference) {
        return paymentService.recheck(reference);
    }

    @PostMapping("/webhook")
    @Operation(summary = "Webhook du fournisseur de paiement (signé HMAC)")
    public ResponseEntity<String> webhook(HttpServletRequest request,
                                          @RequestHeader(value = "X-Payment-Signature", required = false)
                                          String signature) throws IOException {
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        paymentService.handleWebhook(body, signature);
        return ResponseEntity.ok("ok");
    }

    /**
     * FasoArzeka's own redirect: an unsigned GET with the transaction's query
     * parameters (doc §3.1). We never trust these values directly — see
     * {@code ArzekaPaymentProvider.verifyWebhook} — they only carry the
     * {@code paymentRequestId} that lets us re-check the real status
     * server-to-server before touching the payment. The browser is then sent
     * back to the app.
     */
    @GetMapping("/arzeka/callback")
    @Operation(summary = "Retour FasoArzeka (redirection navigateur, non authentifiée)")
    public ResponseEntity<Void> arzekaCallback(@RequestParam Map<String, String> params)
            throws IOException {
        paymentService.handleWebhook(objectMapper.writeValueAsString(params), null);
        String reference = params.get("paymentRequestId");
        String target = appProperties.frontendBaseUrl() + "/tableau-de-bord/paiements"
                + (reference != null ? "?reference=" + reference : "");
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }
}
