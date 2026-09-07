package bf.evenements.plateforme.stand;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StandRepository extends JpaRepository<Stand, UUID> {

    List<Stand> findByEventIdOrderByNumeroAsc(UUID eventId);

    List<Stand> findByStandTypeIdOrderByNumeroAsc(UUID standTypeId);

    boolean existsByEventIdAndNumero(UUID eventId, String numero);

    long countByStandTypeId(UUID standTypeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stand s where s.id = :id")
    Optional<Stand> findByIdForUpdate(@Param("id") UUID id);
}
