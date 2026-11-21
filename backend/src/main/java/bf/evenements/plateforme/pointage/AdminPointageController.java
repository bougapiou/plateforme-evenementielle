package bf.evenements.plateforme.pointage;

import bf.evenements.plateforme.pointage.dto.PointageStandRequest;
import bf.evenements.plateforme.pointage.dto.PointageStandResponse;
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

/** Administration of the choices (stands) offered on the public "Pointage des visiteurs" page. */
@RestController
@RequestMapping("/api/admin/pointage/stands")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permissions.POINTAGE_MANAGE + "')")
@Tag(name = "Pointage des visiteurs (admin)")
public class AdminPointageController {

    private final PointageService service;

    @GetMapping
    @Operation(summary = "Lister les choix du pointage")
    public List<PointageStandResponse> list() {
        return service.listStands();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter un choix")
    public PointageStandResponse create(@Valid @RequestBody PointageStandRequest request) {
        return service.createStand(request);
    }

    @PutMapping("/{standId}")
    @Operation(summary = "Renommer un choix")
    public PointageStandResponse rename(@PathVariable UUID standId,
                                        @Valid @RequestBody PointageStandRequest request) {
        return service.renameStand(standId, request);
    }

    @DeleteMapping("/{standId}")
    @Operation(summary = "Supprimer un choix (et ses passages)")
    public ResponseEntity<Void> delete(@PathVariable UUID standId) {
        service.deleteStand(standId);
        return ResponseEntity.noContent().build();
    }
}
