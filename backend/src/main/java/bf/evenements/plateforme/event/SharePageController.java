package bf.evenements.plateforme.event;

import bf.evenements.plateforme.common.config.AppProperties;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * Link previews for social networks (PART-08). WhatsApp, Facebook or X do not
 * run the Angular application, so the shared link points here: a tiny HTML
 * page carrying the event's Open Graph tags (title, date, place, cover) that
 * immediately sends human visitors on to the real page.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Partage")
public class SharePageController {

    private static final DateTimeFormatter DAY = DateTimeFormatter
            .ofPattern("d MMMM yyyy", Locale.FRENCH).withZone(java.time.ZoneId.of("Africa/Ouagadougou"));

    private final EventRepository eventRepository;
    private final AppProperties appProperties;

    @GetMapping(value = "/partage/evenements/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional(readOnly = true)
    @Operation(summary = "Page d'aperçu (Open Graph) d'un événement pour les réseaux sociaux")
    public ResponseEntity<String> event(@PathVariable String slug) {
        Event e = eventRepository.findBySlug(slug)
                .filter(x -> x.getStatut().isPubliclyVisible() && !x.isMasquePublic())
                .orElseThrow(() -> new ResourceNotFoundException("Événement introuvable : " + slug));
        String base = appProperties.frontendBaseUrl();
        String url = base + "/evenements/" + e.getSlug();
        String when = DAY.format(e.getDateDebut()) + (DAY.format(e.getDateFin()).equals(DAY.format(e.getDateDebut()))
                ? "" : " – " + DAY.format(e.getDateFin()));
        String where = e.getLieu() != null ? e.getLieu() + (e.getVille() != null ? ", " + e.getVille() : "")
                : e.getVille() != null ? e.getVille() : "";
        String description = when + (where.isEmpty() ? "" : " · " + where)
                + (e.getDescriptionCourte() != null ? " — " + e.getDescriptionCourte() : "");
        String image = e.getCoverUrl() != null ? absolute(base, e.getCoverUrl()) : base + "/assets/logo-512.png";

        String html = """
                <!doctype html>
                <html lang="fr"><head><meta charset="utf-8">
                <title>%1$s</title>
                <meta name="description" content="%2$s">
                <meta property="og:type" content="website">
                <meta property="og:site_name" content="Plateforme Nationale des Événements">
                <meta property="og:title" content="%1$s">
                <meta property="og:description" content="%2$s">
                <meta property="og:image" content="%3$s">
                <meta property="og:url" content="%4$s">
                <meta name="twitter:card" content="summary_large_image">
                <link rel="canonical" href="%4$s">
                <meta http-equiv="refresh" content="0; url=%4$s">
                </head><body>
                <p><a href="%4$s">%1$s</a></p>
                <script>location.replace(%5$s);</script>
                </body></html>
                """.formatted(esc(e.getNom()), esc(description), esc(image), esc(url),
                "\"" + url.replace("\\", "").replace("\"", "") + "\"");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(10)).cachePublic())
                .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                .body(html);
    }

    private static String absolute(String base, String url) {
        return url.startsWith("http") ? url : base + (url.startsWith("/") ? "" : "/") + url;
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s, "UTF-8");
    }
}
