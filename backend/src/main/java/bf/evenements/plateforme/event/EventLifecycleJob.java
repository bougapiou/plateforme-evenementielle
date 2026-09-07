package bf.evenements.plateforme.event;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Advances event states based on their scheduled dates:
 * published / open / closed events move to {@code EN_COURS} once started, and
 * {@code EN_COURS} events to {@code TERMINE} once finished.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventLifecycleJob {

    private final EventRepository eventRepository;

    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void advance() {
        Instant now = Instant.now();

        Specification<Event> toStart = Specification.allOf(
                EventSpecifications.statusIn(List.of(EventStatus.PUBLIE,
                        EventStatus.INSCRIPTIONS_OUVERTES, EventStatus.INSCRIPTIONS_FERMEES)),
                (root, q, cb) -> cb.lessThanOrEqualTo(root.get("dateDebut"), now),
                (root, q, cb) -> cb.greaterThan(root.get("dateFin"), now));
        List<Event> starting = eventRepository.findAll(toStart);
        starting.forEach(e -> e.setStatut(EventStatus.EN_COURS));

        Specification<Event> toFinish = Specification.allOf(
                EventSpecifications.statusIn(List.of(EventStatus.EN_COURS,
                        EventStatus.INSCRIPTIONS_OUVERTES, EventStatus.INSCRIPTIONS_FERMEES,
                        EventStatus.PUBLIE)),
                (root, q, cb) -> cb.lessThanOrEqualTo(root.get("dateFin"), now));
        List<Event> finishing = eventRepository.findAll(toFinish);
        finishing.forEach(e -> e.setStatut(EventStatus.TERMINE));

        if (!starting.isEmpty() || !finishing.isEmpty()) {
            log.info("Cycle de vie événements : {} en cours, {} terminés",
                    starting.size(), finishing.size());
        }
    }
}
