package bf.evenements.plateforme.sensor;

import bf.evenements.plateforme.checkin.CheckinDirection;
import bf.evenements.plateforme.rbac.Permissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Capteurs de comptage")
public class SensorController {

    private final CapteurService capteurService;

    public record CapteurRequest(@NotBlank @Size(max = 120) String nom) {
    }

    // --- device endpoints (authenticated by X-Sensor-Key, no JWT) ---
    // No request body on purpose: a microcontroller only has to POST, optionally with ?count=N.

    @PostMapping("/sensors/entry")
    @Operation(summary = "Capteur : enregistrer des ENTRÉES (en-tête X-Sensor-Key, ?count=N optionnel)")
    public CapteurService.SensorReport entry(
            @RequestHeader(value = "X-Sensor-Key", required = false) String key,
            @RequestParam(defaultValue = "1") int count) {
        return capteurService.record(key, CheckinDirection.ENTREE, count);
    }

    @PostMapping("/sensors/exit")
    @Operation(summary = "Capteur : enregistrer des SORTIES (en-tête X-Sensor-Key, ?count=N optionnel)")
    public CapteurService.SensorReport exit(
            @RequestHeader(value = "X-Sensor-Key", required = false) String key,
            @RequestParam(defaultValue = "1") int count) {
        return capteurService.record(key, CheckinDirection.SORTIE, count);
    }

    // --- management (organiser / admin) ---

    @GetMapping("/events/{eventId}/sensors")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_UPDATE + "')")
    @Operation(summary = "Capteurs de comptage d'un événement")
    public List<CapteurService.CapteurView> list(@PathVariable UUID eventId) {
        return capteurService.list(eventId);
    }

    @PostMapping("/events/{eventId}/sensors")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_UPDATE + "')")
    @Operation(summary = "Créer un capteur — la clé n'est affichée qu'une seule fois")
    public CapteurService.CapteurCreated create(@PathVariable UUID eventId,
                                                @Valid @RequestBody CapteurRequest request) {
        return capteurService.create(eventId, request.nom());
    }

    @DeleteMapping("/events/{eventId}/sensors/{capteurId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_UPDATE + "')")
    @Operation(summary = "Révoquer un capteur (sa clé ne fonctionne plus)")
    public void revoke(@PathVariable UUID eventId, @PathVariable UUID capteurId) {
        capteurService.revoke(eventId, capteurId);
    }
}
