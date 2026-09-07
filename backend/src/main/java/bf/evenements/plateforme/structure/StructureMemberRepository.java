package bf.evenements.plateforme.structure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StructureMemberRepository extends JpaRepository<StructureMember, UUID> {

    Optional<StructureMember> findByStructureIdAndUserId(UUID structureId, UUID userId);

    List<StructureMember> findByStructureIdOrderByRoleInterneAscCreatedAtAsc(UUID structureId);

    boolean existsByStructureIdAndUserIdAndActiveTrue(UUID structureId, UUID userId);
}
