package bf.evenements.plateforme.auth;

import bf.evenements.plateforme.auth.dto.AuthResponse;
import bf.evenements.plateforme.auth.dto.CompleteRegistrationRequest;
import bf.evenements.plateforme.auth.dto.GuestSessionRequest;
import bf.evenements.plateforme.auth.dto.LoginRequest;
import bf.evenements.plateforme.auth.dto.RefreshRequest;
import bf.evenements.plateforme.auth.dto.RegisterRequest;
import bf.evenements.plateforme.common.web.HttpUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creer un compte particulier ou structure")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request,
                                 HttpServletRequest http) {
        return authService.register(request, HttpUtils.clientIp(http));
    }

    @PostMapping("/guest")
    @Operation(summary = "Ouvrir une session invité (achat / inscription sans compte)")
    public AuthResponse guest(@Valid @RequestBody GuestSessionRequest request,
                              HttpServletRequest http) {
        return authService.guestSession(request, HttpUtils.clientIp(http));
    }

    @PostMapping("/complete")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Transformer sa session invité en compte (choix d'un mot de passe)")
    public AuthResponse complete(@Valid @RequestBody CompleteRegistrationRequest request) {
        return authService.completeRegistration(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Se connecter et obtenir des jetons")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, HttpUtils.clientIp(http));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renouveler le jeton d'acces (rotation du refresh token)")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoquer le refresh token courant")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
