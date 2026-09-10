package bf.evenements.plateforme.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Reuse an existing phone-only guest session (checkout without an e-mail). */
    Optional<User> findFirstByPhoneAndGuestTrueOrderByCreatedAtDesc(String phone);
}
