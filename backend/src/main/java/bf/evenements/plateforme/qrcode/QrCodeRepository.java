package bf.evenements.plateforme.qrcode;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QrCodeRepository extends JpaRepository<QrCode, UUID> {

    Optional<QrCode> findByToken(String token);

    Optional<QrCode> findByTicketId(UUID ticketId);

    boolean existsByToken(String token);
}
