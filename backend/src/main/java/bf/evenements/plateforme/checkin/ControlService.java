package bf.evenements.plateforme.checkin;

import bf.evenements.plateforme.accreditation.Accreditation;
import bf.evenements.plateforme.accreditation.AccreditationRepository;
import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.auth.AuthService;
import bf.evenements.plateforme.checkin.dto.ScanRequest;
import bf.evenements.plateforme.checkin.dto.ScanResponse;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.web.References;
import bf.evenements.plateforme.event.ActivityAccess;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventActivity;
import bf.evenements.plateforme.event.EventActivityRepository;
import bf.evenements.plateforme.event.EventRepository;
import bf.evenements.plateforme.event.EventService;
import bf.evenements.plateforme.qrcode.QrCode;
import bf.evenements.plateforme.qrcode.QrCodeService;
import bf.evenements.plateforme.ticket.EventTicket;
import bf.evenements.plateforme.ticket.EventTicketRepository;
import bf.evenements.plateforme.ticket.Ticket;
import bf.evenements.plateforme.ticket.TicketOrderService;
import bf.evenements.plateforme.ticket.TicketRepository;
import bf.evenements.plateforme.ticket.TicketScope;
import bf.evenements.plateforme.ticket.TicketStatus;
import bf.evenements.plateforme.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Field-control extras around the scanner: offline pack and synchronisation
 * (CTRL-01), on-site registration (CTRL-03), self check-in by room QR (CTRL-04)
 * and assignment of agents to rooms / slots (CTRL-05).
 */
@Service
@RequiredArgsConstructor
public class ControlService {

    private final CheckinService checkinService;
    private final CheckinRepository checkinRepository;
    private final EventRepository eventRepository;
    private final EventActivityRepository activityRepository;
    private final EventStaffRepository staffRepository;
    private final EventService eventService;
    private final TicketRepository ticketRepository;
    private final EventTicketRepository eventTicketRepository;
    private final TicketOrderService ticketOrderService;
    private final QrCodeService qrCodeService;
    private final AccreditationRepository accreditationRepository;
    private final AuthService authService;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;
    private final NamedParameterJdbcTemplate jdbc;

    // ------------------------------------------------------------ offline pack

    /** One valid credential, identified by the SHA-256 (hex) of its QR token. */
    public record OfflineCredential(String h, String numero, String nom, String categorie,
                                    /** Activities it opens; empty = the whole event. */
                                    List<UUID> activites) {
    }

    /** Ticket currently inside an activity (or the event when {@code activityId} is null). */
    public record OfflineInside(String h, @Nullable UUID activityId) {
    }

    public record OfflinePack(UUID eventId, String eventNom, Instant genereLe,
                              List<OfflineCredential> billets, List<OfflineInside> presents) {
    }

