package bf.evenements.plateforme.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Comptes portant un rôle donné, dans un statut donné (garde-fou « dernier administrateur »). */
    long countByRoles_NameAndStatus(String roleName, UserStatus status);

    /** Nombre de comptes portant un rôle (suppression d'un rôle personnalisé). */
    long countByRoles_Id(UUID roleId);

    /** Nombre de comptes réels (hors visiteurs « invités ») portant un rôle — affiché aux admins. */
    long countByRoles_IdAndGuestFalse(UUID roleId);

    /** Reuse an existing phone-only guest session (checkout without an e-mail). */
    Optional<User> findFirstByPhoneAndGuestTrueOrderByCreatedAtDesc(String phone);
}
