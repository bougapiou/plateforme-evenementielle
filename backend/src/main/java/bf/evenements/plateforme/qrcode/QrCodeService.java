package bf.evenements.plateforme.qrcode;

import bf.evenements.plateforme.common.security.TokenHasher;
import bf.evenements.plateforme.ticket.Ticket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QrCodeService {

    private final QrCodeRepository repository;
    private final TokenHasher tokenHasher;

    /** Issues a QR code for a freshly generated ticket (idempotent). */
    @Transactional
    public QrCode issueFor(Ticket ticket) {
        return repository.findByTicketId(ticket.getId()).orElseGet(() -> {
            QrCode qr = new QrCode();
            qr.setTicket(ticket);
            qr.setToken(uniqueToken());
            qr.setStatut(QrCode.QrStatus.ACTIVE);
            return repository.save(qr);
        });
    }

    @Transactional(readOnly = true)
    public QrCode requireForTicket(java.util.UUID ticketId) {
        return repository.findByTicketId(ticketId)
                .orElseThrow(() -> new bf.evenements.plateforme.common.exception.ResourceNotFoundException(
                        "QR code introuvable pour le billet " + ticketId));
    }

    private String uniqueToken() {
        String token;
        do {
            token = "QR" + tokenHasher.generateOpaqueToken().substring(0, 40);
        } while (repository.existsByToken(token));
        return token;
    }
}
