package bf.evenements.plateforme.checkin;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.checkin.dto.ScanRequest;
import bf.evenements.plateforme.checkin.dto.ScanResponse;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.accreditation.Accreditation;
import bf.evenements.plateforme.accreditation.AccreditationRepository;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventActivity;
import bf.evenements.plateforme.event.EventActivityRepository;
import bf.evenements.plateforme.event.EventRepository;
import bf.evenements.plateforme.event.EventSpecifications;
import bf.evenements.plateforme.event.EventStatus;
import bf.evenements.plateforme.event.dto.ActivityResponse;
import bf.evenements.plateforme.event.dto.EventSummary;
import bf.evenements.plateforme.qrcode.QrCode;
import bf.evenements.plateforme.qrcode.QrCodeRepository;
import bf.evenements.plateforme.rbac.Permissions;
import bf.evenements.plateforme.ticket.EventTicket;
import bf.evenements.plateforme.ticket.Ticket;
import bf.evenements.plateforme.ticket.TicketRepository;
import bf.evenements.plateforme.ticket.TicketScope;
import bf.evenements.plateforme.ticket.TicketStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckinService {

    private final CheckinRepository checkinRepository;
    private final EventStaffRepository staffRepository;
    private final QrCodeRepository qrCodeRepository;
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final EventActivityRepository activityRepository;
    private final AccreditationRepository accreditationRepository;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;

    /** States in which entry control makes sense. */
    private static final Set<EventStatus> SCANNABLE = Set.of(
            EventStatus.PUBLIE, EventStatus.INSCRIPTIONS_OUVERTES,
            EventStatus.INSCRIPTIONS_FERMEES, EventStatus.EN_COURS);

    /**
     * Events the current user may run entry control for: those they organise,
     * those they are assigned to as control staff, and — for an admin — all of
     * them. Only events in a scannable state are returned.
     */
    @Transactional(readOnly = true)
    public List<EventSummary> controllableEvents() {
        UUID me = currentUser.requireId();
        Specification<Event> scannable = EventSpecifications.statusIn(SCANNABLE);
        Sort byDate = Sort.by(Sort.Direction.ASC, "dateDebut");

        if (currentUser.hasAuthority(Permissions.EVENT_VALIDATE)) {
            return eventRepository.findAll(scannable, byDate).stream()
                    .map(EventSummary::from).toList();
        }

        List<UUID> staffEventIds = staffRepository.findByUserId(me).stream()
                .map(s -> s.getEvent().getId()).toList();
        Specification<Event> mineOrStaff = Specification.anyOf(
                EventSpecifications.ownedByUser(me),
                EventSpecifications.idIn(staffEventIds));
        return eventRepository.findAll(scannable.and(mineOrStaff), byDate).stream()
                .map(EventSummary::from).toList();
    }

    /** Activities a controller may pick when checking entries for an event. */
    @Transactional(readOnly = true)
    public List<ActivityResponse> controllableActivities(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> ResourceNotFoundException.of("Événement", eventId));
        requireControl(event, currentUser.requireId());
        return activityRepository.findByEventIdOrderByDateDebutAscOrdreAsc(eventId).stream()
                .map(ActivityResponse::from).toList();
    }

    @Transactional
    public ScanResponse scan(ScanRequest request) {
        UUID me = currentUser.requireId();
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> ResourceNotFoundException.of("Événement", request.eventId()));
        requireControl(event, me);

        EventActivity activity = null;
        if (request.activityId() != null) {
            activity = activityRepository.findById(request.activityId()).orElse(null);
            if (activity == null || !activity.getEvent().getId().equals(event.getId())) {
                return record(event, null, me, null, null, CheckinResult.INVALIDE,
                        "Activité inconnue pour cet événement",
                        ScanResponse.invalide("Activité inconnue", event.getNom()));
            }
        }
        String activiteNom = activity != null ? activity.getTitre() : null;

        String token = request.token().trim();
        QrCode qr = qrCodeRepository.findByToken(token).orElse(null);
        if (qr == null) {
            return scanAccreditation(event, activity, activiteNom, me, token);
        }
        Ticket ticket = ticketRepository.findById(qr.getTicket().getId()).orElseThrow();

        if (!ticket.getEvent().getId().equals(event.getId())) {
            return record(event, activity, me, qr.getId(), ticket.getId(), CheckinResult.INVALIDE,
                    "Billet d'un autre événement",
                    new ScanResponse(CheckinResult.INVALIDE, "Ce billet concerne un autre événement",
                            event.getNom(), activiteNom, null, null, null, null, null));
        }
        if (qr.getStatut() == QrCode.QrStatus.REVOQUE || ticket.getStatut() == TicketStatus.ANNULE) {
            return record(event, activity, me, qr.getId(), ticket.getId(), CheckinResult.INVALIDE,
                    "Billet annulé / révoqué",
                    new ScanResponse(CheckinResult.INVALIDE, "Ticket invalide (annulé)",
                            event.getNom(), activiteNom, null, null, null, null, null));
        }
        if (activity != null && !grantsAccess(ticket, activity)) {
            return record(event, activity, me, qr.getId(), ticket.getId(), CheckinResult.INVALIDE,
                    "Billet non valable pour cette activité",
                    new ScanResponse(CheckinResult.INVALIDE, "Billet non valable pour cette activité",
                            event.getNom(), activiteNom, ticket.getParticipantNom(),
                            ticket.getEventTicket().getNom(), ticket.getNumero(), null, null));
        }

        var existing = activity != null
                ? checkinRepository.findFirstByTicketIdAndActivityIdAndResultatOrderByScannedAtAsc(
                        ticket.getId(), activity.getId(), CheckinResult.VALIDE)
                : checkinRepository.findFirstByTicketIdAndActivityIdIsNullAndResultatOrderByScannedAtAsc(
                        ticket.getId(), CheckinResult.VALIDE);
        if (existing.isPresent()) {
            ScanResponse resp = new ScanResponse(CheckinResult.DEJA_UTILISE, "Ticket déjà utilisé",
                    event.getNom(), activiteNom, ticket.getParticipantNom(),
                    ticket.getEventTicket().getNom(), ticket.getNumero(), null,
                    existing.get().getScannedAt());
            return record(event, activity, me, qr.getId(), ticket.getId(), CheckinResult.DEJA_UTILISE,
                    "Contrôle déjà effectué le " + existing.get().getScannedAt(), resp);
        }

        // valid entry — a general (event-wide) scan consumes the ticket; a
        // per-activity scan leaves it EMISE so the holder can enter other sessions.
        Instant now = Instant.now();
        if (activity == null) {
            ticket.setStatut(TicketStatus.UTILISE);
        }
        ScanResponse resp = new ScanResponse(CheckinResult.VALIDE, "Bienvenue", event.getNom(),
                activiteNom, ticket.getParticipantNom(), ticket.getEventTicket().getNom(),
                ticket.getNumero(), now, null);
        return record(event, activity, me, qr.getId(), ticket.getId(), CheckinResult.VALIDE, null, resp);
    }

    private boolean grantsAccess(Ticket ticket, EventActivity activity) {
        EventTicket category = ticket.getEventTicket();
        if (category.getPortee() == TicketScope.EVENEMENT) {
            return true;
        }
        return category.getActivities().stream()
                .anyMatch(a -> a.getId().equals(activity.getId()));
    }

    /** The QR is not a ticket — try to match an accreditation badge. */
    private ScanResponse scanAccreditation(Event event, EventActivity activity, String activiteNom,
                                           UUID me, String token) {
        Accreditation accr = accreditationRepository.findByQrToken(token).orElse(null);
        if (accr == null) {
            return record(event, activity, me, null, null, null, CheckinResult.INVALIDE,
                    "QR code inconnu",
                    new ScanResponse(CheckinResult.INVALIDE, "QR code inconnu", event.getNom(),
                            activiteNom, null, null, null, null, null));
        }
        String label = "Badge · " + accr.fonctionLabel();
        if (!accr.getEvent().getId().equals(event.getId())) {
            return recordAccr(event, activity, me, accr, CheckinResult.INVALIDE,
                    "Badge d'un autre événement",
                    new ScanResponse(CheckinResult.INVALIDE, "Ce badge concerne un autre événement",
                            event.getNom(), activiteNom, accr.getPersonneNom(), label,
                            accr.getNumero(), null, null));
        }
        if (accr.getStatut() != Accreditation.AccreditationStatus.ACTIVE) {
            return recordAccr(event, activity, me, accr, CheckinResult.INVALIDE, "Badge révoqué",
                    new ScanResponse(CheckinResult.INVALIDE, "Badge révoqué", event.getNom(),
                            activiteNom, accr.getPersonneNom(), label, accr.getNumero(), null, null));
        }
        if (accr.getActivity() != null
                && (activity == null || !accr.getActivity().getId().equals(activity.getId()))) {
            return recordAccr(event, activity, me, accr, CheckinResult.INVALIDE,
                    "Badge lié à l'activité « " + accr.getActivity().getTitre() + " »",
                    new ScanResponse(CheckinResult.INVALIDE,
                            "Badge non valable pour cette activité", event.getNom(), activiteNom,
                            accr.getPersonneNom(), label, accr.getNumero(), null, null));
        }
        // Badges allow re-entry — every scan is valid and simply logged.
        ScanResponse resp = new ScanResponse(CheckinResult.VALIDE,
                "Accès " + accr.fonctionLabel(), event.getNom(), activiteNom,
                accr.getPersonneNom(), label, accr.getNumero(), Instant.now(), null);
        return recordAccr(event, activity, me, accr, CheckinResult.VALIDE, null, resp);
    }

    @Transactional(readOnly = true)
    public PageResponse<CheckinView> list(UUID eventId, Pageable pageable) {
        requireOrganiser(eventId);
        return PageResponse.of(checkinRepository.findByEventIdOrderByScannedAtDesc(eventId, pageable),
                CheckinView::from);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> stats(UUID eventId) {
        requireOrganiser(eventId);
        return Map.of(
                "valides", checkinRepository.countByEventIdAndResultat(eventId, CheckinResult.VALIDE),
                "dejaUtilises",
                checkinRepository.countByEventIdAndResultat(eventId, CheckinResult.DEJA_UTILISE),
                "invalides",
                checkinRepository.countByEventIdAndResultat(eventId, CheckinResult.INVALIDE));
    }

    // --- helpers ---

    private ScanResponse record(Event event, EventActivity activity, UUID scannedBy, UUID qrId,
                                UUID ticketId, UUID accreditationId, CheckinResult resultat,
                                String detail, ScanResponse response) {
        Checkin checkin = new Checkin();
        checkin.setEventId(event.getId());
        checkin.setActivityId(activity != null ? activity.getId() : null);
        checkin.setScannedBy(scannedBy);
        checkin.setQrCodeId(qrId);
        checkin.setTicketId(ticketId);
        checkin.setAccreditationId(accreditationId);
        checkin.setResultat(resultat);
        checkin.setScannedAt(Instant.now());
        checkin.setDetail(detail);
        checkinRepository.save(checkin);
        auditService.record(scannedBy, currentUser.current().map(u -> u.email()).orElse(null),
                "CHECKIN_SCAN", "Checkin", checkin.getId().toString(), null,
                "event=" + event.getNom()
                        + (activity != null ? " activite=" + activity.getTitre() : "")
                        + " resultat=" + resultat);
        return response;
    }

    private ScanResponse record(Event event, EventActivity activity, UUID scannedBy, UUID qrId,
                                UUID ticketId, CheckinResult resultat, String detail,
                                ScanResponse response) {
        return record(event, activity, scannedBy, qrId, ticketId, null, resultat, detail, response);
    }

    private ScanResponse recordAccr(Event event, EventActivity activity, UUID scannedBy,
                                    Accreditation accr, CheckinResult resultat, String detail,
                                    ScanResponse response) {
        return record(event, activity, scannedBy, null, null, accr.getId(), resultat, detail, response);
    }

    private void requireControl(Event event, UUID userId) {
        boolean allowed = event.isOwnedBy(userId)
                || staffRepository.existsByEventIdAndUserId(event.getId(), userId)
                || currentUser.hasAuthority(Permissions.EVENT_VALIDATE);
        if (!allowed) {
            throw new AccessDeniedException("Vous n'êtes pas habilité à contrôler cet événement.");
        }
    }

    private void requireOrganiser(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> ResourceNotFoundException.of("Événement", eventId));
        if (!event.isOwnedBy(currentUser.requireId())
                && !currentUser.hasAuthority(Permissions.EVENT_VALIDATE)) {
            throw new AccessDeniedException("Accès aux contrôles refusé.");
        }
    }

    public record CheckinView(UUID id, UUID ticketId, UUID activityId, CheckinResult resultat,
                              Instant scannedAt, UUID scannedBy, String detail) {
        static CheckinView from(Checkin c) {
            return new CheckinView(c.getId(), c.getTicketId(), c.getActivityId(), c.getResultat(),
                    c.getScannedAt(), c.getScannedBy(), c.getDetail());
        }
    }
}
