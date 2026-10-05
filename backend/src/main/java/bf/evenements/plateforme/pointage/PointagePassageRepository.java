package bf.evenements.plateforme.pointage;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointagePassageRepository extends JpaRepository<PointagePassage, UUID> {

    @Query("select p.stand.id as standId, "
            + "sum(case when p.nom is not null or p.prenom is not null then 1L else 0L end) as identifiees, "
            + "sum(case when p.nom is null and p.prenom is null then 1L else 0L end) as anonymes "
            + "from PointagePassage p group by p.stand.id")
    List<StandCountDetail> countDetailByStand();

    @Query("select "
            + "sum(case when p.nom is not null or p.prenom is not null then 1L else 0L end) as identifiees, "
            + "sum(case when p.nom is null and p.prenom is null then 1L else 0L end) as anonymes "
            + "from PointagePassage p where p.stand.id = :standId")
    StandCountDetail countDetailForStand(@Param("standId") UUID standId);

    interface StandCountDetail {
        UUID getStandId();

        Long getIdentifiees();

        Long getAnonymes();
    }
}
