package bf.evenements.plateforme.bootstrap;

import bf.evenements.plateforme.common.storage.FileStorageService;
import bf.evenements.plateforme.common.storage.StoredFile;
import bf.evenements.plateforme.common.web.Slugs;
import bf.evenements.plateforme.event.ActivityType;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventActivity;
import bf.evenements.plateforme.event.EventActivityRepository;
import bf.evenements.plateforme.event.EventCategory;
import bf.evenements.plateforme.event.EventCategoryRepository;
import bf.evenements.plateforme.event.EventRepository;
import bf.evenements.plateforme.event.EventStatus;
import bf.evenements.plateforme.organizer.Organizer;
import bf.evenements.plateforme.organizer.OrganizerRepository;
import bf.evenements.plateforme.organizer.OrganizerStatus;
import bf.evenements.plateforme.rbac.RoleNames;
import bf.evenements.plateforme.rbac.RoleRepository;
import bf.evenements.plateforme.stand.Stand;
import bf.evenements.plateforme.stand.StandRepository;
import bf.evenements.plateforme.stand.StandType;
import bf.evenements.plateforme.stand.StandTypeRepository;
import bf.evenements.plateforme.ticket.EventTicket;
import bf.evenements.plateforme.ticket.EventTicketRepository;
import bf.evenements.plateforme.ticket.TicketScope;
import bf.evenements.plateforme.user.User;
import bf.evenements.plateforme.user.UserRepository;
import bf.evenements.plateforme.user.UserStatus;
import bf.evenements.plateforme.user.UserType;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Optional demo dataset (SIAO, FESPACO, Semaine du Numérique). Enabled with
 * {@code app.bootstrap.demo-data=true} / {@code DEMO_DATA=true}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder {

    @Value("${app.bootstrap.demo-data:false}")
    private boolean enabled;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizerRepository organizerRepository;
    private final EventCategoryRepository categoryRepository;
    private final EventRepository eventRepository;
    private final EventActivityRepository activityRepository;
    private final EventTicketRepository ticketRepository;
    private final StandTypeRepository standTypeRepository;
    private final StandRepository standRepository;
    private final FileStorageService fileStorage;
    private final PasswordEncoder passwordEncoder;

    private final Map<String, String> imageUrls = new HashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    @Transactional
    public void seed() {
        if (!enabled || eventRepository.existsBySlug("siao-2027")) {
            return;
        }
        Organizer organizer = demoOrganizer();

        // ---- Événements à venir ------------------------------------------------
        Event siao = event("SIAO 2027", "SIAO",
                "Salon International de l'Artisanat de Ouagadougou",
                "foire-commerciale", organizer, "Ouagadougou", "SIEPA - Zone du Bois",
                145, EventStatus.INSCRIPTIONS_OUVERTES, true, false, "siao");
        standTypes(siao);
        tickets(siao,
                new String[] {"Entrée visiteur", "1000", "5000"},
                new String[] {"Pass semaine", "5000", "1000"});

        Event fespaco = event("FESPACO 2027", "FESPACO",
                "Festival Panafricain du Cinéma et de la Télévision de Ouagadougou",
                "festival", organizer, "Ouagadougou", "Ciné Burkina & Canal Olympia",
                200, EventStatus.INSCRIPTIONS_OUVERTES, true, false, "fespaco");
        standTypes(fespaco);
        tickets(fespaco,
                new String[] {"Projection standard", "2000", "3000"},
                new String[] {"Pass festival", "20000", "500"});

        Event sdn = event("Semaine du Numérique 2027", "SDN",
                "Rendez-vous national de la transformation numérique",
                "semaine-thematique", organizer, "Ouagadougou", "Palais des Sports de Ouaga 2000",
                110, EventStatus.INSCRIPTIONS_OUVERTES, false, true, "sdn");
        activity(sdn, "Cérémonie d'ouverture", ActivityType.CEREMONIE, 0, "Grand amphi", "act-ceremonie");
        activity(sdn, "Conférence : IA et services publics", ActivityType.CONFERENCE, 3, "Salle A", "act-conference");
        activity(sdn, "Panel : souveraineté numérique", ActivityType.PANEL, 6, "Salle B", "act-panel");
        activity(sdn, "Networking & startups", ActivityType.NETWORKING, 24, "Hall d'exposition", "act-networking");
        tickets(sdn,
                new String[] {"Standard", "5000", "800"},
                new String[] {"Étudiant", "2000", "500"},
                new String[] {"Professionnel", "15000", "300"});

        // ---- Éditions passées (pour visualiser le rendu) ---------------------
        Event siao24 = event("SIAO 2024", "SIAO",
                "Salon International de l'Artisanat de Ouagadougou — édition 2024",
                "foire-commerciale", organizer, "Ouagadougou", "SIEPA - Zone du Bois",
                -676, EventStatus.TERMINE, true, false, "siao-2024");
        standTypes(siao24);
        tickets(siao24, new String[] {"Entrée visiteur", "1000", "5000"});

        Event fespaco23 = event("FESPACO 2023", "FESPACO",
                "Festival Panafricain du Cinéma et de la Télévision — édition 2023",
                "festival", organizer, "Ouagadougou", "Ciné Burkina & Canal Olympia",
                -1290, EventStatus.TERMINE, false, true, "fespaco-2023");
        activity(fespaco23, "Cérémonie d'ouverture", ActivityType.CEREMONIE, 0, "Ciné Burkina", "act-ceremonie");
        activity(fespaco23, "Projection : Étalon d'or de Yennenga", ActivityType.SPECTACLE, 5, "Canal Olympia", "act-projection");
        activity(fespaco23, "Table ronde : cinéma africain et diffusion", ActivityType.TABLE_RONDE, 26, "CENASA", "act-panel");
        tickets(fespaco23, new String[] {"Pass festival", "20000", "500"});

        Event sdn25 = event("Semaine du Numérique 2025", "SDN",
                "Rendez-vous national de la transformation numérique — édition 2025",
                "semaine-thematique", organizer, "Ouagadougou", "Palais des Sports de Ouaga 2000",
                -323, EventStatus.TERMINE, false, true, "sdn-2025");
        activity(sdn25, "Cérémonie d'ouverture", ActivityType.CEREMONIE, 0, "Grand amphi", "act-ceremonie");
        activity(sdn25, "Conférence : administration numérique", ActivityType.CONFERENCE, 4, "Salle A", "act-conference");
        activity(sdn25, "Exposition des startups", ActivityType.NETWORKING, 24, "Hall d'exposition", "act-expo");
        tickets(sdn25, new String[] {"Standard", "5000", "800"});

        log.info("Jeu de données de démonstration créé : 3 événements à venir + 3 éditions passées "
                + "(SIAO 2024, FESPACO 2023, Semaine du Numérique 2025).");
    }

    /** Reads a bundled demo image, stores it, and returns its public URL (cached). */
    private String demoImage(String name) {
        if (name == null) {
            return null;
        }
        return imageUrls.computeIfAbsent(name, key -> {
            String path = "demo-images/" + key + ".jpg";
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
                if (in == null) {
                    log.warn("Image de démo introuvable : {}", path);
                    return null;
                }
                StoredFile stored = fileStorage.storeBytes(in.readAllBytes(), key + ".jpg",
                        "image/jpeg", "demo");
                return stored.url();
            } catch (IOException e) {
                log.warn("Échec du chargement de l'image de démo {}", path, e);
                return null;
            }
        });
    }

    private Organizer demoOrganizer() {
        User user = userRepository.findByEmailIgnoreCase("organisateur@plateforme.bf")
                .orElseGet(() -> {
                    User u = new User();
                    u.setEmail("organisateur@plateforme.bf");
                    u.setPasswordHash(passwordEncoder.encode("Demo!2026"));
                    u.setFirstName("Comité");
                    u.setLastName("National");
                    u.setType(UserType.ORGANISATEUR);
                    u.setStatus(UserStatus.ACTIF);
                    roleRepository.findByName(RoleNames.ORGANISATEUR).ifPresent(u::addRole);
                    return userRepository.save(u);
                });
        return organizerRepository.findByUserId(user.getId()).orElseGet(() -> {
            Organizer o = new Organizer();
            o.setUser(user);
            o.setNomAffichage("Comité National d'Organisation");
            o.setDescription("Organisateur des grands événements nationaux.");
            o.setStatut(OrganizerStatus.ACTIF);
            o.setApprouveLe(Instant.now());
            return organizerRepository.save(o);
        });
    }

    private Event event(String nom, String sigle, String description, String categorySlug,
                        Organizer organizer, String ville, String lieu, int daysFromNow,
                        EventStatus statut, boolean standsActifs, boolean hasActivities,
                        String coverImage) {
        Instant debut = Instant.now().plus(daysFromNow, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Event e = new Event();
        e.setOrganizer(organizer);
        e.setNom(nom);
        e.setSigle(sigle);
        e.setSlug(Slugs.uniqueSlug(nom, s -> !eventRepository.existsBySlug(s)));
        e.setDescriptionCourte(description);
        e.setDescriptionDetaillee(description + ".\n\nUn événement de référence organisé par le "
                + "Comité National d'Organisation, à " + ville + ".");
        e.setCategory(categoryRepository.findBySlug(categorySlug).orElse(null));
        e.setDateDebut(debut);
        e.setDateFin(debut.plus(5, ChronoUnit.DAYS));
        e.setLieu(lieu);
        e.setVille(ville);
        e.setPays("Burkina Faso");
        e.setCoverUrl(demoImage(coverImage));
        e.setStandsActifs(standsActifs);
        e.setHasActivities(hasActivities);
        e.setStatut(statut);
        e.setValideLe(Instant.now());
        e.setPublieLe(Instant.now());
        if (daysFromNow < 0) {
            e.setInscriptionFin(debut);
        }
        e.setContactEmail("contact@plateforme.bf");
        return eventRepository.save(e);
    }

    private void activity(Event event, String titre, ActivityType type, int hourOffset, String salle,
                          String image) {
        EventActivity a = new EventActivity();
        a.setEvent(event);
        a.setTitre(titre);
        a.setTypeActivite(type);
        a.setDateDebut(event.getDateDebut().plus(hourOffset, ChronoUnit.HOURS));
        a.setDateFin(event.getDateDebut().plus(hourOffset + 2L, ChronoUnit.HOURS));
        a.setSalle(salle);
        a.setImageUrl(demoImage(image));
        a.setOrdre(hourOffset);
        activityRepository.save(a);
    }

    private void tickets(Event event, String[]... defs) {
        int ordre = 0;
        for (String[] d : defs) {
            EventTicket t = new EventTicket();
            t.setEvent(event);
            t.setNom(d[0]);
            t.setPrixMontant(new BigDecimal(d[1]));
            t.setQuantiteTotale(Integer.parseInt(d[2]));
            t.setPortee(TicketScope.EVENEMENT);
            t.setLimiteParUtilisateur(10);
            t.setOrdre(ordre++);
            ticketRepository.save(t);
        }
    }

    private void standTypes(Event event) {
        standType(event, "Stand standard", "3m x 3m", "250000", 100, 0);
        standType(event, "Stand premium", "4m x 4m", "500000", 40, 1);
        standType(event, "Stand VIP", "6m x 6m", "1000000", 12, 2);
    }

    private void standType(Event event, String nom, String dimensions, String prix, int quantite,
                           int ordre) {
        StandType type = new StandType();
        type.setEvent(event);
        type.setNom(nom);
        type.setDimensions(dimensions);
        type.setPrixMontant(new BigDecimal(prix));
        type.setQuantiteTotale(quantite);
        type.setOrdre(ordre);
        type = standTypeRepository.save(type);

        String prefix = nom.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT)
                .substring(0, Math.min(6, nom.replaceAll("[^A-Za-z]", "").length()));
        int generate = Math.min(quantite, 20); // keep the demo dataset small
        for (int i = 1; i <= generate; i++) {
            Stand stand = new Stand();
            stand.setStandType(type);
            stand.setEvent(event);
            stand.setNumero(prefix + "-" + String.format(Locale.ROOT, "%03d", i));
            standRepository.save(stand);
        }
    }
}
