package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventRepository;
import bf.evenements.plateforme.ticket.dto.EventTicketResponse;
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
public class PublicTicketController {

    private final EventRepository eventRepository;
    private final EventTicketService ticketService;

    @GetMapping("/tickets")
    @Operation(summary = "Tarifs / catégories de tickets d'un événement publié")
    public List<EventTicketResponse> tickets(@PathVariable String slug) {
        Event event = eventRepository.findBySlug(slug)
                .filter(e -> e.getStatut().isPubliclyVisible())
                .orElseThrow(() -> new ResourceNotFoundException("Événement introuvable : " + slug));
        return ticketService.listPublic(event.getId());
    }
}
