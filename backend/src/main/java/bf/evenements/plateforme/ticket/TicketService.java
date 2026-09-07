package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.web.QrImages;
import bf.evenements.plateforme.qrcode.QrCode;
import bf.evenements.plateforme.qrcode.QrCodeService;
import bf.evenements.plateforme.rbac.Permissions;
import bf.evenements.plateforme.ticket.dto.TicketResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final QrCodeService qrCodeService;
    private final TicketPdfService pdfService;
    private final CurrentUserProvider currentUser;

    @Transactional(readOnly = true)
    public List<TicketResponse> myTickets() {
        return ticketRepository.findByOrderUserIdOrderByCreatedAtDesc(currentUser.requireId()).stream()
                .map(TicketResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse get(UUID id) {
        return TicketResponse.from(loadAccessible(id));
    }

    @Transactional(readOnly = true)
    public byte[] qrPng(UUID id) {
        QrCode qr = qrCodeService.requireForTicket(loadAccessible(id).getId());
        return QrImages.png(qr.getToken(), 320);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(UUID id) {
        Ticket ticket = loadAccessible(id);
        QrCode qr = qrCodeService.requireForTicket(ticket.getId());
        return pdfService.render(ticket, qr.getToken());
    }

    private Ticket loadAccessible(UUID id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Billet", id));
        UUID me = currentUser.requireId();
        boolean owner = ticket.getOrder().getUser().getId().equals(me);
        boolean organiser = ticket.getEvent().isOwnedBy(me);
        boolean staff = currentUser.hasAuthority(Permissions.CHECKIN_SCAN);
        if (!owner && !organiser && !staff) {
            throw new AccessDeniedException("Accès au billet refusé.");
        }
        return ticket;
    }
}
