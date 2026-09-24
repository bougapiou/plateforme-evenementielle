package bf.evenements.plateforme.sensor;

import bf.evenements.plateforme.checkin.CheckinDirection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PassageCapteurRepository extends JpaRepository<PassageCapteur, UUID> {

    @Query("select coalesce(sum(p.nombre), 0) from PassageCapteur p "
            + "where p.eventId = :eventId and p.sens = :sens")
    long totalForEvent(@Param("eventId") UUID eventId, @Param("sens") CheckinDirection sens);

    @Query("select coalesce(sum(p.nombre), 0) from PassageCapteur p "
            + "where p.capteurId = :capteurId and p.sens = :sens")
    long totalForCapteur(@Param("capteurId") UUID capteurId, @Param("sens") CheckinDirection sens);
}
