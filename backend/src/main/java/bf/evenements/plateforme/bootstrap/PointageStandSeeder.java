package bf.evenements.plateforme.bootstrap;

import bf.evenements.plateforme.pointage.PointageStand;
import bf.evenements.plateforme.pointage.PointageStandRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds the ministries as pointage stands on first startup (module Pointage des visiteurs). */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointageStandSeeder {

    private final PointageStandRepository repository;

    private static final List<String> MINISTERES = List.of(
            "Ministère d’État, Ministère de la Guerre et de la Défense patriotique",
            "Ministère d’État, Ministère de l’Administration territoriale et de la Mobilité",
            "Ministère d’État, Ministère de l’Agriculture, de l’Eau, des Ressources animales et halieutiques",
            "Ministère de l’Économie et des Finances",
            "Ministère de la Sécurité",
            "Ministère des Affaires étrangères",
            "Ministère des Serviteurs du Peuple",
            "Ministère de la Communication, de la Culture, des Arts et du Tourisme",
            "Ministère de la Famille et de la Solidarité",
            "Ministère de la Justice",
            "Ministère de la Santé",
            "Ministère de la Transition digitale, des Postes et des Communications électroniques",
            "Ministère de l’Industrie, du Commerce et de l’Artisanat",
            "Ministère de la Construction de la Patrie",
            "Ministère de l’Énergie, des Mines et des Carrières",
            "Ministère de l’Enseignement de base, de l’Alphabétisation et de la Promotion des Langues nationales",
            "Ministère de l’Enseignement secondaire, de la Formation professionnelle et technique",
            "Ministère de l’Enseignement supérieur, de la Recherche et de l’Innovation",
            "Ministère des Sports, de la Jeunesse et de l’Emploi");

    @EventListener(ApplicationReadyEvent.class)
    @Order(4)
    @Transactional
    public void seed() {
        if (repository.count() > 0) {
            return;
        }
        int ordre = 0;
        for (String nom : MINISTERES) {
            repository.save(new PointageStand(nom, ordre++));
        }
        log.info("Stands de pointage initialisés : {}", MINISTERES.size());
    }
}
