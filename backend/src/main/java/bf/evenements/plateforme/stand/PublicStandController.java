package bf.evenements.plateforme.stand;

import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventRepository;
import bf.evenements.plateforme.stand.dto.StandResponse;
import bf.evenements.plateforme.stand.dto.StandTypeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/events/{slug}")
@RequiredArgsConstructor
@Tag(name = "Site public")
public class PublicStandController {

    private final EventRepository eventRepository;
    private final StandTypeService standTypeService;
    private final StandService standService;

    @GetMapping("/stand-types")
    @Operation(summary = "Types de stands d'un événement publié")
    public List<StandTypeResponse> types(@PathVariable String slug) {
        return standTypeService.listPublic(resolve(slug).getId());
    }

    @GetMapping("/stands")
    @Operation(summary = "Plan des stands avec disponibilité")
    public List<StandResponse> stands(@PathVariable String slug) {
        return standService.listPublic(resolve(slug).getId());
    }

    private Event resolve(String slug) {
        return eventRepository.findBySlug(slug)
                .filter(e -> e.getStatut().isPubliclyVisible())
                .orElseThrow(() -> new ResourceNotFoundException("Événement introuvable : " + slug));
    }
}
