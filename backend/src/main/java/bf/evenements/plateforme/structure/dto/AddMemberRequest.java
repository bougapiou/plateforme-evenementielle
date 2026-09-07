package bf.evenements.plateforme.structure.dto;

import bf.evenements.plateforme.structure.StructureMemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddMemberRequest(
        @NotBlank @Email String email,
        @NotNull StructureMemberRole roleInterne,
        @Size(max = 120) String fonction) {
}
