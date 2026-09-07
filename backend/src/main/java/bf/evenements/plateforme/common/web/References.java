package bf.evenements.plateforme.common.web;

import java.security.SecureRandom;
import java.util.function.Predicate;

/** Human-friendly, collision-checked business references (order numbers, ticket numbers…). */
public final class References {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private References() {
    }

    public static String random(String prefix, int length) {
        StringBuilder sb = new StringBuilder(prefix).append('-');
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    public static String unique(String prefix, int length, Predicate<String> exists) {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = random(prefix, length);
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
        return prefix + "-" + System.currentTimeMillis();
    }
}
