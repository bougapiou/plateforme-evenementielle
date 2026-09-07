package bf.evenements.plateforme.stats;

import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventService;
import bf.evenements.plateforme.rbac.Permissions;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final StatsRepository statsRepository;
    private final EventService eventService;
    private final CurrentUserProvider currentUser;

    @Transactional(readOnly = true)
    public Map<String, Object> adminOverview() {
        var g = statsRepository.globalOverview();
        return Map.of(
                "evenementsTotal", g.getEventsTotal(),
                "evenementsActifs", g.getEventsActifs(),
                "evenementsTermines", g.getEventsTermines(),
                "evenementsAValider", g.getEventsAValider(),
                "utilisateurs", g.getUsersTotal(),
                "structures", g.getStructuresTotal(),
                "organisateursActifs", g.getOrganisateursActifs(),
                "inscriptionsConfirmees", g.getInscriptionsConfirmees(),
                "billetsVendus", g.getBilletsVendus(),
                "chiffreAffaires", scale(g.getChiffreAffaires()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> organizerOverview() {
        var o = statsRepository.organizerOverview(currentUser.requireId());
        return Map.of(
                "evenements", o.getEventsTotal(),
                "billetsVendus", o.getBilletsVendus(),
                "billetsRestants", o.getBilletsRestants(),
                "inscriptionsConfirmees", o.getInscriptionsConfirmees(),
                "standsConfirmes", o.getStandsConfirmes(),
                "revenus", scale(o.getRevenus()),
                "paiementsEnAttente", o.getPaiementsEnAttente());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> eventStats(UUID eventId) {
        Event event = eventService.loadManaged(eventId);
        var e = statsRepository.eventOverview(eventId);
        long billetsRestants = Math.max(0,
                e.getBilletsTotal() - e.getBilletsVendus() - e.getBilletsReserves());
        double tauxRemplissage = e.getBilletsTotal() == 0 ? 0
                : round((double) e.getBilletsVendus() / e.getBilletsTotal() * 100);
        return Map.ofEntries(
                Map.entry("evenement", event.getNom()),
                Map.entry("billetsTotal", e.getBilletsTotal()),
                Map.entry("billetsVendus", e.getBilletsVendus()),
                Map.entry("billetsRestants", billetsRestants),
                Map.entry("tauxRemplissage", tauxRemplissage),
                Map.entry("standsTotal", e.getStandsTotal()),
                Map.entry("standsReserves", e.getStandsReserves()),
                Map.entry("standsDisponibles", Math.max(0, e.getStandsTotal() - e.getStandsReserves())),
                Map.entry("inscriptionsConfirmees", e.getInscriptionsConfirmees()),
                Map.entry("inscriptionsEnAttente", e.getInscriptionsEnAttente()),
                Map.entry("structuresParticipantes", e.getStructuresParticipantes()),
                Map.entry("revenus", scale(e.getRevenus())),
                Map.entry("paiementsEnAttente", e.getPaiementsEnAttente()),
                Map.entry("paiementsReussis", e.getPaiementsReussis()),
                Map.entry("entreesValidees", e.getEntreesValidees()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> eventSeries(UUID eventId) {
        eventService.loadManaged(eventId);
        Instant to = Instant.now();
        Instant from = to.minus(30, ChronoUnit.DAYS);
        List<Map<String, Object>> serie = statsRepository.eventDailySeries(eventId, from, to).stream()
                .map(r -> Map.<String, Object>of(
                        "jour", r.getJour(),
                        "inscriptions", r.getInscriptions(),
                        "revenus", scale(r.getRevenus())))
                .toList();
        List<Map<String, Object>> parCategorie = statsRepository.ticketBreakdown(eventId).stream()
                .map(r -> Map.<String, Object>of("label", r.getLabel(), "valeur", r.getValeur()))
                .toList();
        return Map.of("quotidien", serie, "billetsParCategorie", parCategorie);
    }

    public boolean canReadGlobal() {
        return currentUser.hasAuthority(Permissions.STATS_GLOBAL_READ);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
