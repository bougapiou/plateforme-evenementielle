package bf.evenements.plateforme.structure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StructureRepository extends JpaRepository<Structure, UUID>,
        JpaSpecificationExecutor<Structure> {

    @Query("""
            select distinct s from Structure s
            join s.members m
            where m.user.id = :userId and m.active = true
            order by s.raisonSociale
            """)
    List<Structure> findAllForMember(@Param("userId") UUID userId);

    boolean existsByRccmIgnoreCase(String rccm);
}
