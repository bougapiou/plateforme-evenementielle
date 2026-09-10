package bf.evenements.plateforme.accreditation;

import bf.evenements.plateforme.accreditation.dto.AccreditationRequest;
import bf.evenements.plateforme.accreditation.dto.AccreditationResponse;
import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.security.TokenHasher;
import bf.evenements.plateforme.common.web.References;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventActivity;
import bf.evenements.plateforme.event.EventActivityRepository;
import bf.evenements.plateforme.event.EventService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issue and manage accreditation badges for an event's activities. */
@Service
@RequiredArgsConstructor
public class AccreditationService {

    private final AccreditationRepository accreditationRepository;
    private final EventActivityRepository activityRepository;
    private final EventService eventService;
    private final BadgePdfService badgePdfService;
    private final TokenHasher tokenHasher;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;

    @Transactional
    public AccreditationResponse issue(UUID eventId, AccreditationRequest request) {
        Event event = eventService.loadManaged(eventId);

        EventActivity activity = null;
        if (request.activityId() != null) {
            activity = activityRepository.findById(request.activityId())
                    .filter(a -> a.getEvent().getId().equals(eventId))
                    .orElseThrow(() -> ResourceNotFoundException.of("Activité", request.activityId()));
        }
        if (request.fonction() == AccreditationRole.AUTRE
                && (request.fonctionLibre() == null || request.fonctionLibre().isBlank())) {
            throw new BusinessException("FONCTION_LIBRE_REQUISE",
                    "Précisez la fonction lorsque « Autre » est choisi.");
        }

        Accreditation accr = new Accreditation();
        accr.setEvent(event);
        accr.setActivity(activity);
        accr.setNumero(References.unique("ACC", 8, accreditationRepository::existsByNumero));
        accr.setQrToken(uniqueToken());
        accr.setPersonneNom(request.personneNom().trim());
        accr.setPersonneEmail(blankToNull(request.personneEmail()));
        accr.setOrganisation(blankToNull(request.organisation()));
        accr.setFonction(request.fonction());
        accr.setFonctionLibre(blankToNull(request.fonctionLibre()));
        accr.setPhotoUrl(blankToNull(request.photoUrl()));
        accr.setStatut(Accreditation.AccreditationStatus.ACTIVE);
        accr = accreditationRepository.save(accr);

        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "ACCREDITATION_ISSUED", "Accreditation", accr.getId().toString(), null,
                "event=" + event.getNom() + " " + accr.getFonction() + " " + accr.getPersonneNom());
        return AccreditationResponse.from(accr);
    }

    @Transactional(readOnly = true)
    public List<AccreditationResponse> list(UUID eventId) {
        eventService.loadManaged(eventId);
        return accreditationRepository.findByEventIdOrderByCreatedAtAsc(eventId).stream()
                .map(AccreditationResponse::from).toList();
    }

    @Transactional
    public AccreditationResponse revoke(UUID id) {
        Accreditation accr = loadManaged(id);
        accr.setStatut(Accreditation.AccreditationStatus.REVOQUEE);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "ACCREDITATION_REVOKED", "Accreditation", id.toString(), null, null);
        return AccreditationResponse.from(accr);
    }

    @Transactional(readOnly = true)
    public byte[] badgePdf(UUID id) {
        return badgePdfService.render(loadManaged(id));
    }

    @Transactional(readOnly = true)
    public byte[] qrPng(UUID id) {
        Accreditation accr = loadManaged(id);
        return bf.evenements.plateforme.common.web.QrImages.png(accr.getQrToken(), 260);
    }

    // --- helpers ---

    private Accreditation loadManaged(UUID id) {
        Accreditation accr = accreditationRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Accréditation", id));
        eventService.loadManaged(accr.getEvent().getId()); // owner / admin check
        return accr;
    }

    private String uniqueToken() {
        String token;
        do {
            token = "ACR" + tokenHasher.generateOpaqueToken().substring(0, 40);
        } while (accreditationRepository.existsByQrToken(token));
        return token;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
