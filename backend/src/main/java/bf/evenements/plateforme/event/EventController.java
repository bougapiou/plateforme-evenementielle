package bf.evenements.plateforme.event;

import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.event.dto.EventRequest;
import bf.evenements.plateforme.event.dto.EventResponse;
import bf.evenements.plateforme.event.dto.EventSummary;
import bf.evenements.plateforme.event.dto.RejectRequest;
import bf.evenements.plateforme.rbac.Permissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Événements")
public class EventController {

    private final EventService eventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_CREATE + "')")
    @Operation(summary = "Créer un événement (brouillon)")
    public EventResponse create(@Valid @RequestBody EventRequest request) {
        return eventService.create(request);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_READ + "')")
    @Operation(summary = "Mes événements (organisateur)")
    public PageResponse<EventSummary> mine(@RequestParam(required = false) EventStatus statut,
                                           @PageableDefault(size = 20, sort = "dateDebut",
                                                   direction = Sort.Direction.DESC) Pageable pageable) {
        return eventService.listMine(statut, pageable);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_VALIDATE + "')")
    @Operation(summary = "Tous les événements (admin) — validation / supervision")
    public PageResponse<EventSummary> adminList(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) EventStatus statut,
                                               @RequestParam(required = false) UUID categoryId,
                                               @PageableDefault(size = 20, sort = "createdAt",
                                                       direction = Sort.Direction.DESC) Pageable pageable) {
        return eventService.adminList(search, statut, categoryId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_READ + "')")
    @Operation(summary = "Détail d'un événement (organisateur / admin)")
    public EventResponse get(@PathVariable UUID id) {
        return eventService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_UPDATE + "')")
    @Operation(summary = "Modifier un événement")
    public EventResponse update(@PathVariable UUID id, @Valid @RequestBody EventRequest request) {
        return eventService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_DELETE + "')")
    @Operation(summary = "Supprimer un brouillon")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        eventService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- workflow (organiser) ---

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_UPDATE + "')")
    @Operation(summary = "Soumettre l'événement à validation")
    public EventResponse submit(@PathVariable UUID id) {
        return eventService.submit(id);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_PUBLISH + "')")
    @Operation(summary = "Publier un événement validé")
    public EventResponse publish(@PathVariable UUID id) {
        return eventService.publish(id);
    }

    @PostMapping("/{id}/open-registrations")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_PUBLISH + "')")
    @Operation(summary = "Ouvrir les inscriptions")
    public EventResponse openRegistrations(@PathVariable UUID id) {
        return eventService.openRegistrations(id);
    }

    @PostMapping("/{id}/close-registrations")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_PUBLISH + "')")
    @Operation(summary = "Fermer les inscriptions")
    public EventResponse closeRegistrations(@PathVariable UUID id) {
        return eventService.closeRegistrations(id);
    }

    // --- workflow (admin) ---

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_VALIDATE + "')")
    @Operation(summary = "Valider un événement soumis (admin)")
    public EventResponse validate(@PathVariable UUID id) {
        return eventService.validate(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_VALIDATE + "')")
    @Operation(summary = "Refuser un événement soumis (admin)")
    public EventResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        return eventService.reject(id, request.motif());
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_VALIDATE + "')")
    @Operation(summary = "Suspendre un événement (admin)")
    public EventResponse suspend(@PathVariable UUID id) {
        return eventService.suspend(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_VALIDATE + "')")
    @Operation(summary = "Annuler un événement (admin)")
    public EventResponse cancel(@PathVariable UUID id) {
        return eventService.cancel(id);
    }
}
