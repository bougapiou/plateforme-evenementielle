package bf.evenements.plateforme.checkin;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.checkin.dto.ScanRequest;
import bf.evenements.plateforme.checkin.dto.ScanResponse;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventRepository;
import bf.evenements.plateforme.qrcode.QrCode;
import bf.evenements.plateforme.qrcode.QrCodeRepository;
import bf.evenements.plateforme.rbac.Permissions;
import bf.evenements.plateforme.ticket.Ticket;
import bf.evenements.plateforme.ticket.TicketRepository;
import bf.evenements.plateforme.ticket.TicketStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;

    @Transactional
    public ScanResponse scan(ScanRequest request) {
        UUID me = currentUser.requireId();
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> ResourceNotFoundException.of("Événement", request.eventId()));
        requireControl(event, me);

        QrCode qr = qrCodeRepository.findByToken(request.token().trim()).orElse(null);
        if (qr == null) {
            return record(event, me, null, null, CheckinResult.INVALIDE,
                    "QR code inconnu", ScanResponse.invalide("Ticket invalide", event.getNom()));
        }
        Ticket ticket = ticketRepository.findById(qr.getTicket().getId()).orElseThrow();

        if (!ticket.getEvent().getId().equals(event.getId())) {
            return record(event, me, qr.getId(), ticket.getId(), CheckinResult.INVALIDE,
                    "Billet d'un autre événement",
                    ScanResponse.invalide("Ce billet concerne un autre événement", event.getNom()));
        }
        if (qr.getStatut() == QrCode.QrStatus.REVOQUE || ticket.getStatut() == TicketStatus.ANNULE) {
            return record(event, me, qr.getId(), ticket.getId(), CheckinResult.INVALIDE,
                    "Billet annulé / révoqué",
                    ScanResponse.invalide("Ticket invalide (annulé)", event.getNom()));
        }

        var existing = checkinRepository
                .findFirstByTicketIdAndResultatOrderByScannedAtAsc(ticket.getId(), CheckinResult.VALIDE);
        if (existing.isPresent()) {
            ScanResponse resp = new ScanResponse(CheckinResult.DEJA_UTILISE,
                    "Ticket déjà utilisé", event.getNom(), ticket.getParticipantNom(),
                    ticket.getEventTicket().getNom(), ticket.getNumero(), null,
                    existing.get().getScannedAt());
            return record(event, me, qr.getId(), ticket.getId(), CheckinResult.DEJA_UTILISE,
                    "Contrôle déjà effectué le " + existing.get().getScannedAt(), resp);
        }

        // valid entry
        ticket.setStatut(TicketStatus.UTILISE);
        Instant now = Instant.now();
        ScanResponse resp = new ScanResponse(CheckinResult.VALIDE, "Bienvenue", event.getNom(),
                ticket.getParticipantNom(), ticket.getEventTicket().getNom(), ticket.getNumero(),
                now, null);
        return record(event, me, qr.getId(), ticket.getId(), CheckinResult.VALIDE, null, resp);
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

    private ScanResponse record(Event event, UUID scannedBy, UUID qrId, UUID ticketId,
                                CheckinResult resultat, String detail, ScanResponse response) {
        Checkin checkin = new Checkin();
        checkin.setEventId(event.getId());
        checkin.setScannedBy(scannedBy);
        checkin.setQrCodeId(qrId);
        checkin.setTicketId(ticketId);
        checkin.setResultat(resultat);
        checkin.setScannedAt(Instant.now());
        checkin.setDetail(detail);
        checkinRepository.save(checkin);
        auditService.record(scannedBy, currentUser.current().map(u -> u.email()).orElse(null),
                "CHECKIN_SCAN", "Checkin", checkin.getId().toString(), null,
                "event=" + event.getNom() + " resultat=" + resultat);
        return response;
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

    public record CheckinView(UUID id, UUID ticketId, CheckinResult resultat, Instant scannedAt,
                              UUID scannedBy, String detail) {
        static CheckinView from(Checkin c) {
            return new CheckinView(c.getId(), c.getTicketId(), c.getResultat(), c.getScannedAt(),
                    c.getScannedBy(), c.getDetail());
        }
    }
}
