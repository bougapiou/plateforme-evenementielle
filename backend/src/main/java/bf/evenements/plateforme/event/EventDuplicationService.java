package bf.evenements.plateforme.event;

import bf.evenements.plateforme.event.dto.ActivityRequest;
import bf.evenements.plateforme.event.dto.EventRequest;
import bf.evenements.plateforme.event.dto.EventResponse;
import bf.evenements.plateforme.event.dto.PartnerRequest;
import bf.evenements.plateforme.event.dto.SpeakerRequest;
import bf.evenements.plateforme.ticket.EventTicket;
import bf.evenements.plateforme.ticket.EventTicketRepository;
import bf.evenements.plateforme.ticket.EventTicketService;
import bf.evenements.plateforme.ticket.TicketScope;
import bf.evenements.plateforme.ticket.dto.EventTicketRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * « Next edition » (ORG-07): copies an event as a new draft with its programme,
 * tickets, speakers and partners, every date shifted by the same offset (one
 * year by default). Registrations, orders, scans and stands are not copied.
 */
@Service
@RequiredArgsConstructor
public class EventDuplicationService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Ouagadougou");

    private final EventService eventService;
    private final EventActivityService activityService;
    private final EventActivityRepository activityRepository;
    private final SpeakerRepository speakerRepository;
    private final SpeakerService speakerService;
    private final PartnerRepository partnerRepository;
    private final PartnerService partnerService;
    private final EventTicketRepository ticketRepository;
    private final EventTicketService ticketService;

    public record DuplicateRequest(@Nullable String nom, @Nullable Instant dateDebut) {
    }

    @Transactional
    public EventResponse duplicate(UUID sourceId, DuplicateRequest request) {
        Event src = eventService.loadManaged(sourceId);
        Instant newStart = request.dateDebut() != null ? request.dateDebut()
                : src.getDateDebut().atZone(ZONE).plusYears(1).toInstant();
        Duration shift = Duration.between(src.getDateDebut(), newStart);
        String nom = request.nom() != null && !request.nom().isBlank() ? request.nom().trim() : nextEditionName(src);

        EventResponse created = eventService.create(new EventRequest(
                nom, src.getSigle(), src.getDescriptionCourte(), src.getDescriptionDetaillee(),
                src.getCategory() != null ? src.getCategory().getId() : null, src.getLogoUrl(), src.getCoverUrl(),
                newStart, shift(src.getDateFin(), shift), src.getLieu(), src.getAdresse(), src.getVille(),
                src.getPays(), src.getLatitude(), src.getLongitude(), src.getCapaciteMax(), src.getContactEmail(),
                src.getContactTelephone(), src.getSiteWeb(), src.getConditionsParticipation(),
                src.isHasActivities(), src.isStandsActifs(), src.isStandsParticuliers(),
                src.isValidationInscription(), shift(src.getInscriptionDebut(), shift),
                shift(src.getInscriptionFin(), shift), shift(src.getReservationDebut(), shift),
                shift(src.getReservationFin(), shift)));
        UUID target = created.id();

        Map<UUID, UUID> speakers = new HashMap<>();
        for (Speaker s : speakerRepository.findByEventIdOrderByOrdreAscNomAsc(sourceId)) {
            speakers.put(s.getId(), speakerService.create(target, new SpeakerRequest(s.getNom(), s.getTitre(),
                    s.getOrganisation(), s.getBio(), s.getPhotoUrl(), s.getOrdre())).id());
        }
        for (Partner p : partnerRepository.findByEventIdOrderByOrdreAscNomAsc(sourceId)) {
            partnerService.create(target, new PartnerRequest(p.getNom(), p.getLogoUrl(), p.getSiteWeb(),
                    p.getNiveau(), p.getOrdre()));
        }

        Map<UUID, UUID> activities = new HashMap<>();
        Set<UUID> autoTickets = new HashSet<>();
        for (EventActivity a : activityRepository.findByEventIdOrderByDateDebutAscOrdreAsc(sourceId)) {
            if (a.getFreeTicketId() != null) {
                autoTickets.add(a.getFreeTicketId());
            }
            UUID id = activityService.create(target, new ActivityRequest(a.getTitre(), a.getDescription(),
                    a.getTypeActivite(), a.getAcces(), shift(a.getDateDebut(), shift), shift(a.getDateFin(), shift),
                    a.getSalle(), a.getLieu(), a.getIntervenant(), a.getModerateur(), a.getImageUrl(), a.getTheme(),
                    a.getFormat(), a.getLienDiffusion(),
                    a.getSpeakerId() != null ? speakers.get(a.getSpeakerId()) : null, a.getCapacite(),
                    a.getOrdre())).id();
            activities.put(a.getId(), id);
        }

        for (EventTicket t : ticketRepository.findByEventIdOrderByOrdreAscPrixMontantAsc(sourceId)) {
            if (autoTickets.contains(t.getId())) {
                continue; // recreated with its activity (access « Gratuit »)
            }
            Set<UUID> acts = new HashSet<>();
            t.getActivities().forEach(a -> {
                UUID mapped = activities.get(a.getId());
                if (mapped != null) {
                    acts.add(mapped);
                }
            });
            if (t.getPortee() == TicketScope.ACTIVITE && acts.isEmpty()) {
                continue;
            }
            ticketService.create(target, new EventTicketRequest(t.getNom(), t.getDescription(), t.getPrixMontant(),
                    t.getDevise(), t.getPortee(), t.getQuantiteTotale(), t.getLimiteParUtilisateur(),
                    shift(t.getVenteDebut(), shift), shift(t.getVenteFin(), shift), t.isActif(), t.getOrdre(),
                    t.isFormulaireRequis(), t.getIdentiteRequise(), acts));
        }
        return eventService.get(target);
    }

    @Nullable
    private static Instant shift(@Nullable Instant i, Duration d) {
        return i == null ? null : i.plus(d);
    }

    /** « SIAO 2026 » → « SIAO 2027 »; otherwise « … (édition suivante) ». */
    private static String nextEditionName(Event src) {
        String nom = src.getNom();
        int year = src.getDateDebut().atZone(ZONE).getYear();
        if (nom.contains(String.valueOf(year))) {
            return nom.replace(String.valueOf(year), String.valueOf(year + 1));
        }
        return nom + " (édition suivante)";
    }
}
