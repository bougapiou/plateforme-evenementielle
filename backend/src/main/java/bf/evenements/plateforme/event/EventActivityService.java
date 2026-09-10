package bf.evenements.plateforme.event;

import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.money.Money;
import bf.evenements.plateforme.event.dto.ActivityRequest;
import bf.evenements.plateforme.event.dto.ActivityResponse;
import bf.evenements.plateforme.ticket.EventTicket;
import bf.evenements.plateforme.ticket.EventTicketRepository;
import bf.evenements.plateforme.ticket.TicketScope;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the programme of an event. Activities double as the schedule and, when
 * {@code event.hasActivities} is set, as the units ticket categories can target.
 */
@Service
@RequiredArgsConstructor
public class EventActivityService {

    /** Fallback quota for a free activity with no explicit capacity. */
    private static final int OPEN_QUOTA = 100_000;

    private final EventActivityRepository activityRepository;
    private final EventTicketRepository eventTicketRepository;
    private final SpeakerRepository speakerRepository;
    private final EventService eventService;

    @Transactional(readOnly = true)
    public List<ActivityResponse> list(UUID eventId) {
        eventService.loadManaged(eventId);
        return activityRepository.findByEventIdOrderByDateDebutAscOrdreAsc(eventId).stream()
                .map(ActivityResponse::from).toList();
    }

    @Transactional
    public ActivityResponse create(UUID eventId, ActivityRequest request) {
        Event event = editableEvent(eventId);
        EventActivity activity = new EventActivity();
        activity.setEvent(event);
        apply(activity, request, eventId);
        activity = activityRepository.save(activity); // needs an id for the ticket link
        syncAccess(activity, request.resolvedAcces());
        return ActivityResponse.from(activity);
    }

    @Transactional
    public ActivityResponse update(UUID eventId, UUID activityId, ActivityRequest request) {
        editableEvent(eventId);
        EventActivity activity = load(eventId, activityId);
        apply(activity, request, eventId);
        syncAccess(activity, request.resolvedAcces());
        return ActivityResponse.from(activity);
    }

    @Transactional
    public void delete(UUID eventId, UUID activityId) {
        editableEvent(eventId);
        EventActivity activity = load(eventId, activityId);
        if (activity.getFreeTicketId() != null) {
            eventTicketRepository.findById(activity.getFreeTicketId()).ifPresent(ft -> {
                ft.getActivities().clear();
                ft.setActif(false);
            });
        }
        activityRepository.delete(activity);
    }

    /**
     * Keeps the auto-managed free ticket category in sync with the activity's
     * access mode. GRATUIT ⇒ a price-0 ACTIVITE ticket exists and is active;
     * leaving GRATUIT only deactivates it (its id and history are kept).
     */
    private void syncAccess(EventActivity a, ActivityAccess acces) {
        a.setAcces(acces);
        if (acces == ActivityAccess.GRATUIT) {
            EventTicket ft = a.getFreeTicketId() == null ? null
                    : eventTicketRepository.findById(a.getFreeTicketId()).orElse(null);
            if (ft == null) {
                ft = new EventTicket();
                ft.setEvent(a.getEvent());
                ft.setPortee(TicketScope.ACTIVITE);
                ft.setLimiteParUtilisateur(1);
            }
            ft.setNom("Accès — " + a.getTitre());
            ft.setDescription("Accès gratuit à l'activité « " + a.getTitre() + " »");
            ft.setPrixMontant(BigDecimal.ZERO);
            ft.setDevise(Money.DEFAULT_CURRENCY);
            ft.setQuantiteTotale(
                    a.getCapacite() != null && a.getCapacite() > 0 ? a.getCapacite() : OPEN_QUOTA);
            ft.setActif(true);
            ft.getActivities().clear();
            ft.getActivities().add(a);
            ft = eventTicketRepository.save(ft);
            a.setFreeTicketId(ft.getId());
        } else if (a.getFreeTicketId() != null) {
            eventTicketRepository.findById(a.getFreeTicketId())
                    .ifPresent(ft -> ft.setActif(false));
        }
    }

    private Event editableEvent(UUID eventId) {
        Event event = eventService.loadManaged(eventId);
        if (event.getStatut() == EventStatus.ANNULE || event.getStatut() == EventStatus.TERMINE) {
            throw new BusinessException("EVENT_LOCKED",
                    "Le programme ne peut plus être modifié pour cet événement.");
        }
        return event;
    }

    private EventActivity load(UUID eventId, UUID activityId) {
        EventActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> ResourceNotFoundException.of("Activité", activityId));
        if (!activity.getEvent().getId().equals(eventId)) {
            throw ResourceNotFoundException.of("Activité", activityId);
        }
        return activity;
    }

    private void apply(EventActivity a, ActivityRequest r, UUID eventId) {
        a.setTitre(r.titre().trim());
        a.setDescription(r.description());
        a.setTypeActivite(r.typeActivite());
        a.setDateDebut(r.dateDebut());
        a.setDateFin(r.dateFin());
        a.setSalle(r.salle());
        a.setLieu(r.lieu());
        a.setIntervenant(r.intervenant());
        a.setModerateur(r.moderateur());
        a.setImageUrl(r.imageUrl());
        a.setCapacite(r.capacite());
        if (r.ordre() != null) {
            a.setOrdre(r.ordre());
        }
        if (r.speakerId() != null) {
            speakerRepository.findById(r.speakerId())
                    .filter(s -> s.getEvent().getId().equals(eventId))
                    .orElseThrow(() -> ResourceNotFoundException.of("Intervenant", r.speakerId()));
            a.setSpeakerId(r.speakerId());
        } else {
            a.setSpeakerId(null);
        }
    }
}
