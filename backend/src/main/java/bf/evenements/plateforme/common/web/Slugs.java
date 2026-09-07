package bf.evenements.plateforme.common.web;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

public final class Slugs {

    private Slugs() {
    }

    /** Lower-case, accent-free, hyphen-separated slug from arbitrary text. */
    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "item";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "item" : normalized;
    }

    /**
     * Produces a slug for {@code base} that satisfies {@code isFree}, appending
     * {@code -2}, {@code -3}, ... until an unused value is found.
     */
    public static String uniqueSlug(String base, Predicate<String> isFree) {
        String root = slugify(base);
        if (isFree.test(root)) {
            return root;
        }
        for (int i = 2; i < 10_000; i++) {
            String candidate = root + "-" + i;
            if (isFree.test(candidate)) {
                return candidate;
            }
        }
        return root + "-" + System.currentTimeMillis();
    }
}
