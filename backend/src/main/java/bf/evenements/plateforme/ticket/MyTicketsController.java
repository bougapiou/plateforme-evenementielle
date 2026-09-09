package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.ticket.dto.TicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "Billets électroniques")
public class MyTicketsController {

    private final TicketService ticketService;

    @GetMapping("/my")
    @Operation(summary = "Mes billets électroniques")
    public List<TicketResponse> myTickets() {
        return ticketService.myTickets();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un billet")
    public TicketResponse get(@PathVariable UUID id) {
        return ticketService.get(id);
    }

    @GetMapping(value = "/{id}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Image QR code du billet")
    public ResponseEntity<byte[]> qr(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(ticketService.qrPng(id));
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Billet électronique en PDF")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        byte[] pdf = ticketService.pdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition",
                        ContentDisposition.attachment().filename("billet-" + id + ".pdf").build().toString())
                .body(pdf);
    }
}
