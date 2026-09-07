package bf.evenements.plateforme.user;

import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.rbac.Permissions;
import bf.evenements.plateforme.user.dto.ChangePasswordRequest;
import bf.evenements.plateforme.user.dto.UpdateProfileRequest;
import bf.evenements.plateforme.user.dto.UpdateUserRolesRequest;
import bf.evenements.plateforme.user.dto.UpdateUserStatusRequest;
import bf.evenements.plateforme.user.dto.UserResponse;
import bf.evenements.plateforme.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Utilisateurs")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Profil de l'utilisateur connecte")
    public UserResponse me() {
        return userService.me();
    }

    @PatchMapping("/me")
    @Operation(summary = "Mettre a jour son profil")
    public UserResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(request);
    }

    @PostMapping("/me/password")
    @Operation(summary = "Changer son mot de passe")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.USER_READ + "')")
    @Operation(summary = "Lister les utilisateurs (admin)")
    public PageResponse<UserSummary> list(@RequestParam(required = false) String search,
                                          @RequestParam(required = false) UserType type,
                                          @RequestParam(required = false) UserStatus status,
                                          @PageableDefault(size = 20) Pageable pageable) {
        return userService.search(search, type, status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.USER_READ + "')")
    @Operation(summary = "Detail d'un utilisateur (admin)")
    public UserResponse get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + Permissions.USER_MANAGE + "')")
    @Operation(summary = "Activer / desactiver un utilisateur (admin)")
    public UserResponse updateStatus(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateUserStatusRequest request) {
        return userService.updateStatus(id, request.status());
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('" + Permissions.ROLE_MANAGE + "')")
    @Operation(summary = "Remplacer les roles d'un utilisateur (admin)")
    public UserResponse updateRoles(@PathVariable UUID id,
                                    @Valid @RequestBody UpdateUserRolesRequest request) {
        return userService.updateRoles(id, request.roles());
    }
}
