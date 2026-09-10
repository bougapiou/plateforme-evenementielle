package bf.evenements.plateforme.checkin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckinRepository extends JpaRepository<Checkin, UUID> {

    /** Latest event-level check-in for a ticket — tells whether the holder is inside. */
    Optional<Checkin> findFirstByTicketIdAndActivityIdIsNullAndResultatOrderByScannedAtDesc(
            UUID ticketId, CheckinResult resultat);

    /** Latest check-in for a (ticket, activity) — tells whether the holder is inside the activity. */
    Optional<Checkin> findFirstByTicketIdAndActivityIdAndResultatOrderByScannedAtDesc(
            UUID ticketId, UUID activityId, CheckinResult resultat);

    long countByTicketIdAndActivityIdIsNullAndSensAndResultat(
            UUID ticketId, CheckinDirection sens, CheckinResult resultat);

    long countByTicketIdAndActivityIdAndSensAndResultat(
            UUID ticketId, UUID activityId, CheckinDirection sens, CheckinResult resultat);

    long countByEventIdAndActivityIdIsNullAndSensAndResultat(
            UUID eventId, CheckinDirection sens, CheckinResult resultat);

    long countByEventIdAndActivityIdAndSensAndResultat(
            UUID eventId, UUID activityId, CheckinDirection sens, CheckinResult resultat);

    /** Distinct tickets that have entered at least once (event-level). */
    @Query("select count(distinct c.ticketId) from Checkin c where c.eventId = :eventId "
            + "and c.activityId is null and c.sens = bf.evenements.plateforme.checkin.CheckinDirection.ENTREE "
            + "and c.resultat = bf.evenements.plateforme.checkin.CheckinResult.VALIDE")
    long countDistinctEnteredTickets(@Param("eventId") UUID eventId);

    /** Distinct tickets that have entered a given activity at least once. */
    @Query("select count(distinct c.ticketId) from Checkin c where c.eventId = :eventId "
            + "and c.activityId = :activityId "
            + "and c.sens = bf.evenements.plateforme.checkin.CheckinDirection.ENTREE "
            + "and c.resultat = bf.evenements.plateforme.checkin.CheckinResult.VALIDE")
    long countDistinctEnteredTicketsForActivity(@Param("eventId") UUID eventId,
                                                @Param("activityId") UUID activityId);

    Page<Checkin> findByEventIdOrderByScannedAtDesc(UUID eventId, Pageable pageable);

    long countByEventIdAndResultat(UUID eventId, CheckinResult resultat);

    long countByEventIdAndActivityIdAndResultat(UUID eventId, UUID activityId,
                                                CheckinResult resultat);

    List<Checkin> findByEventId(UUID eventId);
}
