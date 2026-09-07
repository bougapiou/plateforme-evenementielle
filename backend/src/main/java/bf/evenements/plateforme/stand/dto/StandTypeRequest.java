package bf.evenements.plateforme.stand.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record StandTypeRequest(
        @NotBlank @Size(max = 120) String nom,
        @Size(max = 1000) String description,
        @Size(max = 60) String dimensions,
        @NotNull @PositiveOrZero BigDecimal prixMontant,
        @Size(min = 3, max = 3) String devise,
        @NotNull @Positive Integer quantiteTotale,
        @Size(max = 1000) String equipements,
        @Size(max = 1000) String conditions,
        Integer ordre) {
}
