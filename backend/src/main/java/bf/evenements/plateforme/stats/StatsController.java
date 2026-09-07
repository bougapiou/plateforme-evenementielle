package bf.evenements.plateforme.stats;

import bf.evenements.plateforme.rbac.Permissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Tag(name = "Statistiques")
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/admin/overview")
    @PreAuthorize("hasAuthority('" + Permissions.STATS_GLOBAL_READ + "')")
    @Operation(summary = "Statistiques globales (admin)")
    public Map<String, Object> adminOverview() {
        return statsService.adminOverview();
    }

    @GetMapping("/organizer/overview")
    @PreAuthorize("hasAuthority('" + Permissions.STATS_OWN_READ + "')")
    @Operation(summary = "Synthèse de mes événements (organisateur)")
    public Map<String, Object> organizerOverview() {
        return statsService.organizerOverview();
    }

    @GetMapping("/events/{eventId}")
    @PreAuthorize("hasAuthority('" + Permissions.STATS_OWN_READ + "') or hasAuthority('"
            + Permissions.STATS_GLOBAL_READ + "')")
    @Operation(summary = "Statistiques d'un événement")
    public Map<String, Object> eventStats(@PathVariable UUID eventId) {
        return statsService.eventStats(eventId);
    }

    @GetMapping("/events/{eventId}/series")
    @PreAuthorize("hasAuthority('" + Permissions.STATS_OWN_READ + "') or hasAuthority('"
            + Permissions.STATS_GLOBAL_READ + "')")
    @Operation(summary = "Séries temporelles d'un événement (graphiques)")
    public Map<String, Object> eventSeries(@PathVariable UUID eventId) {
        return statsService.eventSeries(eventId);
    }
}
