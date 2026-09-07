package bf.evenements.plateforme.stand;

import bf.evenements.plateforme.common.config.AppProperties;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.payment.PaymentService;
import bf.evenements.plateforme.payment.PaymentTargetType;
import bf.evenements.plateforme.rbac.Permissions;
import bf.evenements.plateforme.stand.dto.CreateStandReservationRequest;
import bf.evenements.plateforme.stand.dto.StandReservationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stand-reservations")
@RequiredArgsConstructor
@Tag(name = "Stands — réservations")
public class StandReservationController {

    private final StandReservationService service;
    private final PaymentService paymentService;
    private final AppProperties appProperties;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.STAND_RESERVE + "')")
    @Operation(summary = "Réserver un stand (blocage temporaire 15 min)")
    public StandReservationResponse reserve(@Valid @RequestBody CreateStandReservationRequest request) {
        return service.reserve(request);
    }

    @GetMapping("/my")
    @Operation(summary = "Mes réservations de stands")
    public PageResponse<StandReservationResponse> mine(@PageableDefault(size = 20) Pageable pageable) {
        return service.myReservations(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une réservation")
    public StandReservationResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/for-event/{eventId}")
    @PreAuthorize("hasAuthority('" + Permissions.STAND_MANAGE + "')")
    @Operation(summary = "Réservations d'un événement (organisateur)")
    public PageResponse<StandReservationResponse> forEvent(@PathVariable UUID eventId,
                                                           @PageableDefault(size = 20) Pageable pageable) {
        return service.forEvent(eventId, pageable);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler une réservation non payée")
    public StandReservationResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @org.springframework.web.bind.annotation.GetMapping(value = "/{id}/confirmation.pdf",
            produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Confirmation de réservation de stand (PDF)")
    public org.springframework.http.ResponseEntity<byte[]> confirmation(@PathVariable UUID id) {
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(service.confirmationPdf(id));
    }

    @PostMapping("/{id}/pay-sandbox")
    @Operation(summary = "[SANDBOX] Confirmer le paiement d'une réservation (dev / démo)")
    public StandReservationResponse paySandbox(@PathVariable UUID id) {
        if (!"sandbox".equalsIgnoreCase(appProperties.payment().provider())) {
            throw new BusinessException("SANDBOX_DISABLED",
                    "Le paiement simulé n'est disponible qu'avec le fournisseur 'sandbox'.");
        }
        paymentService.quickSandboxPay(PaymentTargetType.STAND_RESERVATION, id);
        return service.get(id);
    }
}
