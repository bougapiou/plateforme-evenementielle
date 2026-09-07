package bf.evenements.plateforme.common.security;

import bf.evenements.plateforme.user.User;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated principal placed in the security context. Authorities are the
 * user's permission names plus {@code ROLE_*} entries for each assigned role.
 */
public record AppUserPrincipal(
        UUID id,
        String email,
        String passwordHash,
        boolean active,
        Collection<? extends GrantedAuthority> authorities) implements UserDetails {

    public static AppUserPrincipal from(User user) {
        List<GrantedAuthority> auth = Stream.concat(
                        user.permissionNames().stream().map(SimpleGrantedAuthority::new),
                        user.roleNames().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)))
                .map(GrantedAuthority.class::cast)
                .toList();
        return new AppUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(),
                user.isActive(), auth);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
