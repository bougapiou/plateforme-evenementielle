package bf.evenements.plateforme.event;

import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.web.Slugs;
import bf.evenements.plateforme.event.dto.EventCategoryRequest;
import bf.evenements.plateforme.event.dto.EventCategoryResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventCategoryService {

    private final EventCategoryRepository repository;
    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public List<EventCategoryResponse> listAll() {
        return repository.findAllByOrderByOrdreAscNomAsc().stream()
                .map(EventCategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EventCategoryResponse> listActive() {
        return repository.findByActifTrueOrderByOrdreAscNomAsc().stream()
                .map(EventCategoryResponse::from).toList();
    }

    @Transactional
    public EventCategoryResponse create(EventCategoryRequest request) {
        EventCategory category = new EventCategory();
        apply(category, request);
        category.setSlug(Slugs.uniqueSlug(request.nom(), s -> !repository.existsBySlug(s)));
        return EventCategoryResponse.from(repository.save(category));
    }

    @Transactional
    public EventCategoryResponse update(UUID id, EventCategoryRequest request) {
        EventCategory category = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Catégorie", id));
        apply(category, request);
        return EventCategoryResponse.from(category);
    }

    @Transactional
    public void delete(UUID id) {
        EventCategory category = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Catégorie", id));
        if (eventRepository.countByCategoryId(id) > 0) {
            throw new BusinessException("CATEGORY_IN_USE",
                    "Cette catégorie est utilisée par des événements ; désactivez-la plutôt.");
        }
        repository.delete(category);
    }

    private void apply(EventCategory c, EventCategoryRequest r) {
        c.setNom(r.nom().trim());
        c.setDescription(r.description());
        c.setIcone(r.icone());
        if (r.ordre() != null) {
            c.setOrdre(r.ordre());
        }
        if (r.actif() != null) {
            c.setActif(r.actif());
        }
    }
}
