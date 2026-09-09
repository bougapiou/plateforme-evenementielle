package bf.evenements.plateforme.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Turns a guest account into a full one by choosing a password. */
public record CompleteRegistrationRequest(
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName) {
}
