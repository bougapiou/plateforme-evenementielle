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
import java.util.Optional;
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
        CheckinDirection sens = request.resolvedSens();

        EventActivity activity = null;
        if (request.activityId() != null) {
            activity = activityRepository.findById(request.activityId()).orElse(null);
            if (activity == null || !activity.getEvent().getId().equals(event.getId())) {
                return record(event, null, sens, me, null, null, CheckinResult.INVALIDE,
                        "Activité inconnue pour cet événement",
                        ScanResponse.invalide("Activité inconnue", event.getNom()));
            }
        }
        String activiteNom = activity != null ? activity.getTitre() : null;

        String token = request.token().trim();
        QrCode qr = qrCodeRepository.findByToken(token).orElse(null);
        if (qr == null) {
            return scanAccreditation(event, activity, activiteNom, sens, me, token);
        }
        Ticket ticket = ticketRepository.findById(qr.getTicket().getId()).orElseThrow();
        String nom = event.getNom();
        String pNom = ticket.getParticipantNom();
        String catNom = ticket.getEventTicket().getNom();
        String num = ticket.getNumero();

        if (!ticket.getEvent().getId().equals(event.getId())) {
            return record(event, activity, sens, me, qr.getId(), ticket.getId(),
                    CheckinResult.INVALIDE, "Billet d'un autre événement",
                    ScanResponse.of(CheckinResult.INVALIDE, sens,
                            "Ce billet concerne un autre événement", nom, activiteNom,
                            null, null, null, null, null));
        }
        if (qr.getStatut() == QrCode.QrStatus.REVOQUE || ticket.getStatut() == TicketStatus.ANNULE) {
            return record(event, activity, sens, me, qr.getId(), ticket.getId(),
                    CheckinResult.INVALIDE, "Billet annulé / révoqué",
                    ScanResponse.of(CheckinResult.INVALIDE, sens, "Ticket invalide (annulé)",
                            nom, activiteNom, null, null, null, null, null));
        }
        if (activity != null && !grantsAccess(ticket, activity)) {
            return record(event, activity, sens, me, qr.getId(), ticket.getId(),
                    CheckinResult.INVALIDE, "Billet non valable pour cette activité",
                    ScanResponse.of(CheckinResult.INVALIDE, sens,
                            "Billet non valable pour cette activité", nom, activiteNom,
                            pNom, catNom, num, null, null));
        }

        UUID activityId = activity != null ? activity.getId() : null;

        // The controller picks the direction (entrée / sortie) for every event.
        // A scan alternates ENTREE / SORTIE per (billet, activité); the ticket
        // stays EMISE so a legitimate re-entry after an exit is possible.
        var last = lastValid(ticket.getId(), activityId);
        boolean inside = last.map(c -> c.getSens() == CheckinDirection.ENTREE).orElse(false);
        long priorEntries = entryCount(ticket.getId(), activityId);

        if (sens == CheckinDirection.ENTREE) {
            if (inside) {
                return record(event, activity, CheckinDirection.ENTREE, me, qr.getId(), ticket.getId(),
                        CheckinResult.DEJA_UTILISE, "Entrée refusée : déjà à l'intérieur",
                        ScanResponse.of(CheckinResult.DEJA_UTILISE, CheckinDirection.ENTREE,
                                "Déjà à l'intérieur", nom, activiteNom, pNom, catNom, num, null,
                                last.get().getScannedAt()));
            }
            boolean reentree = priorEntries > 0;
            return record(event, activity, CheckinDirection.ENTREE, me, qr.getId(), ticket.getId(),
                    CheckinResult.VALIDE, reentree ? "Ré-entrée" : null,
                    new ScanResponse(CheckinResult.VALIDE, CheckinDirection.ENTREE, reentree,
                            reentree ? "Ré-entrée" : "Bienvenue", nom, activiteNom, pNom, catNom, num,
                            Instant.now(), null));
        }
        // SORTIE
        if (!inside) {
            return record(event, activity, CheckinDirection.SORTIE, me, qr.getId(), ticket.getId(),
                    CheckinResult.DEJA_UTILISE, "Sortie refusée : pas à l'intérieur",
                    ScanResponse.of(CheckinResult.DEJA_UTILISE, CheckinDirection.SORTIE,
                            "Pas à l'intérieur (déjà sorti ou jamais entré)", nom, activiteNom, pNom,
                            catNom, num, null, null));
        }
        return record(event, activity, CheckinDirection.SORTIE, me, qr.getId(), ticket.getId(),
                CheckinResult.VALIDE, null,
                ScanResponse.of(CheckinResult.VALIDE, CheckinDirection.SORTIE, "Sortie enregistrée",
                        nom, activiteNom, pNom, catNom, num, null, null));
    }

    private Optional<Checkin> lastValid(UUID ticketId, UUID activityId) {
        return activityId == null
                ? checkinRepository.findFirstByTicketIdAndActivityIdIsNullAndResultatOrderByScannedAtDesc(
                        ticketId, CheckinResult.VALIDE)
                : checkinRepository.findFirstByTicketIdAndActivityIdAndResultatOrderByScannedAtDesc(
                        ticketId, activityId, CheckinResult.VALIDE);
    }

    private long entryCount(UUID ticketId, UUID activityId) {
        return activityId == null
                ? checkinRepository.countByTicketIdAndActivityIdIsNullAndSensAndResultat(
                        ticketId, CheckinDirection.ENTREE, CheckinResult.VALIDE)
                : checkinRepository.countByTicketIdAndActivityIdAndSensAndResultat(
                        ticketId, activityId, CheckinDirection.ENTREE, CheckinResult.VALIDE);
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
                                           CheckinDirection sens, UUID me, String token) {
        Accreditation accr = accreditationRepository.findByQrToken(token).orElse(null);
        String nom = event.getNom();
        if (accr == null) {
            return record(event, activity, sens, me, null, null, null, CheckinResult.INVALIDE,
                    "QR code inconnu",
                    ScanResponse.of(CheckinResult.INVALIDE, sens, "QR code inconnu", nom,
                            activiteNom, null, null, null, null, null));
        }
        String label = "Badge · " + accr.fonctionLabel();
        String pNom = accr.getPersonneNom();
        String num = accr.getNumero();
        if (!accr.getEvent().getId().equals(event.getId())) {
            return recordAccr(event, activity, sens, me, accr, CheckinResult.INVALIDE,
                    "Badge d'un autre événement",
                    ScanResponse.of(CheckinResult.INVALIDE, sens, "Ce badge concerne un autre événement",
                            nom, activiteNom, pNom, label, num, null, null));
        }
        if (accr.getStatut() != Accreditation.AccreditationStatus.ACTIVE) {
            return recordAccr(event, activity, sens, me, accr, CheckinResult.INVALIDE, "Badge révoqué",
                    ScanResponse.of(CheckinResult.INVALIDE, sens, "Badge révoqué", nom, activiteNom,
                            pNom, label, num, null, null));
        }
        if (accr.getActivity() != null
                && (activity == null || !accr.getActivity().getId().equals(activity.getId()))) {
            return recordAccr(event, activity, sens, me, accr, CheckinResult.INVALIDE,
                    "Badge lié à l'activité « " + accr.getActivity().getTitre() + " »",
                    ScanResponse.of(CheckinResult.INVALIDE, sens, "Badge non valable pour cette activité",
                            nom, activiteNom, pNom, label, num, null, null));
        }
        // Badges allow re-entry — every scan is valid and simply logged.
        String message = sens == CheckinDirection.SORTIE ? "Sortie " + accr.fonctionLabel()
                : "Accès " + accr.fonctionLabel();
        return recordAccr(event, activity, sens, me, accr, CheckinResult.VALIDE, null,
                ScanResponse.of(CheckinResult.VALIDE, sens, message, nom, activiteNom, pNom, label,
                        num, Instant.now(), null));
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
        return eventFlow(eventId);
    }

    /** Same counters, scoped to a single activity of the event. */
    @Transactional(readOnly = true)
    public Map<String, Long> statsForActivity(UUID eventId, UUID activityId) {
        requireOrganiser(eventId);
        return activityFlow(eventId, activityId);
    }

    /** Real-time attendance: event-level flow + one line per activity. */
    @Transactional(readOnly = true)
    public AttendanceView attendance(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> ResourceNotFoundException.of("Événement", eventId));
        requireControl(event, currentUser.requireId());
        List<ActivityFlow> activites = activityRepository
                .findByEventIdOrderByDateDebutAscOrdreAsc(eventId).stream()
                .map(a -> new ActivityFlow(a.getId(), a.getTitre(), a.getDateDebut(),
                        a.getAcces() != null ? a.getAcces().name() : null,
                        activityFlow(eventId, a.getId())))
                .toList();
        return new AttendanceView(event.getId(), event.getNom(), eventFlow(eventId), activites);
    }

    private Map<String, Long> eventFlow(UUID eventId) {
        long entrees = checkinRepository.countByEventIdAndActivityIdIsNullAndSensAndResultat(
                eventId, CheckinDirection.ENTREE, CheckinResult.VALIDE);
        long sorties = checkinRepository.countByEventIdAndActivityIdIsNullAndSensAndResultat(
                eventId, CheckinDirection.SORTIE, CheckinResult.VALIDE);
        long distinctEntered = checkinRepository.countDistinctEnteredTickets(eventId);
        return Map.of(
                "valides", checkinRepository.countByEventIdAndResultat(eventId, CheckinResult.VALIDE),
                "dejaUtilises",
                checkinRepository.countByEventIdAndResultat(eventId, CheckinResult.DEJA_UTILISE),
                "invalides",
                checkinRepository.countByEventIdAndResultat(eventId, CheckinResult.INVALIDE),
                "entrees", entrees,
                "sorties", sorties,
                "presents", Math.max(0, entrees - sorties),
                "reentrees", Math.max(0, entrees - distinctEntered));
    }

    private Map<String, Long> activityFlow(UUID eventId, UUID activityId) {
        long entrees = checkinRepository.countByEventIdAndActivityIdAndSensAndResultat(
                eventId, activityId, CheckinDirection.ENTREE, CheckinResult.VALIDE);
        long sorties = checkinRepository.countByEventIdAndActivityIdAndSensAndResultat(
                eventId, activityId, CheckinDirection.SORTIE, CheckinResult.VALIDE);
        long distinctEntered =
                checkinRepository.countDistinctEnteredTicketsForActivity(eventId, activityId);
        return Map.of(
                "valides", checkinRepository.countByEventIdAndActivityIdAndResultat(
                        eventId, activityId, CheckinResult.VALIDE),
                "dejaUtilises", checkinRepository.countByEventIdAndActivityIdAndResultat(
                        eventId, activityId, CheckinResult.DEJA_UTILISE),
                "invalides", checkinRepository.countByEventIdAndActivityIdAndResultat(
                        eventId, activityId, CheckinResult.INVALIDE),
                "entrees", entrees,
                "sorties", sorties,
                "presents", Math.max(0, entrees - sorties),
                "reentrees", Math.max(0, entrees - distinctEntered));
    }

    public record AttendanceView(UUID eventId, String eventNom,
                                 Map<String, Long> event, List<ActivityFlow> activites) {
    }

    public record ActivityFlow(UUID id, String titre, Instant dateDebut, String acces,
                               Map<String, Long> flux) {
    }

    // --- helpers ---

    private ScanResponse record(Event event, EventActivity activity, CheckinDirection sens,
                                UUID scannedBy, UUID qrId, UUID ticketId, UUID accreditationId,
                                CheckinResult resultat, String detail, ScanResponse response) {
        Checkin checkin = new Checkin();
        checkin.setEventId(event.getId());
        checkin.setActivityId(activity != null ? activity.getId() : null);
        checkin.setSens(sens);
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
                        + " sens=" + sens + " resultat=" + resultat);
        return response;
    }

    private ScanResponse record(Event event, EventActivity activity, CheckinDirection sens,
                                UUID scannedBy, UUID qrId, UUID ticketId, CheckinResult resultat,
                                String detail, ScanResponse response) {
        return record(event, activity, sens, scannedBy, qrId, ticketId, null, resultat, detail,
                response);
    }

    private ScanResponse recordAccr(Event event, EventActivity activity, CheckinDirection sens,
                                    UUID scannedBy, Accreditation accr, CheckinResult resultat,
                                    String detail, ScanResponse response) {
        return record(event, activity, sens, scannedBy, null, null, accr.getId(), resultat, detail,
                response);
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
                              CheckinDirection sens, Instant scannedAt, UUID scannedBy,
                              String detail) {
        static CheckinView from(Checkin c) {
            return new CheckinView(c.getId(), c.getTicketId(), c.getActivityId(), c.getResultat(),
                    c.getSens(), c.getScannedAt(), c.getScannedBy(), c.getDetail());
        }
    }
}
