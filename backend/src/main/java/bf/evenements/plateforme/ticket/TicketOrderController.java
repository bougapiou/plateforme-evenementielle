package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.common.config.AppProperties;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.rbac.Permissions;
import bf.evenements.plateforme.ticket.dto.CreateOrderRequest;
import bf.evenements.plateforme.ticket.dto.TicketOrderResponse;
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
@RequestMapping("/api/ticket-orders")
@RequiredArgsConstructor
@Tag(name = "Billetterie — commandes")
public class TicketOrderController {

    private final TicketOrderService service;
    private final AppProperties appProperties;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.TICKET_PURCHASE + "')")
    @Operation(summary = "Créer une commande de billets (réserve le quota pendant 30 min)")
    public TicketOrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return service.createOrder(request);
    }

    @GetMapping("/my")
    @Operation(summary = "Mes commandes")
    public PageResponse<TicketOrderResponse> myOrders(@PageableDefault(size = 20) Pageable pageable) {
        return service.myOrders(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une commande")
    public TicketOrderResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/for-event/{eventId}")
    @PreAuthorize("hasAuthority('" + Permissions.TICKET_MANAGE + "')")
    @Operation(summary = "Commandes d'un événement (organisateur)")
    public PageResponse<TicketOrderResponse> forEvent(@PathVariable UUID eventId,
                                                      @PageableDefault(size = 20) Pageable pageable) {
        return service.forEvent(eventId, pageable);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler une commande en attente")
    public TicketOrderResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/pay-sandbox")
    @Operation(summary = "[SANDBOX] Confirmer le paiement d'une commande (dev / démo)")
    public TicketOrderResponse paySandbox(@PathVariable UUID id) {
        if (!"sandbox".equalsIgnoreCase(appProperties.payment().provider())) {
            throw new BusinessException("SANDBOX_DISABLED",
                    "Le paiement simulé n'est disponible qu'avec le fournisseur 'sandbox'.");
        }
        return service.confirmSandboxPayment(id);
    }
}
