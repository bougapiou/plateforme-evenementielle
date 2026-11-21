package bf.evenements.plateforme.pointage;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PointageStandRepository extends JpaRepository<PointageStand, UUID> {

    List<PointageStand> findAllByOrderByOrdreAscNomAsc();

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, UUID id);

    @Query("select coalesce(max(s.ordre), -1) from PointageStand s")
    int maxOrdre();
}
