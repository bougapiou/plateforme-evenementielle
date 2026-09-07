package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.ticket.dto.TicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "Mes billets")
public class MyTicketsController {

    private final TicketService ticketService;

    @GetMapping("/my")
    @Operation(summary = "Mes billets électroniques")
    public List<TicketResponse> myTickets() {
        return ticketService.myTickets();
    }
}
