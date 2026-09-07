package bf.evenements.plateforme.payment;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.common.config.AppProperties;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.events.PaymentSucceededEvent;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.money.Money;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.common.web.References;
import bf.evenements.plateforme.payment.dto.InitiatePaymentRequest;
import bf.evenements.plateforme.payment.dto.PaymentResponse;
import bf.evenements.plateforme.payment.provider.PaymentProvider;
import bf.evenements.plateforme.payment.provider.SandboxPaymentProvider;
import bf.evenements.plateforme.stand.StandReservation;
import bf.evenements.plateforme.stand.StandReservationRepository;
import bf.evenements.plateforme.stand.StandReservationService;
import bf.evenements.plateforme.ticket.TicketOrder;
import bf.evenements.plateforme.ticket.TicketOrderRepository;
import bf.evenements.plateforme.ticket.TicketOrderService;
import bf.evenements.plateforme.ticket.TicketOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final List<PaymentProvider> providers;
    private final SandboxPaymentProvider sandboxProvider;
    private final TicketOrderRepository ticketOrderRepository;
    private final StandReservationRepository standReservationRepository;
    private final TicketOrderService ticketOrderService;
    private final StandReservationService standReservationService;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;
    private final AppProperties appProperties;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    private PaymentProvider provider() {
        String configured = appProperties.payment().provider();
        return providers.stream().filter(p -> p.name().equalsIgnoreCase(configured)).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucun PaymentProvider pour '" + configured + "'"));
    }

    // ---------------------------------------------------------- initiate

    @Transactional
    public PaymentResponse initiate(InitiatePaymentRequest request) {
        UUID me = currentUser.requireId();
        Target target = loadTarget(request.targetType(), request.targetId(), me);

        Payment payment = new Payment();
        payment.setReference(References.unique("PAY", 8, paymentRepository::existsByReference));
        payment.setProvider(provider().name());
        payment.setMoyen(request.moyen() != null ? request.moyen() : PaymentMethod.SANDBOX);
        payment.setTargetType(request.targetType());
        payment.setTargetId(request.targetId());
        payment.setUserId(me);
        payment.setEventId(target.eventId());
        payment.setMontant(target.amount().amount());
        payment.setDevise(target.amount().currency());
        payment.setStatut(PaymentStatus.EN_ATTENTE);
        switch (request.targetType()) {
            case TICKET_ORDER -> payment.setTicketOrderId(request.targetId());
            case STAND_RESERVATION -> payment.setStandReservationId(request.targetId());
        }

        PaymentProvider.Initiation init = provider().initiate(new PaymentProvider.Context(
                payment.getReference(), target.amount(), target.customerEmail(),
                target.description(), request.returnUrl()));
        payment.setProviderRef(init.providerRef());
        payment.setPaymentUrl(init.redirectUrl());
        payment = paymentRepository.save(payment);

        auditService.record(me, currentUser.require().email(), "PAYMENT_INITIATED", "Payment",
                payment.getId().toString(), null,
                "ref=" + payment.getReference() + " montant=" + payment.getMontant());
        return PaymentResponse.from(payment);
    }

    // ---------------------------------------------------------- webhook

    /** Handles a provider callback. Idempotent by (provider, transactionRef). */
    @Transactional
    public void handleWebhook(String rawBody, String signatureHeader) {
        PaymentProvider.WebhookResult result = provider().verifyWebhook(rawBody, signatureHeader);

        Payment payment = paymentRepository.findByReference(result.reference())
                .or(() -> result.providerRef() != null
                        ? paymentRepository.findByProviderAndTransactionRef(provider().name(),
                                result.providerRef())
                        : java.util.Optional.empty())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Paiement inconnu pour la référence " + result.reference()));

        // idempotency
        if (payment.getStatut() == PaymentStatus.REUSSI) {
            log.debug("Webhook ignoré (paiement déjà réussi) : {}", payment.getReference());
            return;
        }

        applyOutcome(payment, result);
    }

    /** Sandbox helper: simulates the provider POSTing a signed webhook. Owner only. */
    @Transactional
    public PaymentResponse simulate(String reference, String outcome) {
        if (!"sandbox".equalsIgnoreCase(appProperties.payment().provider())) {
            throw new BusinessException("SANDBOX_DISABLED",
                    "La simulation n'est possible qu'avec le provider 'sandbox'.");
        }
        Payment payment = paymentRepository.findByReference(reference)
                .orElseThrow(() -> ResourceNotFoundException.of("Paiement", reference));
        if (!payment.getUserId().equals(currentUser.requireId())
                && !currentUser.hasAuthority(bf.evenements.plateforme.rbac.Permissions.PAYMENT_MANAGE)) {
            throw new AccessDeniedException("Accès au paiement refusé.");
        }
        String body = sandboxProvider.buildSignedWebhookBody(reference, payment.getProviderRef(),
                outcome == null ? "SUCCESS" : outcome);
        handleWebhook(body, sandboxProvider.sign(body));
        return PaymentResponse.from(paymentRepository.findByReference(reference).orElseThrow());
    }

    /** One-shot sandbox payment used by the ticket / stand "pay-sandbox" endpoints. */
    @Transactional
    public void quickSandboxPay(PaymentTargetType targetType, UUID targetId) {
        PaymentResponse init = initiate(new InitiatePaymentRequest(targetType, targetId,
                PaymentMethod.SANDBOX, null));
        simulate(init.reference(), "SUCCESS");
    }

    // ---------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID id) {
        Payment p = paymentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Paiement", id));
        if (!p.getUserId().equals(currentUser.requireId())
                && !currentUser.hasAuthority(bf.evenements.plateforme.rbac.Permissions.PAYMENT_READ)) {
            throw new AccessDeniedException("Accès au paiement refusé.");
        }
        return PaymentResponse.from(p);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> myPayments(Pageable pageable) {
        return PageResponse.of(
                paymentRepository.findByUserIdOrderByCreatedAtDesc(currentUser.requireId(), pageable),
                PaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> adminList(@Nullable PaymentStatus statut, Pageable pageable) {
        Specification<Payment> spec = statut == null ? null
                : (root, q, cb) -> cb.equal(root.get("statut"), statut);
        return PageResponse.of(paymentRepository.findAll(spec, pageable), PaymentResponse::from);
    }

    @Transactional
    public PaymentResponse refund(UUID id) {
        Payment p = paymentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Paiement", id));
        if (p.getStatut() != PaymentStatus.REUSSI) {
            throw new BusinessException("NOT_REFUNDABLE", "Seul un paiement réussi peut être remboursé.");
        }
        p.setStatut(PaymentStatus.REMBOURSE);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "PAYMENT_REFUNDED", "Payment", id.toString(), null, "ref=" + p.getReference());
        return PaymentResponse.from(p);
    }

    // ---------------------------------------------------------- internals

    private void applyOutcome(Payment payment, PaymentProvider.WebhookResult result) {
        switch (result.outcome()) {
            case SUCCESS -> {
                payment.setStatut(PaymentStatus.REUSSI);
                payment.setTransactionRef(result.transactionRef());
                payment.setPaidAt(Instant.now());
                try {
                    paymentRepository.saveAndFlush(payment);
                } catch (DataIntegrityViolationException dup) {
                    log.info("Webhook doublon ignoré : {}", payment.getReference());
                    return;
                }
                confirmTarget(payment);
                auditService.record(payment.getUserId(), null, "PAYMENT_SUCCEEDED", "Payment",
                        payment.getId().toString(), null, "ref=" + payment.getReference());
                eventPublisher.publishEvent(new PaymentSucceededEvent(
                        payment.getTargetType().name(), payment.getTargetId(), payment.getId()));
            }
            case CANCELLED -> {
                payment.setStatut(PaymentStatus.ANNULE);
                payment.setEchecMotif(result.reason());
            }
            case FAILED -> {
                payment.setStatut(PaymentStatus.ECHOUE);
                payment.setEchecMotif(result.reason() != null ? result.reason() : "Paiement refusé");
            }
        }
    }

    private void confirmTarget(Payment payment) {
        switch (payment.getTargetType()) {
            case TICKET_ORDER -> ticketOrderService.markPaid(payment.getTargetId());
            case STAND_RESERVATION -> standReservationService.markPaid(payment.getTargetId());
        }
    }

    private Target loadTarget(PaymentTargetType type, UUID id, UUID actorId) {
        return switch (type) {
            case TICKET_ORDER -> {
                TicketOrder o = ticketOrderRepository.findById(id)
                        .orElseThrow(() -> ResourceNotFoundException.of("Commande", id));
                if (!o.getUser().getId().equals(actorId)) {
                    throw new AccessDeniedException("Cette commande ne vous appartient pas.");
                }
                if (o.getStatut() != TicketOrderStatus.EN_ATTENTE) {
                    throw new BusinessException("ORDER_NOT_PAYABLE",
                            "Commande non payable (" + o.getStatut() + ").");
                }
                yield new Target(o.getEvent().getId(), o.total(), o.getAcheteurEmail(),
                        "Billets " + o.getEvent().getNom() + " (" + o.getReference() + ")");
            }
            case STAND_RESERVATION -> {
                StandReservation r = standReservationRepository.findById(id)
                        .orElseThrow(() -> ResourceNotFoundException.of("Réservation", id));
                if (!r.belongsTo(actorId)) {
                    throw new AccessDeniedException("Cette réservation ne vous appartient pas.");
                }
                if (!r.getStatut().isPending()) {
                    throw new BusinessException("RESERVATION_NOT_PAYABLE",
                            "Réservation non payable (" + r.getStatut() + ").");
                }
                yield new Target(r.getEvent().getId(), r.amount(), null,
                        "Stand " + r.getStand().getNumero() + " — " + r.getEvent().getNom());
            }
        };
    }

    private record Target(UUID eventId, Money amount, String customerEmail, String description) {
        Target {
            if (amount == null || amount.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("NOTHING_TO_PAY", "Aucun montant à régler.");
            }
        }
    }
}
