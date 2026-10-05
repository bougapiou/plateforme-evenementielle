package bf.evenements.plateforme.standvisit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisiteStandRepository extends JpaRepository<VisiteStand, UUID> {

    long countByStandId(UUID standId);

    long countByEventId(UUID eventId);

    @Query("select v.stand.id as standId, count(v) as total from VisiteStand v "
            + "where v.event.id = :eventId group by v.stand.id")
    List<StandCount> countByStandForEvent(@Param("eventId") UUID eventId);

    interface StandCount {
        UUID getStandId();

        long getTotal();
    }
}
