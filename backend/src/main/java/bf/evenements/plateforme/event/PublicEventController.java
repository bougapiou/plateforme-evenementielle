package bf.evenements.plateforme.event;

import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.event.dto.EventCategoryResponse;
import bf.evenements.plateforme.event.dto.EventPublicResponse;
import bf.evenements.plateforme.event.dto.EventSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public catalogue of published events. No authentication required. */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Site public")
public class PublicEventController {

    private final PublicEventService publicEventService;
    private final EventCategoryService categoryService;

    @GetMapping("/events")
    @Operation(summary = "Rechercher les événements publiés (filtres : catégorie, ville, dates)")
    public PageResponse<EventSummary> events(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String categorie,
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant au,
            @PageableDefault(size = 12, sort = "dateDebut", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return publicEventService.search(search, categorie, ville, du, au, pageable);
    }

    @GetMapping("/events/{slug}")
    @Operation(summary = "Page publique d'un événement")
    public EventPublicResponse event(@PathVariable String slug) {
        return publicEventService.getBySlug(slug);
    }

    @GetMapping("/event-categories")
    @Operation(summary = "Catégories d'événements actives")
    public List<EventCategoryResponse> categories() {
        return categoryService.listActive();
    }
}
