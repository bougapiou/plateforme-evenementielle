package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.ticket.dto.TicketResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final CurrentUserProvider currentUser;

    @Transactional(readOnly = true)
    public List<TicketResponse> myTickets() {
        return ticketRepository.findByOrderUserIdOrderByCreatedAtDesc(currentUser.requireId()).stream()
                .map(TicketResponse::from)
                .toList();
    }
}
