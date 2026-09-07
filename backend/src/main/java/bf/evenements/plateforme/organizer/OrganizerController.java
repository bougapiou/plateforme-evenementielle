package bf.evenements.plateforme.organizer;

import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.organizer.dto.OrganizerApplyRequest;
import bf.evenements.plateforme.organizer.dto.OrganizerResponse;
import bf.evenements.plateforme.organizer.dto.OrganizerUpdateRequest;
import bf.evenements.plateforme.rbac.Permissions;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizers")
@RequiredArgsConstructor
@Tag(name = "Organisateurs")
public class OrganizerController {

    private final OrganizerService organizerService;

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Demander à devenir organisateur")
    public OrganizerResponse apply(@Valid @RequestBody OrganizerApplyRequest request) {
        return organizerService.apply(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Mon profil organisateur")
    public OrganizerResponse me() {
        return organizerService.me();
    }

    @PutMapping("/me")
    @Operation(summary = "Mettre à jour mon profil organisateur")
    public OrganizerResponse updateMe(@Valid @RequestBody OrganizerUpdateRequest request) {
        return organizerService.updateMe(request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.ORGANIZER_MANAGE + "')")
    @Operation(summary = "Lister les organisateurs / demandes (admin)")
    public PageResponse<OrganizerResponse> list(@RequestParam(required = false) OrganizerStatus statut,
                                                @PageableDefault(size = 20) Pageable pageable) {
        return organizerService.adminList(statut, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.ORGANIZER_MANAGE + "')")
    @Operation(summary = "Détail d'un organisateur (admin)")
    public OrganizerResponse get(@PathVariable UUID id) {
        return organizerService.get(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('" + Permissions.ORGANIZER_MANAGE + "')")
    @Operation(summary = "Approuver un organisateur (admin)")
    public OrganizerResponse approve(@PathVariable UUID id) {
        return organizerService.approve(id);
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('" + Permissions.ORGANIZER_MANAGE + "')")
    @Operation(summary = "Suspendre un organisateur (admin)")
    public OrganizerResponse suspend(@PathVariable UUID id) {
        return organizerService.suspend(id);
    }
}
