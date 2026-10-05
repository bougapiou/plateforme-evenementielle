package bf.evenements.plateforme.pointage;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointageStandRepository extends JpaRepository<PointageStand, UUID> {

    List<PointageStand> findAllByOrderByOrdreAscNomAsc();
}
