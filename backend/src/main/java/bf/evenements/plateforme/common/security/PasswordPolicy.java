package bf.evenements.plateforme.common.security;

import bf.evenements.plateforme.common.exception.BusinessException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Password strength rules applied wherever a password is chosen (sign-up,
 * account claim, change, reset, admin creation): minimum length, at least a
 * letter and a digit or symbol, not a well-known password, not the e-mail.
 */
@Component
public class PasswordPolicy {

    private final int minLength;
    private final Set<String> common = new HashSet<>();

    public PasswordPolicy(@Value("${app.security.password-min-length:12}") int minLength) {
        this.minLength = minLength;
        var resource = new ClassPathResource("security/mots-de-passe-courants.txt");
        try (var in = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            in.lines().map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .forEach(l -> common.add(normalize(l)));
        } catch (IOException e) {
            throw new IllegalStateException("Liste des mots de passe courants introuvable", e);
        }
    }

    public int minLength() {
        return minLength;
    }

    /** Throws {@code WEAK_PASSWORD} with a user-facing reason when the password is refused. */
    public void check(String password, @Nullable String email) {
        if (password == null || password.length() < minLength) {
            throw weak("Le mot de passe doit contenir au moins " + minLength + " caractères.");
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean other = password.chars().anyMatch(c -> !Character.isLetter(c));
        if (!letter || !other) {
            throw weak("Le mot de passe doit mélanger des lettres et des chiffres ou symboles.");
        }
        if (password.chars().distinct().count() < 5) {
            throw weak("Le mot de passe est trop répétitif.");
        }
        String n = normalize(password);
        String core = n.replaceAll("[^a-z]+$", "").replaceAll("^[^a-z]+", "");
        if (common.contains(n) || common.contains(core) || isSequence(n)) {
            throw weak("Ce mot de passe est trop courant. Choisissez-en un plus personnel.");
        }
        if (email != null && email.contains("@")) {
            String local = normalize(email.substring(0, email.indexOf('@')));
            if (local.length() >= 4 && n.contains(local)) {
                throw weak("Le mot de passe ne doit pas contenir votre adresse e-mail.");
            }
        }
    }

    private static boolean isSequence(String s) {
        if (s.length() < 2) {
            return true;
        }
        int step = s.charAt(1) - s.charAt(0);
        if (Math.abs(step) > 1) {
            return false;
        }
        for (int i = 2; i < s.length(); i++) {
            if (s.charAt(i) - s.charAt(i - 1) != step) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static BusinessException weak(String message) {
        return new BusinessException("WEAK_PASSWORD", message);
    }
}
