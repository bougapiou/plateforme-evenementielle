package bf.evenements.plateforme.invoice;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findByPaymentId(UUID paymentId);

    List<Invoice> findByUserIdOrderByEmiseLeDesc(UUID userId);

    boolean existsByPaymentId(UUID paymentId);

    boolean existsByEventId(UUID eventId);

    boolean existsByNumero(String numero);

    long countByType(Invoice.InvoiceType type);
}
