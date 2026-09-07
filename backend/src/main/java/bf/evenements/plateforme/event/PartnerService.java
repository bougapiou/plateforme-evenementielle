package bf.evenements.plateforme.event;

import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.event.dto.PartnerRequest;
import bf.evenements.plateforme.event.dto.PartnerResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PartnerService {

    private final PartnerRepository partnerRepository;
    private final EventService eventService;

    @Transactional(readOnly = true)
    public List<PartnerResponse> list(UUID eventId) {
        eventService.loadManaged(eventId);
        return partnerRepository.findByEventIdOrderByOrdreAscNomAsc(eventId).stream()
                .map(PartnerResponse::from).toList();
    }

    @Transactional
    public PartnerResponse create(UUID eventId, PartnerRequest request) {
        Event event = eventService.loadManaged(eventId);
        Partner partner = new Partner();
        partner.setEvent(event);
        apply(partner, request);
        return PartnerResponse.from(partnerRepository.save(partner));
    }

    @Transactional
    public PartnerResponse update(UUID eventId, UUID partnerId, PartnerRequest request) {
        eventService.loadManaged(eventId);
        Partner partner = load(eventId, partnerId);
        apply(partner, request);
        return PartnerResponse.from(partner);
    }

    @Transactional
    public void delete(UUID eventId, UUID partnerId) {
        eventService.loadManaged(eventId);
        partnerRepository.delete(load(eventId, partnerId));
    }

    private Partner load(UUID eventId, UUID partnerId) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Partenaire", partnerId));
        if (!partner.getEvent().getId().equals(eventId)) {
            throw ResourceNotFoundException.of("Partenaire", partnerId);
        }
        return partner;
    }

    private void apply(Partner p, PartnerRequest r) {
        p.setNom(r.nom().trim());
        p.setLogoUrl(r.logoUrl());
        p.setSiteWeb(r.siteWeb());
        p.setNiveau(r.niveau());
        if (r.ordre() != null) {
            p.setOrdre(r.ordre());
        }
    }
}
