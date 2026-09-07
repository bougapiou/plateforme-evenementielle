package bf.evenements.plateforme.stand;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StandReservationRepository extends JpaRepository<StandReservation, UUID> {

    Page<StandReservation> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<StandReservation> findByEventIdOrderByCreatedAtDesc(UUID eventId, Pageable pageable);

    boolean existsByReference(String reference);

    boolean existsByNumeroReservation(String numero);

    @Query("""
            select r from StandReservation r
            where r.stand.id = :standId
              and r.statut in (bf.evenements.plateforme.stand.StandReservationStatus.RESERVE_TEMP,
                               bf.evenements.plateforme.stand.StandReservationStatus.ATTENTE_PAIEMENT,
                               bf.evenements.plateforme.stand.StandReservationStatus.PAYE,
                               bf.evenements.plateforme.stand.StandReservationStatus.CONFIRME)
            """)
    List<StandReservation> findActiveByStand(@Param("standId") UUID standId);

    @Query("""
            select r from StandReservation r
            where r.statut in (bf.evenements.plateforme.stand.StandReservationStatus.RESERVE_TEMP,
                               bf.evenements.plateforme.stand.StandReservationStatus.ATTENTE_PAIEMENT)
              and r.holdExpireLe < :cutoff
            """)
    List<StandReservation> findExpired(@Param("cutoff") Instant cutoff);

    List<StandReservation> findByEventId(UUID eventId);
}