    /**
     * Everything a scanner needs to keep validating without network: hashed
     * tokens of valid tickets / badges and who is currently inside. Raw tokens
     * never leave the server.
     */
    @Transactional(readOnly = true)
    public OfflinePack offlinePack(UUID eventId) {
        Event event = controllable(eventId);
        List<OfflineCredential> out = new ArrayList<>();
        jdbc.query("""
                select q.token, t.numero, coalesce(t.participant_nom, '') as nom, et.nom as categorie,
                       et.portee, et.id as cat
                from tickets t
                join qr_codes q on q.ticket_id = t.id and q.statut = 'ACTIVE'
                join event_tickets et on et.id = t.event_ticket_id
                where t.event_id = :e and t.statut <> 'ANNULE'
                """, new MapSqlParameterSource("e", eventId), rs -> {
            List<UUID> acts = "ACTIVITE".equals(rs.getString("portee"))
                    ? jdbc.queryForList("select activity_id from event_ticket_activities where event_ticket_id = :c",
                            new MapSqlParameterSource("c", rs.getObject("cat", UUID.class)), UUID.class)
                    : List.of();
            out.add(new OfflineCredential(sha256(rs.getString("token")), rs.getString("numero"),
                    rs.getString("nom"), rs.getString("categorie"), acts));
        });
        for (Accreditation a : accreditationRepository.findByEventIdOrderByCreatedAtAsc(eventId)) {
            if (a.getStatut() != null && "ACTIVE".equals(a.getStatut().name())) {
                out.add(new OfflineCredential(sha256(a.getQrToken()), a.getNumero(), a.getPersonneNom(),
                        "Badge " + a.getFonction().name().toLowerCase(),
                        a.getActivity() == null ? List.of() : List.of(a.getActivity().getId())));
            }
        }
        // Last valid scan per (ticket, activity): inside when it is an entry.
        List<OfflineInside> inside = jdbc.query("""
                select q.token, x.activity_id from (
                    select distinct on (c.ticket_id, c.activity_id) c.ticket_id, c.activity_id, c.sens
                    from checkins c
                    where c.event_id = :e and c.resultat = 'VALIDE' and c.ticket_id is not null
                    order by c.ticket_id, c.activity_id, c.scanned_at desc
                ) x join qr_codes q on q.ticket_id = x.ticket_id
                where x.sens = 'ENTREE'
                """, new MapSqlParameterSource("e", eventId),
                (rs, i) -> new OfflineInside(sha256(rs.getString("token")), rs.getObject("activity_id", UUID.class)));
        auditService.record(currentUser.requireId(), currentUser.require().email(), "CHECKIN_OFFLINE_PACK",
                "Event", eventId.toString(), null, "billets=" + out.size());
        return new OfflinePack(event.getId(), event.getNom(), Instant.now(), out, inside);
    }

    // ------------------------------------------------------------ sync

    public record SyncResult(String clientRef, String resultat, String message, String participantNom,
                             boolean dejaSynchronise) {
    }

    /**
     * Replays scans recorded offline, in order. Each one is processed in its own
     * transaction; a scan already synchronised ({@code clientRef} known) is not
     * recorded twice.
     */
    public List<SyncResult> sync(List<ScanRequest> scans) {
        List<SyncResult> results = new ArrayList<>(scans.size());
        scans.stream()
                .sorted((a, b) -> {
                    Instant x = a.scannedAt() == null ? Instant.EPOCH : a.scannedAt();
                    Instant y = b.scannedAt() == null ? Instant.EPOCH : b.scannedAt();
                    return x.compareTo(y);
                })
                .forEach(s -> results.add(syncOne(s)));
        return results;
    }

    private SyncResult syncOne(ScanRequest s) {
        if (s.clientRef() != null && !s.clientRef().isBlank()) {
            var known = checkinRepository.findFirstByClientRef(s.clientRef());
            if (known.isPresent()) {
                return new SyncResult(s.clientRef(), known.get().getResultat().name(),
                        known.get().getDetail(), null, true);
            }
        }
        try {
            ScanResponse r = checkinService.scan(s, CheckinCanal.HORS_LIGNE);
            return new SyncResult(s.clientRef(), r.resultat().name(), r.message(), r.participantNom(), false);
        } catch (RuntimeException e) {
            return new SyncResult(s.clientRef(), "ERREUR", e.getMessage(), null, false);
        }
    }

    // ------------------------------------------------------------ on-site registration

    public record OnSiteResponse(String numeroBillet, ScanResponse scan) {
    }

    /**
     * A visitor shows up without registering: the agent creates a free ticket
     * (name + phone) for the session — or the event — and lets him in at once.
     * The ticket is marked « sur place » in the statistics.
     */
    @Transactional
    public OnSiteResponse registerOnSite(UUID eventId, @Nullable UUID activityId, String nom, String telephone) {
        Event event = controllable(eventId);
        EventActivity activity = null;
        if (activityId != null) {
            activity = activityRepository.findById(activityId)
                    .filter(a -> a.getEvent().getId().equals(eventId))
                    .orElseThrow(() -> ResourceNotFoundException.of("Activité", activityId));
        }
        var allowed = checkinService.assignedActivities(event, currentUser.requireId());
        if (!allowed.isEmpty() && (activityId == null || !allowed.contains(activityId))) {
            throw new AccessDeniedException("Vous n'êtes pas affecté à cette activité.");
        }
        EventTicket category = freeCategoryFor(event, activity);
        User holder = authService.guestUserFor(telephone.trim(), nom.trim());
        Ticket ticket = ticketOrderService.issueOnSite(category, holder, nom.trim(), telephone.trim());
        QrCode qr = qrCodeService.issueFor(ticket);
        auditService.record(currentUser.requireId(), currentUser.require().email(), "TICKET_ON_SITE",
                "Ticket", ticket.getId().toString(), null,
                "event=" + event.getNom() + (activity != null ? " activite=" + activity.getTitre() : ""));
        ScanResponse scan = checkinService.scan(
                new ScanRequest(qr.getToken(), eventId, activityId, CheckinDirection.ENTREE));
        return new OnSiteResponse(ticket.getNumero(), scan);
    }

