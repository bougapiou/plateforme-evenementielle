package bf.evenements.plateforme.standvisit;

import bf.evenements.plateforme.standvisit.dto.FrequentationResponse;
import bf.evenements.plateforme.standvisit.dto.SignalerPassageRequest;
import bf.evenements.plateforme.standvisit.dto.StandInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fréquentation des stands : one public page per stand where a visitor signals their passage, and
 * a public live ranking per event. No login — see SecurityConfig for the explicit POST exemption
 * (every other route here is a plain public GET).
 */
@RestController
@RequestMapping("/api/public/events/{slug}")
@RequiredArgsConstructor
@Tag(name = "Site public")
public class PublicVisiteStandController {

    private final VisiteStandService service;

    @GetMapping("/stands/{standId}/passage")
    @Operation(summary = "Informations du stand affichées avant de signaler son passage")
    public StandInfoResponse standInfo(@PathVariable String slug, @PathVariable UUID standId) {
        return service.standInfo(slug, standId);
    }

    @PostMapping("/stands/{standId}/passage")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Signaler son passage à un stand (identité facultative)")
    public void signaler(@PathVariable String slug, @PathVariable UUID standId,
            @Valid @RequestBody SignalerPassageRequest request) {
        service.signaler(slug, standId, request);
    }

    @GetMapping("/frequentation")
    @Operation(summary = "Classement public de fréquentation des stands")
    public FrequentationResponse frequentation(@PathVariable String slug) {
        return service.stats(slug);
    }
}
