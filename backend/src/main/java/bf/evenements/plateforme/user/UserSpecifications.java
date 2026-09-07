package bf.evenements.plateforme.user;

import org.springframework.data.jpa.domain.Specification;

/** Composable filters for {@link User} listings. */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> textSearch(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String like = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("email")), like),
                cb.like(cb.lower(root.get("firstName")), like),
                cb.like(cb.lower(root.get("lastName")), like));
    }

    public static Specification<User> hasType(UserType type) {
        return type == null ? null : (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<User> hasStatus(UserStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