    private EventTicket freeCategoryFor(Event event, @Nullable EventActivity activity) {
        if (activity != null && activity.getAcces() == ActivityAccess.GRATUIT && activity.getFreeTicketId() != null) {
            return eventTicketRepository.findById(activity.getFreeTicketId()).orElseThrow();
        }
        List<EventTicket> free = eventTicketRepository.findByEventIdOrderByOrdreAscPrixMontantAsc(event.getId()).stream()
                .filter(t -> t.isActif() && t.getPrixMontant().signum() == 0)
                .toList();
        if (activity != null) {
            var forActivity = free.stream()
                    .filter(t -> t.getPortee() == TicketScope.ACTIVITE
                            && t.getActivities().stream().anyMatch(a -> a.getId().equals(activity.getId())))
                    .findFirst();
            if (forActivity.isPresent()) {
                return forActivity.get();
            }
            if (activity.getAcces() == ActivityAccess.PAYANT) {
                throw new BusinessException("ON_SITE_PAID",
                        "Activité payante : l'inscription sur place se fait au guichet de paiement.");
            }
        }
        return free.stream().filter(t -> t.getPortee() == TicketScope.EVENEMENT).findFirst()
                .orElseThrow(() -> new BusinessException("ON_SITE_NO_FREE_TICKET",
                        "Aucun billet gratuit n'est configuré : créez-en un dans la billetterie "
                                + "pour permettre l'inscription sur place."));
    }

    // ------------------------------------------------------------ self check-in

    public record SelfCheckinConfig(UUID activityId, boolean actif, @Nullable String jeton) {
    }

    /** Organiser: turns the room-QR self check-in (backup mode) on or off for a session. */
    @Transactional
    public SelfCheckinConfig configureSelfCheckin(UUID eventId, UUID activityId, boolean actif) {
        eventService.loadManaged(eventId);
        EventActivity a = activityRepository.findById(activityId)
                .filter(x -> x.getEvent().getId().equals(eventId))
                .orElseThrow(() -> ResourceNotFoundException.of("Activité", activityId));
        a.setAutoEnregistrement(actif);
        if (actif && a.getAutoJeton() == null) {
            a.setAutoJeton(References.unique("AE", 16,
                    j -> activityRepository.existsByAutoJeton(j)));
        }
        return new SelfCheckinConfig(a.getId(), a.isAutoEnregistrement(), a.getAutoJeton());
    }

    public record SelfCheckinInfo(String evenement, String activite, @Nullable String salle,
                                  Instant debut, @Nullable Instant fin) {
    }

    @Transactional(readOnly = true)
    public SelfCheckinInfo selfCheckinInfo(String jeton) {
        EventActivity a = selfCheckinActivity(jeton);
        return new SelfCheckinInfo(a.getEvent().getNom(), a.getTitre(), a.getSalle(), a.getDateDebut(), a.getDateFin());
    }

