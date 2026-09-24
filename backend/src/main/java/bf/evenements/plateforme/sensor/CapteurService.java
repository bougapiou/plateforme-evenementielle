package bf.evenements.plateforme.sensor;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.checkin.CheckinDirection;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.security.TokenHasher;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Anonymous people counting: sensors are provisioned by the organiser, authenticated by a key. */
@Service
@RequiredArgsConstructor
public class CapteurService {

    private static final String KEY_PREFIX = "pne_";

    private final CapteurRepository capteurRepository;
    private final PassageCapteurRepository passageRepository;
    private final EventService eventService;
    private final TokenHasher tokenHasher;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;

    // --- management (organiser / admin) ---

    @Transactional(readOnly = true)
    public List<CapteurView> list(UUID eventId) {
        eventService.loadManaged(eventId);
        return capteurRepository.findByEventIdOrderByCreatedAtAsc(eventId).stream()
                .map(this::view).toList();
    }

    @Transactional
    public CapteurCreated create(UUID eventId, String nom) {
        Event event = eventService.loadManaged(eventId);
        String key = KEY_PREFIX + tokenHasher.generateOpaqueToken();
        Capteur c = new Capteur();
        c.setEvent(event);
        c.setNom(nom.trim());
        c.setCleHash(tokenHasher.sha256(key));
        c.setClePrefixe(key.substring(0, 8));
        c = capteurRepository.save(c);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "SENSOR_CREATED", "Event", eventId.toString(), null, "capteur=" + c.getNom());
        return new CapteurCreated(view(c), key);
    }

    @Transactional
    public void revoke(UUID eventId, UUID capteurId) {
        eventService.loadManaged(eventId);
        Capteur c = capteurRepository.findByIdAndEventId(capteurId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Capteur introuvable"));
        c.setActif(false);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "SENSOR_REVOKED", "Event", eventId.toString(), null, "capteur=" + c.getNom());
    }

    // --- device side ---

    /** Records {@code count} people crossing in {@code sens}; returns the running totals. */
    @Transactional
    public SensorReport record(String key, CheckinDirection sens, int count) {
        if (count < 1 || count > 1000) {
            throw new BusinessException("INVALID_COUNT", "count doit être compris entre 1 et 1000.");
        }
        Capteur c = authenticate(key);
        Event event = c.getEvent();
        if (!event.getStatut().isPubliclyVisible()) {
            throw new BusinessException("EVENT_NOT_ACTIVE",
                    "Cet événement n'est pas ouvert : les passages ne sont pas comptés.");
        }
        PassageCapteur p = new PassageCapteur();
        p.setEventId(event.getId());
        p.setCapteurId(c.getId());
        p.setSens(sens);
        p.setNombre(count);
        passageRepository.save(p);
        c.setDerniereActivite(Instant.now());
        return report(event.getId());
    }

    /** Side-effect-free connectivity check for the module's /config page: who am I, is my event open? */
    @Transactional
    public PingResponse ping(String key) {
        Capteur c = authenticate(key);
        c.setDerniereActivite(Instant.now());
        Event event = c.getEvent();
        SensorReport r = report(event.getId());
        return new PingResponse(c.getNom(), event.getNom(), event.getStatut().name(),
                event.getStatut().isPubliclyVisible(), r.entrees(), r.sorties(), r.presents());
    }

    private Capteur authenticate(String key) {
        Capteur c = key == null || key.isBlank()
                ? null
                : capteurRepository.findByCleHash(tokenHasher.sha256(key.trim())).orElse(null);
        if (c == null || !c.isActif()) {
            throw new BadCredentialsException("Clé de capteur invalide ou révoquée");
        }
        return c;
    }

    /** Running totals for an event — also merged into the attendance view. */
    @Transactional(readOnly = true)
    public SensorReport report(UUID eventId) {
        long in = passageRepository.totalForEvent(eventId, CheckinDirection.ENTREE);
        long out = passageRepository.totalForEvent(eventId, CheckinDirection.SORTIE);
        return new SensorReport(in, out, Math.max(0, in - out));
    }

    private CapteurView view(Capteur c) {
        return new CapteurView(c.getId(), c.getNom(), c.getClePrefixe() + "…", c.isActif(),
                c.getDerniereActivite(),
                passageRepository.totalForCapteur(c.getId(), CheckinDirection.ENTREE),
                passageRepository.totalForCapteur(c.getId(), CheckinDirection.SORTIE));
    }

    public record CapteurView(UUID id, String nom, String clePrefixe, boolean actif,
                              Instant derniereActivite, long entrees, long sorties) {
    }

    /** {@code cle} is returned only once, at creation. */
    public record CapteurCreated(CapteurView capteur, String cle) {
    }

    /** {@code accepte}: whether passages are currently counted (event published / running). */
    public record PingResponse(String capteur, String evenement, String statut, boolean accepte,
                               long entrees, long sorties, long presents) {
    }

    public record SensorReport(long entrees, long sorties, long presents) {
    }
}
