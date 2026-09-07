package bf.evenements.plateforme.stand;

import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.money.Money;
import bf.evenements.plateforme.common.web.Slugs;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventService;
import bf.evenements.plateforme.stand.dto.StandTypeRequest;
import bf.evenements.plateforme.stand.dto.StandTypeResponse;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StandTypeService {

    private final StandTypeRepository standTypeRepository;
    private final StandRepository standRepository;
    private final StandReservationRepository reservationRepository;
    private final EventService eventService;

    @Transactional(readOnly = true)
    public List<StandTypeResponse> list(UUID eventId) {
        eventService.loadManaged(eventId);
        return build(eventId);
    }

    @Transactional(readOnly = true)
    public List<StandTypeResponse> listPublic(UUID eventId) {
        return build(eventId);
    }

    private List<StandTypeResponse> build(UUID eventId) {
        var reservationsByType = reservationRepository.findByEventId(eventId).stream()
                .filter(r -> r.getStatut().isActive())
                .collect(java.util.stream.Collectors.groupingBy(r -> r.getStandType().getId(),
                        java.util.stream.Collectors.counting()));
        return standTypeRepository.findByEventIdOrderByOrdreAscPrixMontantAsc(eventId).stream()
                .map(t -> StandTypeResponse.from(t,
                        reservationsByType.getOrDefault(t.getId(), 0L).intValue()))
                .toList();
    }

    @Transactional
    public StandTypeResponse create(UUID eventId, StandTypeRequest request) {
        Event event = eventService.loadManaged(eventId);
        StandType type = new StandType();
        type.setEvent(event);
        apply(type, request);
        type = standTypeRepository.save(type);
        generateStands(type, event, 0, request.quantiteTotale());
        return StandTypeResponse.from(type, 0);
    }

    @Transactional
    public StandTypeResponse update(UUID eventId, UUID typeId, StandTypeRequest request) {
        Event event = eventService.loadManaged(eventId);
        StandType type = load(eventId, typeId);
        int currentStands = (int) standRepository.countByStandTypeId(typeId);
        int target = request.quantiteTotale();

        if (target > currentStands) {
            generateStands(type, event, currentStands, target - currentStands);
        } else if (target < currentStands) {
            removeSpareStands(typeId, currentStands - target);
        }
        apply(type, request);
        return StandTypeResponse.from(type, activeReservationCount(typeId));
    }

    @Transactional
    public void delete(UUID eventId, UUID typeId) {
        eventService.loadManaged(eventId);
        StandType type = load(eventId, typeId);
        if (activeReservationCount(typeId) > 0) {
            throw new BusinessException("STAND_TYPE_IN_USE",
                    "Ce type de stand a des réservations actives.");
        }
        standRepository.deleteAll(standRepository.findByStandTypeIdOrderByNumeroAsc(typeId));
        standTypeRepository.delete(type);
    }

    // --- helpers ---

    private void generateStands(StandType type, Event event, int startIndex, int count) {
        String prefix = standPrefix(type.getNom());
        for (int i = 1; i <= count; i++) {
            int n = startIndex + i;
            String numero = uniqueNumero(event.getId(), prefix, n);
            Stand stand = new Stand();
            stand.setStandType(type);
            stand.setEvent(event);
            stand.setNumero(numero);
            standRepository.save(stand);
        }
    }

    private void removeSpareStands(UUID typeId, int toRemove) {
        var reservedStandIds = reservationRepository.findByEventId(
                        standTypeRepository.findById(typeId).orElseThrow().getEvent().getId()).stream()
                .filter(r -> r.getStatut().isActive())
                .map(r -> r.getStand().getId())
                .collect(java.util.stream.Collectors.toSet());
        var removable = standRepository.findByStandTypeIdOrderByNumeroAsc(typeId).stream()
                .filter(s -> !reservedStandIds.contains(s.getId()))
                .limit(toRemove)
                .toList();
        if (removable.size() < toRemove) {
            throw new BusinessException("STANDS_RESERVED",
                    "Impossible de réduire : trop de stands sont réservés.");
        }
        standRepository.deleteAll(removable);
    }

    private String uniqueNumero(UUID eventId, String prefix, int n) {
        String candidate = prefix + "-" + String.format(Locale.ROOT, "%03d", n);
        int suffix = n;
        while (standRepository.existsByEventIdAndNumero(eventId, candidate)) {
            suffix++;
            candidate = prefix + "-" + String.format(Locale.ROOT, "%03d", suffix);
        }
        return candidate;
    }

    private static String standPrefix(String nom) {
        String slug = Slugs.slugify(nom).replace("-", "").toUpperCase(Locale.ROOT);
        return slug.isBlank() ? "STAND" : slug.substring(0, Math.min(6, slug.length()));
    }

    private int activeReservationCount(UUID typeId) {
        return (int) reservationRepository.findByEventId(
                        standTypeRepository.findById(typeId).orElseThrow().getEvent().getId()).stream()
                .filter(r -> r.getStandType().getId().equals(typeId) && r.getStatut().isActive())
                .count();
    }

    private StandType load(UUID eventId, UUID typeId) {
        StandType type = standTypeRepository.findById(typeId)
                .orElseThrow(() -> ResourceNotFoundException.of("Type de stand", typeId));
        if (!type.getEvent().getId().equals(eventId)) {
            throw ResourceNotFoundException.of("Type de stand", typeId);
        }
        return type;
    }

    private void apply(StandType t, StandTypeRequest r) {
        t.setNom(r.nom().trim());
        t.setDescription(r.description());
        t.setDimensions(r.dimensions());
        t.setPrixMontant(r.prixMontant());
        t.setDevise(r.devise() == null || r.devise().isBlank()
                ? Money.DEFAULT_CURRENCY : r.devise().toUpperCase());
        t.setQuantiteTotale(r.quantiteTotale());
        t.setEquipements(r.equipements());
        t.setConditions(r.conditions());
        if (r.ordre() != null) {
            t.setOrdre(r.ordre());
        }
    }
}
