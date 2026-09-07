package bf.evenements.plateforme.event;

import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.event.dto.SpeakerRequest;
import bf.evenements.plateforme.event.dto.SpeakerResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpeakerService {

    private final SpeakerRepository speakerRepository;
    private final EventService eventService;

    @Transactional(readOnly = true)
    public List<SpeakerResponse> list(UUID eventId) {
        eventService.loadManaged(eventId);
        return speakerRepository.findByEventIdOrderByOrdreAscNomAsc(eventId).stream()
                .map(SpeakerResponse::from).toList();
    }

    @Transactional
    public SpeakerResponse create(UUID eventId, SpeakerRequest request) {
        Event event = eventService.loadManaged(eventId);
        Speaker speaker = new Speaker();
        speaker.setEvent(event);
        apply(speaker, request);
        return SpeakerResponse.from(speakerRepository.save(speaker));
    }

    @Transactional
    public SpeakerResponse update(UUID eventId, UUID speakerId, SpeakerRequest request) {
        eventService.loadManaged(eventId);
        Speaker speaker = load(eventId, speakerId);
        apply(speaker, request);
        return SpeakerResponse.from(speaker);
    }

    @Transactional
    public void delete(UUID eventId, UUID speakerId) {
        eventService.loadManaged(eventId);
        speakerRepository.delete(load(eventId, speakerId));
    }

    private Speaker load(UUID eventId, UUID speakerId) {
        Speaker speaker = speakerRepository.findById(speakerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Intervenant", speakerId));
        if (!speaker.getEvent().getId().equals(eventId)) {
            throw ResourceNotFoundException.of("Intervenant", speakerId);
        }
        return speaker;
    }

    private void apply(Speaker s, SpeakerRequest r) {
        s.setNom(r.nom().trim());
        s.setTitre(r.titre());
        s.setOrganisation(r.organisation());
        s.setBio(r.bio());
        s.setPhotoUrl(r.photoUrl());
        if (r.ordre() != null) {
            s.setOrdre(r.ordre());
        }
    }
}
