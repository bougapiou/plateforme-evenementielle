package bf.evenements.plateforme.report;

import bf.evenements.plateforme.common.pdf.DocumentPdf;
import bf.evenements.plateforme.common.storage.FileStorageService;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventService;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-click closing report (STAT-04): key figures, attendance per day (with a
 * bar per day), top 5 sessions and participant profile — ready to send to the
 * minister the evening of the closing, without reworking in Excel.
 */
@Service
@RequiredArgsConstructor
public class BilanPdfService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter DT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH).withZone(ReportService.ZONE);

    private final ReportService reportService;
    private final EventService eventService;
    private final FileStorageService fileStorage;

    @Transactional(readOnly = true)
    public byte[] bilan(UUID eventId) {
        Event event = eventService.loadForStats(eventId);
        ReportService.EventReport r = reportService.eventReport(eventId, null);
        ReportService.Metrics t = r.totaux();

        DocumentPdf pdf = DocumentPdf.create()
                .event(fileStorage, event.getCoverUrl(), event.getNom(), event.getNom(),
                        DT.format(event.getDateDebut()) + " – " + DT.format(event.getDateFin()))
                .kicker("Rapport de bilan")
                .title("Bilan — " + event.getNom())
                .subtitle("Généré le " + DT.format(java.time.Instant.now()));

        pdf.section("Chiffres clés")
                .row("Inscriptions", fmt(t.inscriptions()) + " (dont " + fmt(t.inscriptionsConfirmees())
                        + " confirmées)")
                .row("Billets émis", fmt(t.billetsEmis()))
                .row("Personnes présentes", fmt(t.visiteursUniques()) + " personnes uniques contrôlées")
                .row("Entrées / sorties", fmt(t.entrees()) + " / " + fmt(t.sorties()))
                .row("Taux de présence", t.billetsEmis() == 0 ? "—"
                        : pct(t.visiteursUniques() * 100.0 / t.billetsEmis()))
                .row("Passages capteurs", t.capteurEntrees() == 0 ? null : fmt(t.capteurEntrees()) + " entrées comptées")
                .row("Visites de stands", t.visitesStands() == 0 ? null : fmt(t.visitesStands()))
                .row("Recettes", t.revenus() == null || t.revenus().signum() == 0 ? null
                        : t.revenus().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString() + " FCFA ("
                        + fmt(t.paiementsReussis()) + " paiements)");

        if (!r.parJour().isEmpty()) {
            long max = r.parJour().stream().mapToLong(d -> d.valeurs().visiteursUniques()).max().orElse(1);
            pdf.section("Fréquentation par jour");
            for (var d : r.parJour()) {
                long v = d.valeurs().visiteursUniques();
                pdf.row(cap(DAY.format(d.jour())), bar(v, max) + "  " + fmt(v) + " présents · "
                        + fmt(d.valeurs().inscriptions()) + " inscriptions");
            }
        }

        var sessions = r.parActivite().stream()
                .sorted(Comparator.comparingLong(ReportService.ActivityMetrics::visiteursUniques).reversed())
                .limit(5).toList();
        if (!sessions.isEmpty() && sessions.get(0).visiteursUniques() > 0) {
            pdf.section("Top 5 des sessions");
            int i = 1;
            for (var a : sessions) {
                pdf.row(i++ + ". " + a.titre(), fmt(a.visiteursUniques()) + " présents"
                        + (a.inscrits() > 0 ? " / " + fmt(a.inscrits()) + " inscrits" : "")
                        + (a.tauxPresence() == null ? "" : " (" + pct(a.tauxPresence()) + ")")
                        + (a.salle() == null ? "" : " · " + a.salle()));
            }
        }

        long surPlace = r.parActivite().stream().mapToLong(ReportService.ActivityMetrics::inscritsSurPlace).sum();
        long auto = r.parActivite().stream().mapToLong(ReportService.ActivityMetrics::autoEnregistres).sum();
        pdf.section("Profil des participants")
                .row("Sessions au programme", fmt(r.parActivite().size()))
                .row("Inscrits sur place", fmt(surPlace))
                .row("Auto-enregistrements", fmt(auto))
                .row("Billets payants", t.revenus() != null && t.revenus().compareTo(BigDecimal.ZERO) > 0
                        ? fmt(t.paiementsReussis()) + " paiements réussis" : "Événement gratuit");
        pdf.note("Chiffres issus des contrôles d'accès (QR) et des inscriptions enregistrés sur la "
                + "Plateforme Nationale des Événements.");
        return pdf.build();
    }

    private static String bar(long v, long max) {
        int n = max <= 0 ? 0 : (int) Math.round(v * 20.0 / max);
        return "|".repeat(Math.max(n, v > 0 ? 1 : 0));
    }

    private static String fmt(long n) {
        return String.format(Locale.FRANCE, "%,d", n).replace(' ', ' ').replace(' ', ' ');
    }

    private static String pct(double v) {
        return String.format(Locale.FRANCE, "%.1f %%", v);
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
