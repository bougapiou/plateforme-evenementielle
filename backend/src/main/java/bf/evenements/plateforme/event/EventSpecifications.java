package bf.evenements.plateforme.event;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class EventSpecifications {

    private EventSpecifications() {
    }

    public static Specification<Event> ownedByUser(UUID userId) {
        return (root, query, cb) ->
                cb.equal(root.get("organizer").get("user").get("id"), userId);
    }

    public static Specification<Event> hasStatus(EventStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("statut"), status);
    }

    public static Specification<Event> statusIn(Collection<EventStatus> statuses) {
        return (root, query, cb) -> root.get("statut").in(statuses);
    }

    /** Matches events whose id is in the given collection; matches nothing if empty. */
    public static Specification<Event> idIn(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("id").in(ids);
    }

    public static Specification<Event> hasCategory(UUID categoryId) {
        return categoryId == null ? null
                : (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Event> categorySlug(String slug) {
        return (slug == null || slug.isBlank()) ? null
                : (root, query, cb) -> cb.equal(root.get("category").get("slug"), slug);
    }

    public static Specification<Event> inCity(String ville) {
        return (ville == null || ville.isBlank()) ? null
                : (root, query, cb) -> cb.equal(cb.lower(root.get("ville")), ville.trim().toLowerCase());
    }

    public static Specification<Event> startsAfter(Instant from) {
        return from == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dateDebut"), from);
    }

    public static Specification<Event> startsBefore(Instant to) {
        return to == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("dateDebut"), to);
    }

    public static Specification<Event> textSearch(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String like = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("nom")), like),
                cb.like(cb.lower(root.get("sigle")), like),
                cb.like(cb.lower(root.get("ville")), like),
                cb.like(cb.lower(root.get("descriptionCourte")), like));
    }
}
