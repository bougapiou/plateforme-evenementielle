package bf.evenements.plateforme.stand;

import bf.evenements.plateforme.rbac.Permissions;
import bf.evenements.plateforme.stand.dto.StandResponse;
import bf.evenements.plateforme.stand.dto.StandTypeRequest;
import bf.evenements.plateforme.stand.dto.StandTypeResponse;
import bf.evenements.plateforme.stand.dto.UpdateStandRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}")
@RequiredArgsConstructor
@Tag(name = "Stands — configuration")
@PreAuthorize("hasAuthority('" + Permissions.STAND_MANAGE + "')")
public class StandTypeController {

    private final StandTypeService standTypeService;
    private final StandService standService;

    @GetMapping("/stand-types")
    @Operation(summary = "Lister les types de stands (organisateur)")
    public List<StandTypeResponse> types(@PathVariable UUID eventId) {
        return standTypeService.list(eventId);
    }

    @PostMapping("/stand-types")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un type de stand (génère les stands)")
    public StandTypeResponse createType(@PathVariable UUID eventId,
                                        @Valid @RequestBody StandTypeRequest request) {
        return standTypeService.create(eventId, request);
    }

    @PutMapping("/stand-types/{typeId}")
    @Operation(summary = "Modifier un type de stand (ajuste le nombre de stands)")
    public StandTypeResponse updateType(@PathVariable UUID eventId, @PathVariable UUID typeId,
                                        @Valid @RequestBody StandTypeRequest request) {
        return standTypeService.update(eventId, typeId, request);
    }

    @DeleteMapping("/stand-types/{typeId}")
    @Operation(summary = "Supprimer un type de stand sans réservation")
    public ResponseEntity<Void> deleteType(@PathVariable UUID eventId, @PathVariable UUID typeId) {
        standTypeService.delete(eventId, typeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stands")
    @Operation(summary = "Plan des stands (organisateur)")
    public List<StandResponse> stands(@PathVariable UUID eventId) {
        return standService.listForManagement(eventId);
    }

    @PatchMapping("/stands/{standId}")
    @Operation(summary = "Positionner / (dés)activer un stand")
    public StandResponse updateStand(@PathVariable UUID eventId, @PathVariable UUID standId,
                                     @RequestBody UpdateStandRequest request) {
        return standService.update(eventId, standId, request);
    }
}
