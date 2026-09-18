package bf.evenements.plateforme.payment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PaymentRepository extends JpaRepository<Payment, UUID>,
        JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByReference(String reference);

    Optional<Payment> findByProviderAndTransactionRef(String provider, String transactionRef);

    boolean existsByReference(String reference);

    boolean existsByEventId(UUID eventId);

    Page<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<Payment> findFirstByTargetTypeAndTargetIdAndStatutOrderByCreatedAtDesc(
            PaymentTargetType targetType, UUID targetId, PaymentStatus statut);
}
