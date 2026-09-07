package bf.evenements.plateforme.event;

import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.event.dto.ActivityRequest;
import bf.evenements.plateforme.event.dto.ActivityResponse;
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

    private final EventActivityRepository activityRepository;
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
        return ActivityResponse.from(activityRepository.save(activity));
    }

    @Transactional
    public ActivityResponse update(UUID eventId, UUID activityId, ActivityRequest request) {
        editableEvent(eventId);
        EventActivity activity = load(eventId, activityId);
        apply(activity, request, eventId);
        return ActivityResponse.from(activity);
    }

    @Transactional
    public void delete(UUID eventId, UUID activityId) {
        editableEvent(eventId);
        activityRepository.delete(load(eventId, activityId));
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
