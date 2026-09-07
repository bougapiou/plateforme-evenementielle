package bf.evenements.plateforme.rbac;

import bf.evenements.plateforme.rbac.dto.RoleRequest;
import bf.evenements.plateforme.rbac.dto.RoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Roles & permissions")
@PreAuthorize("hasAuthority('" + Permissions.ROLE_MANAGE + "')")
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/roles")
    @Operation(summary = "Lister les roles")
    public List<RoleResponse> listRoles() {
        return roleService.listRoles();
    }

    @GetMapping("/permissions")
    @Operation(summary = "Lister les permissions disponibles")
    public List<Map<String, String>> listPermissions() {
        return roleService.listPermissions().stream()
                .map(p -> Map.of("name", p.getName(),
                        "description", p.getDescription() == null ? "" : p.getDescription()))
                .toList();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creer un role personnalise")
    public RoleResponse create(@Valid @RequestBody RoleRequest request) {
        return roleService.create(request);
    }

    @PutMapping("/roles/{id}/permissions")
    @Operation(summary = "Remplacer les permissions d'un role")
    public RoleResponse updatePermissions(@PathVariable UUID id,
                                          @Valid @RequestBody RoleRequest request) {
        return roleService.updatePermissions(id, request.permissions());
    }
}
