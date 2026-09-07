package bf.evenements.plateforme.stand;

import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventRepository;
import bf.evenements.plateforme.event.EventService;
import bf.evenements.plateforme.stand.dto.StandResponse;
import bf.evenements.plateforme.stand.dto.UpdateStandRequest;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StandService {

    private final StandRepository standRepository;
    private final StandReservationRepository reservationRepository;
    private final EventRepository eventRepository;
    private final EventService eventService;

    @Transactional(readOnly = true)
    public java.util.List<StandResponse> listForManagement(UUID eventId) {
        eventService.loadManaged(eventId);
        return build(eventId);
    }

    @Transactional(readOnly = true)
    public java.util.List<StandResponse> listPublic(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> ResourceNotFoundException.of("Événement", eventId));
        if (!event.getStatut().isPubliclyVisible()) {
            throw ResourceNotFoundException.of("Événement", eventId);
        }
        return build(eventId);
    }

    private java.util.List<StandResponse> build(UUID eventId) {
        Set<UUID> reservedStandIds = reservationRepository.findByEventId(eventId).stream()
                .filter(r -> r.getStatut().isActive())
                .map(r -> r.getStand().getId())
                .collect(Collectors.toSet());
        return standRepository.findByEventIdOrderByNumeroAsc(eventId).stream()
                .map(s -> StandResponse.from(s, reservedStandIds.contains(s.getId())))
                .toList();
    }

    @Transactional
    public StandResponse update(UUID eventId, UUID standId, UpdateStandRequest request) {
        eventService.loadManaged(eventId);
        Stand stand = standRepository.findById(standId)
                .filter(s -> s.getEvent().getId().equals(eventId))
                .orElseThrow(() -> ResourceNotFoundException.of("Stand", standId));
        if (request.positionX() != null) {
            stand.setPositionX(request.positionX());
        }
        if (request.positionY() != null) {
            stand.setPositionY(request.positionY());
        }
        if (request.statut() != null) {
            stand.setStatut(request.statut());
        }
        boolean reserved = !reservationRepository.findActiveByStand(standId).isEmpty();
        return StandResponse.from(stand, reserved);
    }
}
