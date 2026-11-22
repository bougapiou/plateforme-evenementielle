package bf.evenements.plateforme.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    long countByTelephoneAndCreatedAtAfter(String telephone, Instant after);

    Optional<OtpCode> findFirstByTelephoneAndUtiliseLeIsNullOrderByCreatedAtDesc(String telephone);
}
