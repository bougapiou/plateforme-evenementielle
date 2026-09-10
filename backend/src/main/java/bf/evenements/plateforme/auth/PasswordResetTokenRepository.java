package bf.evenements.plateforme.auth;

import bf.evenements.plateforme.user.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Transactional
    @Query("update PasswordResetToken t set t.usedAt = :now "
            + "where t.user = :user and t.usedAt is null")
    int invalidateAllForUser(@Param("user") User user, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("delete from PasswordResetToken t where t.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
