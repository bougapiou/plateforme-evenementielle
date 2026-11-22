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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final bf.evenements.plateforme.sensor.PassageCapteurRepository passageRepository;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;
    private final bf.evenements.plateforme.user.UserRepository userRepository;

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
        var allowed = assignedActivities(event, currentUser.requireId());
        return activityRepository.findByEventIdOrderByDateDebutAscOrdreAsc(eventId).stream()
                .filter(a -> allowed.isEmpty() || allowed.contains(a.getId()))
                .map(ActivityResponse::from).toList();
    }

    /** Offline metadata of the scan being processed (channel, device id, scan time). */
    private record ScanMeta(CheckinCanal canal, String clientRef, Instant scannedAt) {
    }

    private static final ThreadLocal<ScanMeta> META = new ThreadLocal<>();

    /** How far back an offline scan may be dated when synchronised. */
    private static final java.time.Duration MAX_OFFLINE_AGE = java.time.Duration.ofDays(3);

    @Transactional
    public ScanResponse scan(ScanRequest request) {
        return scan(request, CheckinCanal.SCAN);
    }

    /**
     * Scans with an explicit channel. For offline scans the device time is kept
     * (clamped to the last {@link #MAX_OFFLINE_AGE}) and {@code clientRef} makes
     * a replay return nothing new.
     */
    @Transactional
    public ScanResponse scan(ScanRequest request, CheckinCanal canal) {
        Instant at = Instant.now();
        if (request.scannedAt() != null && request.scannedAt().isBefore(at)
                && request.scannedAt().isAfter(at.minus(MAX_OFFLINE_AGE))) {
            at = request.scannedAt();
        }
        META.set(new ScanMeta(canal, request.clientRef(), at));
        try {
            return doScan(request);
        } finally {
            META.remove();
        }
    }

    private ScanResponse doScan(ScanRequest request) {
        UUID me = currentUser.requireId();
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> ResourceNotFoundException.of("Événement", request.eventId()));
        requireControl(event, me);
        CheckinDirection sens = request.resolvedSens();
        requireAssignedActivity(event, me, request.activityId());

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
                            now(), null));
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
                        num, now(), null));
    }

    @Transactional(readOnly = true)
    public PageResponse<CheckinView> list(UUID eventId, Pageable pageable) {
        requireOrganiser(eventId);
        var page = checkinRepository.findByEventIdOrderByScannedAtDesc(eventId, pageable);
        var agentIds = page.getContent().stream().map(Checkin::getScannedBy)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Map<UUID, String> agents = new java.util.HashMap<>();
        userRepository.findAllById(agentIds).forEach(u -> agents.put(u.getId(), u.getFullName()));
        Map<UUID, EventActivity> acts = new java.util.HashMap<>();
        activityRepository.findByEventIdOrderByDateDebutAscOrdreAsc(eventId).forEach(a -> acts.put(a.getId(), a));
        return PageResponse.of(page, c -> CheckinView.from(c, agents.get(c.getScannedBy()),
                c.getActivityId() == null ? null : acts.get(c.getActivityId())));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> stats(UUID eventId) {
        requireControlAccess(eventId);
        return eventFlow(eventId);
    }

    /** Same counters, scoped to a single activity of the event. */
    @Transactional(readOnly = true)
    public Map<String, Long> statsForActivity(UUID eventId, UUID activityId) {
        requireControlAccess(eventId);
        return activityFlow(eventId, activityId);
    }

    /**
     * Full per-ticket breakdown — entries, exits, re-entries and current state,
     * one row per ticket number — for the event's general entry or one activity.
     * Most recently scanned tickets first.
     */
    @Transactional(readOnly = true)
    public List<TicketFlowView> ticketDetails(UUID eventId, UUID activityId) {
        requireControlAccess(eventId);
        List<Checkin> scans = activityId == null
                ? checkinRepository.findByEventIdAndActivityIdIsNullAndTicketIdIsNotNullOrderByScannedAtAsc(
                        eventId)
                : checkinRepository.findByEventIdAndActivityIdAndTicketIdIsNotNullOrderByScannedAtAsc(
                        eventId, activityId);
        if (scans.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Checkin>> byTicket = scans.stream()
                .collect(Collectors.groupingBy(Checkin::getTicketId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, Ticket> tickets = ticketRepository.findAllById(byTicket.keySet()).stream()
                .collect(Collectors.toMap(Ticket::getId, t -> t));

        List<TicketFlowView> views = new ArrayList<>();
        for (var entry : byTicket.entrySet()) {
            List<Checkin> forTicket = entry.getValue(); // ascending by scannedAt
            long entrees = forTicket.stream().filter(c -> c.getResultat() == CheckinResult.VALIDE
                    && c.getSens() == CheckinDirection.ENTREE).count();
            long sorties = forTicket.stream().filter(c -> c.getResultat() == CheckinResult.VALIDE
                    && c.getSens() == CheckinDirection.SORTIE).count();
            long reentrees = Math.max(0, entrees - 1);
            Checkin lastValid = forTicket.stream()
                    .filter(c -> c.getResultat() == CheckinResult.VALIDE)
                    .reduce((a, b) -> b).orElse(null);
            boolean present = lastValid != null && lastValid.getSens() == CheckinDirection.ENTREE;
            Instant dernierScan = forTicket.get(forTicket.size() - 1).getScannedAt();
            Ticket t = tickets.get(entry.getKey());
            views.add(new TicketFlowView(entry.getKey(), t != null ? t.getNumero() : "—",
                    t != null ? t.getParticipantNom() : null,
                    t != null ? t.getEventTicket().getNom() : null,
                    entrees, sorties, reentrees, present, dernierScan));
        }
        views.sort(Comparator.comparing(TicketFlowView::dernierScan).reversed());
        return views;
    }

    /** Real-time attendance: event-level flow + one line per activity. */
    @Transactional(readOnly = true)
    public AttendanceView attendance(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> ResourceNotFoundException.of("Événement", eventId));
        requireControl(event, currentUser.requireId());
        return buildAttendanceView(event);
    }

    /**
     * Same view as {@link #attendance}, but for anyone — no login, no control
     * permission. Only reachable for events already visible on the public site
     * (see {@link bf.evenements.plateforme.event.EventStatus#isPubliclyVisible()}),
     * so a draft or unvalidated event's live counts stay private. Meant for a
     * dedicated link (e.g. a screen at the venue entrance), one event at a time —
     * there is no public listing of "controllable" events.
     */
    @Transactional(readOnly = true)
    public AttendanceView publicAttendance(String slug) {
        Event event = eventRepository.findBySlug(slug)
                .filter(e -> e.getStatut().isPubliclyVisible())
                .orElseThrow(() -> new ResourceNotFoundException("Événement introuvable : " + slug));
        return buildAttendanceView(event);
    }

    private AttendanceView buildAttendanceView(Event event) {
        UUID eventId = event.getId();
        List<ActivityFlow> activites = activityRepository
                .findByEventIdOrderByDateDebutAscOrdreAsc(eventId).stream()
                .map(a -> new ActivityFlow(a.getId(), a.getTitre(), a.getDateDebut(),
                        a.getAcces() != null ? a.getAcces().name() : null,
                        activityFlow(eventId, a.getId())))
                .toList();
        long in = passageRepository.totalForEvent(eventId, CheckinDirection.ENTREE);
        long out = passageRepository.totalForEvent(eventId, CheckinDirection.SORTIE);
        Map<String, Long> physique = Map.of("entrees", in, "sorties", out,
                "presents", Math.max(0, in - out));
        return new AttendanceView(event.getId(), event.getNom(), eventFlow(eventId), activites,
                physique);
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

    /** {@code comptagePhysique}: anonymous laser-sensor counts, kept apart from ticket scans. */
    public record AttendanceView(UUID eventId, String eventNom,
                                 Map<String, Long> event, List<ActivityFlow> activites,
                                 Map<String, Long> comptagePhysique) {
    }

    public record ActivityFlow(UUID id, String titre, Instant dateDebut, String acces,
                               Map<String, Long> flux) {
    }

    /** One row per ticket: its number, holder, category and its full entry/exit history. */
    public record TicketFlowView(UUID ticketId, String numero, String participantNom,
                                 String categorieNom, long entrees, long sorties, long reentrees,
                                 boolean present, Instant dernierScan) {
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
        ScanMeta meta = META.get();
        checkin.setScannedAt(meta != null ? meta.scannedAt() : Instant.now());
        checkin.setDetail(detail);
        if (meta != null) {
            checkin.setCanal(meta.canal());
            checkin.setClientRef(meta.clientRef());
        }
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

    private static Instant now() {
        ScanMeta meta = META.get();
        return meta != null ? meta.scannedAt() : Instant.now();
    }

    /**
     * Activities a staff member is restricted to (rooms / time slots). Empty =
     * no restriction (organiser, admin, or agent assigned to the whole event).
     */
    public java.util.Set<UUID> assignedActivities(Event event, UUID userId) {
        if (event.isOwnedBy(userId) || currentUser.hasAuthority(Permissions.EVENT_VALIDATE)) {
            return java.util.Set.of();
        }
        return staffRepository.findByEventIdAndUserId(event.getId(), userId)
                .map(s -> s.getActivities().stream().map(EventActivity::getId)
                        .collect(java.util.stream.Collectors.toSet()))
                .orElse(java.util.Set.of());
    }

    private void requireAssignedActivity(Event event, UUID userId, UUID activityId) {
        var allowed = assignedActivities(event, userId);
        if (!allowed.isEmpty() && (activityId == null || !allowed.contains(activityId))) {
            throw new AccessDeniedException(
                    "Vous êtes affecté à d'autres salles : choisissez une de vos activités.");
        }
    }

    /** Owner, assigned control staff or admin of the event — else access denied. */
    public void requireControlOf(Event event) {
        requireControl(event, currentUser.requireId());
    }

    private void requireControl(Event event, UUID userId) {
        boolean allowed = event.isOwnedBy(userId)
                || staffRepository.existsByEventIdAndUserId(event.getId(), userId)
                || currentUser.hasAuthority(Permissions.EVENT_VALIDATE);
        if (!allowed) {
            throw new AccessDeniedException("Vous n'êtes pas habilité à contrôler cet événement.");
        }
    }

    /** Owner, assigned control staff, or admin — same audience as {@link #requireControl}. */
    private void requireControlAccess(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> ResourceNotFoundException.of("Événement", eventId));
        requireControl(event, currentUser.requireId());
    }

    private void requireOrganiser(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> ResourceNotFoundException.of("Événement", eventId));
        if (!event.isOwnedBy(currentUser.requireId())
                && !currentUser.hasAuthority(Permissions.EVENT_VALIDATE)) {
            throw new AccessDeniedException("Accès aux contrôles refusé.");
        }
    }

    /** One line of the scan log: who scanned, where (activity / room), when, through which channel. */
    public record CheckinView(UUID id, UUID ticketId, UUID activityId, CheckinResult resultat,
                              CheckinDirection sens, Instant scannedAt, UUID scannedBy,
                              String detail, String agentNom, String activiteTitre, String salle,
                              CheckinCanal canal) {
        static CheckinView from(Checkin c, String agentNom, EventActivity a) {
            return new CheckinView(c.getId(), c.getTicketId(), c.getActivityId(), c.getResultat(),
                    c.getSens(), c.getScannedAt(), c.getScannedBy(), c.getDetail(), agentNom,
                    a == null ? null : a.getTitre(), a == null ? null : a.getSalle(), c.getCanal());
        }
    }
}
