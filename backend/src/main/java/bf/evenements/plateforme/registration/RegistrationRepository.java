package bf.evenements.plateforme.registration;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

    Page<Registration> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Registration> findByEventIdOrderByCreatedAtDesc(UUID eventId, Pageable pageable);

    Optional<Registration> findByTicketOrderId(UUID ticketOrderId);

    boolean existsByReference(String reference);

    boolean existsByEventIdAndUserIdAndStatutIn(UUID eventId, UUID userId,
                                                java.util.Collection<RegistrationStatus> statuts);
}
