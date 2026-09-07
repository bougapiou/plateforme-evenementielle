package bf.evenements.plateforme.structure.dto;

import bf.evenements.plateforme.structure.StructureMember;
import bf.evenements.plateforme.structure.StructureMemberRole;
import java.util.UUID;

public record StructureMemberResponse(
        UUID id,
        UUID userId,
        String fullName,
        String email,
        StructureMemberRole roleInterne,
        String fonction,
        boolean active) {

    public static StructureMemberResponse from(StructureMember m) {
        return new StructureMemberResponse(
                m.getId(), m.getUser().getId(), m.getUser().getFullName(), m.getUser().getEmail(),
                m.getRoleInterne(), m.getFonction(), m.isActive());
    }
}
