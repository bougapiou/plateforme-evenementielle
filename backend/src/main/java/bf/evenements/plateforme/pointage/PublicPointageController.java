package bf.evenements.plateforme.pointage;

import bf.evenements.plateforme.pointage.dto.PointageStandResponse;
import bf.evenements.plateforme.pointage.dto.PointageStandStatResponse;
import bf.evenements.plateforme.pointage.dto.PointageStatsResponse;
import bf.evenements.plateforme.pointage.dto.SignalerPointagePassagesRequest;
import bf.evenements.plateforme.pointage.dto.SignalerPointagePassagesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
 * Pointage des visiteurs : module public, indépendant des événements — aucune connexion
 * requise. Voir SecurityConfig pour l'exemption POST explicite sur /passages (tout le reste
 * ici est un simple GET public).
 */
@RestController
@RequestMapping("/api/public/pointage")
@RequiredArgsConstructor
@Tag(name = "Site public")
public class PublicPointageController {

    private final PointageService service;

    @GetMapping("/stands")
    @Operation(summary = "Liste des stands du module Pointage des visiteurs")
    public List<PointageStandResponse> stands() {
        return service.listStands();
    }

    @PostMapping("/passages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Signaler son passage à plusieurs stands en une fois (identité facultative)")
    public SignalerPointagePassagesResponse signaler(@Valid @RequestBody SignalerPointagePassagesRequest request) {
        return new SignalerPointagePassagesResponse(service.signalerPlusieurs(request));
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques globales du module Pointage des visiteurs")
    public PointageStatsResponse stats() {
        return service.stats();
    }

    @GetMapping("/stands/{standId}/stats")
    @Operation(summary = "Statistiques d'un seul stand")
    public PointageStandStatResponse standStats(@PathVariable UUID standId) {
        return service.standStats(standId);
    }
}
