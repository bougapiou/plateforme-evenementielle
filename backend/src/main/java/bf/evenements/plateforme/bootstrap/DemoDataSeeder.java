package bf.evenements.plateforme.bootstrap;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
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
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    @Transactional
    public void seed() {
        if (!enabled || eventRepository.existsBySlug("siao-2027")) {
            return;
        }
        Organizer organizer = demoOrganizer();

        Event siao = event("SIAO 2027", "SIAO",
                "Salon International de l'Artisanat de Ouagadougou",
                "foire-commerciale", organizer, "Ouagadougou", "SIEPA - Zone du Bois",
                60, true, false);
        standTypes(siao);
        tickets(siao,
                new String[] {"Entrée visiteur", "1000", "5000"},
                new String[] {"Pass semaine", "5000", "1000"});

        Event fespaco = event("FESPACO 2027", "FESPACO",
                "Festival Panafricain du Cinéma et de la Télévision de Ouagadougou",
                "festival", organizer, "Ouagadougou", "Ciné Burkina & Canal Olympia",
                90, true, false);
        standTypes(fespaco);
        tickets(fespaco,
                new String[] {"Projection standard", "2000", "3000"},
                new String[] {"Pass festival", "20000", "500"});

        Event sdn = event("Semaine du Numérique 2027", "SDN",
                "Rendez-vous national de la transformation numérique",
                "semaine-thematique", organizer, "Ouagadougou", "Palais des Sports de Ouaga 2000",
                45, false, true);
        activity(sdn, "Cérémonie d'ouverture", ActivityType.CEREMONIE, 0, "Grand amphi");
        activity(sdn, "Conférence : IA et services publics", ActivityType.CONFERENCE, 3, "Salle A");
        activity(sdn, "Panel : souveraineté numérique", ActivityType.PANEL, 6, "Salle B");
        activity(sdn, "Networking & startups", ActivityType.NETWORKING, 24, "Hall d'exposition");
        tickets(sdn,
                new String[] {"Standard", "5000", "800"},
                new String[] {"Étudiant", "2000", "500"},
                new String[] {"Professionnel", "15000", "300"});

        log.info("Jeu de données de démonstration créé : SIAO, FESPACO, Semaine du Numérique 2027.");
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
                        boolean standsActifs, boolean hasActivities) {
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
        e.setStandsActifs(standsActifs);
        e.setHasActivities(hasActivities);
        e.setStatut(EventStatus.INSCRIPTIONS_OUVERTES);
        e.setValideLe(Instant.now());
        e.setPublieLe(Instant.now());
        e.setContactEmail("contact@plateforme.bf");
        return eventRepository.save(e);
    }

    private void activity(Event event, String titre, ActivityType type, int hourOffset, String salle) {
        EventActivity a = new EventActivity();
        a.setEvent(event);
        a.setTitre(titre);
        a.setTypeActivite(type);
        a.setDateDebut(event.getDateDebut().plus(hourOffset, ChronoUnit.HOURS));
        a.setDateFin(event.getDateDebut().plus(hourOffset + 2L, ChronoUnit.HOURS));
        a.setSalle(salle);
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