    /**
     * Participant side: confirms his presence by scanning the room's QR and
     * giving the phone number used for registration. Only while the session is
     * running (30 min before its start → its end).
     */
    @Transactional
    public ScanResponse selfCheckin(String jeton, String telephone) {
        EventActivity a = selfCheckinActivity(jeton);
        Instant now = Instant.now();
        Instant end = a.getDateFin() != null ? a.getDateFin() : a.getDateDebut().plus(Duration.ofHours(4));
        if (now.isBefore(a.getDateDebut().minus(Duration.ofMinutes(30))) || now.isAfter(end)) {
            throw new BusinessException("SELF_CHECKIN_CLOSED",
                    "L'auto-enregistrement n'est ouvert que pendant la session.");
        }
        String digits = telephone.replaceAll("[^0-9]", "");
        if (digits.length() < 6) {
            throw new BusinessException("PHONE_INVALID", "Numéro de téléphone invalide.");
        }
        UUID eventId = a.getEvent().getId();
        List<UUID> tickets = jdbc.queryForList("""
                select t.id from tickets t
                join ticket_orders o on o.id = t.order_id
                join users u on u.id = o.user_id
                join event_tickets et on et.id = t.event_ticket_id
                where t.event_id = :e and t.statut <> 'ANNULE'
                  and right(regexp_replace(coalesce(t.participant_telephone, u.phone, ''), '[^0-9]', '', 'g'), 8)
                      = right(:tel, 8)
                  and (et.portee = 'EVENEMENT' or exists (
                        select 1 from event_ticket_activities eta
                        where eta.event_ticket_id = et.id and eta.activity_id = :a))
                order by (et.portee = 'ACTIVITE') desc, t.created_at
                """, new MapSqlParameterSource("e", eventId).addValue("a", a.getId()).addValue("tel", digits),
                UUID.class);
        if (tickets.isEmpty()) {
            throw new BusinessException("NOT_REGISTERED",
                    "Aucune inscription à cette session pour ce numéro. Adressez-vous à l'accueil.");
        }
        Ticket ticket = ticketRepository.findById(tickets.get(0)).orElseThrow();
        boolean already = checkinRepository.findFirstByTicketIdAndActivityIdAndResultatOrderByScannedAtDesc(
                ticket.getId(), a.getId(), CheckinResult.VALIDE).isPresent();
        if (!already) {
            Checkin c = new Checkin();
            c.setEventId(eventId);
            c.setActivityId(a.getId());
            c.setTicketId(ticket.getId());
            c.setSens(CheckinDirection.ENTREE);
            c.setResultat(CheckinResult.VALIDE);
            c.setCanal(CheckinCanal.AUTO_ENREGISTREMENT);
            c.setScannedAt(now);
            c.setDetail("Auto-enregistrement");
            checkinRepository.save(c);
        }
        if (ticket.getStatut() == TicketStatus.EMISE) {
            ticket.setStatut(TicketStatus.UTILISE);
        }
        return ScanResponse.of(already ? CheckinResult.DEJA_UTILISE : CheckinResult.VALIDE,
                CheckinDirection.ENTREE, already ? "Présence déjà enregistrée" : "Présence enregistrée, bienvenue",
                a.getEvent().getNom(), a.getTitre(), ticket.getParticipantNom(), ticket.getEventTicket().getNom(),
                ticket.getNumero(), now, null);
    }

    private EventActivity selfCheckinActivity(String jeton) {
        return activityRepository.findByAutoJeton(jeton)
                .filter(EventActivity::isAutoEnregistrement)
                .orElseThrow(() -> new ResourceNotFoundException("Auto-enregistrement indisponible."));
    }

    // ------------------------------------------------------------ staff assignment

    /** Restricts a control agent to some activities (empty = whole event). */
    @Transactional
    public Set<UUID> assignActivities(UUID eventId, UUID userId, List<UUID> activityIds) {
        eventService.loadManaged(eventId);
        EventStaff staff = staffRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent introuvable pour cet événement."));
        staff.getActivities().clear();
        for (UUID id : activityIds) {
            EventActivity a = activityRepository.findById(id)
                    .filter(x -> x.getEvent().getId().equals(eventId))
                    .orElseThrow(() -> ResourceNotFoundException.of("Activité", id));
            staff.getActivities().add(a);
        }
        auditService.record(currentUser.requireId(), currentUser.require().email(), "STAFF_ACTIVITIES",
                "EventStaff", staff.getId().toString(), null, "activites=" + activityIds.size());
        return staff.getActivities().stream().map(EventActivity::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    // ------------------------------------------------------------ helpers

    private Event controllable(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> ResourceNotFoundException.of("Événement", eventId));
        checkinService.requireControlOf(event);
        return event;
    }

    static String sha256(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
