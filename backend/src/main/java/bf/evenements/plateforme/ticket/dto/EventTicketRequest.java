package bf.evenements.plateforme.ticket.dto;

import bf.evenements.plateforme.ticket.TicketScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record EventTicketRequest(
        @NotBlank @Size(max = 120) String nom,
        @Size(max = 1000) String description,
        @NotNull @PositiveOrZero BigDecimal prixMontant,
        @Size(min = 3, max = 3) String devise,
        @NotNull TicketScope portee,
        @NotNull @Positive Integer quantiteTotale,
        @Positive Integer limiteParUtilisateur,
        Instant venteDebut,
        Instant venteFin,
        Boolean actif,
        Integer ordre,
        /** Free categories only: false skips the identity form on claim. Ignored if paid. */
        Boolean formulaireRequis,
        /** Required when {@code portee == ACTIVITE}: activities this ticket grants access to. */
        Set<UUID> activityIds) {
}
