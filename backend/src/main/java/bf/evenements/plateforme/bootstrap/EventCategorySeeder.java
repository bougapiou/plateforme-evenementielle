package bf.evenements.plateforme.bootstrap;

import bf.evenements.plateforme.common.web.Slugs;
import bf.evenements.plateforme.event.EventCategory;
import bf.evenements.plateforme.event.EventCategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds the default event categories on first startup. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventCategorySeeder {

    private final EventCategoryRepository repository;

    private static final List<String[]> DEFAULTS = List.of(
            new String[] {"Salon", "briefcase"},
            new String[] {"Foire commerciale", "store"},
            new String[] {"Festival", "music"},
            new String[] {"Forum", "users"},
            new String[] {"Conférence", "mic"},
            new String[] {"Exposition", "image"},
            new String[] {"Séminaire", "presentation"},
            new String[] {"Atelier", "tool"},
            new String[] {"Semaine thématique", "calendar"},
            new String[] {"Événement institutionnel", "building"});

    @EventListener(ApplicationReadyEvent.class)
    @Order(3)
    @Transactional
    public void seed() {
        if (repository.count() > 0) {
            return;
        }
        int ordre = 0;
        for (String[] def : DEFAULTS) {
            repository.save(new EventCategory(def[0], Slugs.slugify(def[0]), null, def[1], ordre++));
        }
        log.info("Catégories d'événements initialisées : {}", DEFAULTS.size());
    }
}
