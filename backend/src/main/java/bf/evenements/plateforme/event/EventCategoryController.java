package bf.evenements.plateforme.event;

import bf.evenements.plateforme.event.dto.EventCategoryRequest;
import bf.evenements.plateforme.event.dto.EventCategoryResponse;
import bf.evenements.plateforme.rbac.Permissions;
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
@RequestMapping("/api/event-categories")
@RequiredArgsConstructor
@Tag(name = "Catégories d'événements")
public class EventCategoryController {

    private final EventCategoryService service;

    @GetMapping
    @Operation(summary = "Lister les catégories actives")
    public List<EventCategoryResponse> listActive() {
        return service.listActive();
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_CATEGORY_MANAGE + "')")
    @Operation(summary = "Lister toutes les catégories (admin)")
    public List<EventCategoryResponse> listAll() {
        return service.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_CATEGORY_MANAGE + "')")
    @Operation(summary = "Créer une catégorie (admin)")
    public EventCategoryResponse create(@Valid @RequestBody EventCategoryRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_CATEGORY_MANAGE + "')")
    @Operation(summary = "Modifier une catégorie (admin)")
    public EventCategoryResponse update(@PathVariable UUID id,
                                        @Valid @RequestBody EventCategoryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_CATEGORY_MANAGE + "')")
    @Operation(summary = "Supprimer une catégorie inutilisée (admin)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
