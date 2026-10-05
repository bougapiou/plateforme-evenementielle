package bf.evenements.plateforme.standvisit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisiteStandRepository extends JpaRepository<VisiteStand, UUID> {

    @Query("select v.stand.id as standId, "
            + "sum(case when v.nom is not null or v.prenom is not null then 1L else 0L end) as identifiees, "
            + "sum(case when v.nom is null and v.prenom is null then 1L else 0L end) as anonymes "
            + "from VisiteStand v where v.event.id = :eventId group by v.stand.id")
    List<StandCountDetail> countDetailByStandForEvent(@Param("eventId") UUID eventId);

    @Query("select "
            + "sum(case when v.nom is not null or v.prenom is not null then 1L else 0L end) as identifiees, "
            + "sum(case when v.nom is null and v.prenom is null then 1L else 0L end) as anonymes "
            + "from VisiteStand v where v.stand.id = :standId")
    StandCountDetail countDetailForStand(@Param("standId") UUID standId);

    interface StandCountDetail {
        UUID getStandId();

        Long getIdentifiees();

        Long getAnonymes();
    }
}
