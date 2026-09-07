package bf.evenements.plateforme.structure;

import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.rbac.Permissions;
import bf.evenements.plateforme.structure.dto.AddMemberRequest;
import bf.evenements.plateforme.structure.dto.StructureMemberResponse;
import bf.evenements.plateforme.structure.dto.StructureRequest;
import bf.evenements.plateforme.structure.dto.StructureResponse;
import bf.evenements.plateforme.structure.dto.StructureSummary;
import bf.evenements.plateforme.structure.dto.UpdateStructureStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/structures")
@RequiredArgsConstructor
@Tag(name = "Structures")
public class StructureController {

    private final StructureService structureService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une structure (le créateur en devient propriétaire)")
    public StructureResponse create(@Valid @RequestBody StructureRequest request) {
        return structureService.create(request);
    }

    @GetMapping("/mine")
    @Operation(summary = "Mes structures")
    public List<StructureSummary> mine() {
        return structureService.mine();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une structure (membre ou administrateur)")
    public StructureResponse get(@PathVariable UUID id) {
        return structureService.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour une structure")
    public StructureResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody StructureRequest request) {
        return structureService.update(id, request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.STRUCTURE_READ + "')")
    @Operation(summary = "Lister les structures (admin)")
    public PageResponse<StructureSummary> list(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) StructureStatus statut,
                                               @RequestParam(required = false) StructureType type,
                                               @PageableDefault(size = 20) Pageable pageable) {
        return structureService.adminList(search, statut, type, pageable);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + Permissions.STRUCTURE_MANAGE + "')")
    @Operation(summary = "Vérifier / suspendre une structure (admin)")
    public StructureResponse updateStatus(@PathVariable UUID id,
                                          @Valid @RequestBody UpdateStructureStatusRequest request) {
        return structureService.updateStatus(id, request.statut());
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "Lister les représentants d'une structure")
    public List<StructureMemberResponse> members(@PathVariable UUID id) {
        return structureService.listMembers(id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter un représentant (utilisateur existant)")
    public StructureMemberResponse addMember(@PathVariable UUID id,
                                             @Valid @RequestBody AddMemberRequest request) {
        return structureService.addMember(id, request);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Operation(summary = "Retirer un représentant")
    public ResponseEntity<Void> removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        structureService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }
}
