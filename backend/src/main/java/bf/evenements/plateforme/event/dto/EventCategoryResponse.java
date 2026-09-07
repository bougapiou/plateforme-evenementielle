package bf.evenements.plateforme.event.dto;

import bf.evenements.plateforme.event.EventCategory;
import java.util.UUID;

public record EventCategoryResponse(
        UUID id,
        String nom,
        String slug,
        String description,
        String icone,
        boolean actif,
        int ordre) {

    public static EventCategoryResponse from(EventCategory c) {
        return new EventCategoryResponse(c.getId(), c.getNom(), c.getSlug(), c.getDescription(),
                c.getIcone(), c.isActif(), c.getOrdre());
    }
}
