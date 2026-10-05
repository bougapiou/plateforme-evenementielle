package bf.evenements.plateforme.standvisit;

import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventRepository;
import bf.evenements.plateforme.stand.Stand;
import bf.evenements.plateforme.stand.StandRepository;
import bf.evenements.plateforme.stand.StandReservation;
import bf.evenements.plateforme.stand.StandReservationRepository;
import bf.evenements.plateforme.standvisit.dto.FrequentationResponse;
import bf.evenements.plateforme.standvisit.dto.SignalerPassageRequest;
import bf.evenements.plateforme.standvisit.dto.StandFrequentationResponse;
import bf.evenements.plateforme.standvisit.dto.StandInfoResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stand foot traffic, independent of ticket check-in and the laser sensors : a visitor signals
 * their own passage from a page specific to one stand (one link/QR per stand, printed or
 * displayed there). No account, no required identity. Everything here is public — the stats page
 * is a live ranking anyone can open, by design (see the project's product decision).
 */
@Service
@RequiredArgsConstructor
public class VisiteStandService {

    private final VisiteStandRepository visiteRepository;
    private final StandRepository standRepository;
    private final StandReservationRepository reservationRepository;
    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public StandInfoResponse standInfo(String slug, UUID standId) {
        Stand stand = resolveStand(slug, standId);
        return StandInfoResponse.from(stand, exposantNom(stand.getId()));
    }

    @Transactional
    public void signaler(String slug, UUID standId, SignalerPassageRequest request) {
        Stand stand = resolveStand(slug, standId);
        VisiteStand v = new VisiteStand();
        v.setEvent(stand.getEvent());
        v.setStand(stand);
        v.setNom(blankToNull(request.nom()));
        v.setPrenom(blankToNull(request.prenom()));
        v.setTelephone(blankToNull(request.telephone()));
        visiteRepository.save(v);
    }

    @Transactional(readOnly = true)
    public FrequentationResponse stats(String slug) {
        Event event = resolveEvent(slug);
        Map<UUID, Long> parStand = visiteRepository.countByStandForEvent(event.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        VisiteStandRepository.StandCount::getStandId, VisiteStandRepository.StandCount::getTotal));

        List<StandFrequentationResponse> stands = standRepository
                .findByEventIdOrderByNumeroAsc(event.getId()).stream()
                .map(s -> new StandFrequentationResponse(s.getId(), s.getNumero(), s.getStandType().getNom(),
                        exposantNom(s.getId()), parStand.getOrDefault(s.getId(), 0L)))
                .sorted(Comparator.comparingLong(StandFrequentationResponse::visites).reversed())
                .toList();

        long total = stands.stream().mapToLong(StandFrequentationResponse::visites).sum();
        return new FrequentationResponse(event.getNom(), total, stands);
    }

    /** First structure or user name behind an active reservation of this stand, if any. */
    private String exposantNom(UUID standId) {
        List<StandReservation> actives = reservationRepository.findActiveByStand(standId);
        if (actives.isEmpty()) {
            return null;
        }
        StandReservation r = actives.get(0);
        return r.getStructure() != null ? r.getStructure().getRaisonSociale() : r.getUser().getFullName();
    }

    private Stand resolveStand(String slug, UUID standId) {
        Event event = resolveEvent(slug);
        Stand stand = standRepository.findById(standId)
                .orElseThrow(() -> ResourceNotFoundException.of("Stand", standId));
        if (!stand.getEvent().getId().equals(event.getId())) {
            throw new ResourceNotFoundException("Stand introuvable pour cet événement");
        }
        return stand;
    }

    private Event resolveEvent(String slug) {
        return eventRepository.findBySlug(slug)
                .filter(e -> e.getStatut().isPubliclyVisible())
                .orElseThrow(() -> new ResourceNotFoundException("Événement introuvable : " + slug));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
