package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.rbac.Permissions;
import bf.evenements.plateforme.ticket.dto.EventTicketRequest;
import bf.evenements.plateforme.ticket.dto.EventTicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/tickets")
@RequiredArgsConstructor
@Tag(name = "Billetterie — catégories")
@PreAuthorize("hasAuthority('" + Permissions.TICKET_MANAGE + "')")
public class EventTicketController {

    private final EventTicketService service;

    @GetMapping
    @Operation(summary = "Lister les catégories de tickets (organisateur)")
    public List<EventTicketResponse> list(@PathVariable UUID eventId) {
        return service.listForManagement(eventId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une catégorie de tickets")
    public EventTicketResponse create(@PathVariable UUID eventId,
                                      @Valid @RequestBody EventTicketRequest request) {
        return service.create(eventId, request);
    }

    @PutMapping("/{ticketId}")
    @Operation(summary = "Modifier une catégorie de tickets")
    public EventTicketResponse update(@PathVariable UUID eventId, @PathVariable UUID ticketId,
                                      @Valid @RequestBody EventTicketRequest request) {
        return service.update(eventId, ticketId, request);
    }

    @DeleteMapping("/{ticketId}")
    @Operation(summary = "Supprimer une catégorie de tickets inutilisée")
    public ResponseEntity<Void> delete(@PathVariable UUID eventId, @PathVariable UUID ticketId) {
        service.delete(eventId, ticketId);
        return ResponseEntity.noContent().build();
    }
}
