package bf.evenements.plateforme.event;

import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.event.dto.ActivityResponse;
import bf.evenements.plateforme.event.dto.EventPublicResponse;
import bf.evenements.plateforme.event.dto.EventSummary;
import bf.evenements.plateforme.event.dto.PartnerResponse;
import bf.evenements.plateforme.event.dto.SpeakerResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only, unauthenticated access to published events. */
@Service
@RequiredArgsConstructor
public class PublicEventService {

    private final EventRepository eventRepository;
    private final EventActivityRepository activityRepository;
    private final SpeakerRepository speakerRepository;
    private final PartnerRepository partnerRepository;

    @Transactional(readOnly = true)
    public PageResponse<EventSummary> search(@Nullable String search, @Nullable String categorySlug,
                                             @Nullable String ville, @Nullable Instant from,
                                             @Nullable Instant to, Pageable pageable) {
        Specification<Event> spec = Specification.allOf(
                EventSpecifications.statusIn(java.util.List.of(
                        EventStatus.PUBLIE, EventStatus.INSCRIPTIONS_OUVERTES,
                        EventStatus.INSCRIPTIONS_FERMEES, EventStatus.EN_COURS, EventStatus.TERMINE)),
                EventSpecifications.textSearch(search),
                EventSpecifications.categorySlug(categorySlug),
                EventSpecifications.inCity(ville),
                EventSpecifications.startsAfter(from),
                EventSpecifications.startsBefore(to));
        return PageResponse.of(eventRepository.findAll(spec, pageable), EventSummary::from);
    }

    @Transactional(readOnly = true)
    public EventPublicResponse getBySlug(String slug) {
        Event event = eventRepository.findBySlug(slug)
                .filter(e -> e.getStatut().isPubliclyVisible())
                .orElseThrow(() -> new ResourceNotFoundException("Événement introuvable : " + slug));

        var programme = activityRepository.findByEventIdOrderByDateDebutAscOrdreAsc(event.getId())
                .stream().map(ActivityResponse::from).toList();
        var speakers = speakerRepository.findByEventIdOrderByOrdreAscNomAsc(event.getId())
                .stream().map(SpeakerResponse::from).toList();
        var partners = partnerRepository.findByEventIdOrderByOrdreAscNomAsc(event.getId())
                .stream().map(PartnerResponse::from).toList();
        return EventPublicResponse.from(event, programme, speakers, partners);
    }
}
