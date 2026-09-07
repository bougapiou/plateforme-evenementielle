package bf.evenements.plateforme.ticket.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID eventId,
        UUID structureId,
        @Size(max = 200) String acheteurNom,
        @Email @Size(max = 180) String acheteurEmail,
        @NotEmpty @Valid List<Line> lignes) {

    public record Line(
            @NotNull UUID eventTicketId,
            @NotNull @Positive Integer quantite) {
    }
}
