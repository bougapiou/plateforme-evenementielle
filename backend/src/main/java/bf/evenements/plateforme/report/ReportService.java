package bf.evenements.plateforme.report;

import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event reporting for organisers and administrators: figures for one day or
 * the whole event, and the laser sensors' people flow per day or per hour.
 * Days and hours are local to {@link #ZONE}.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    public static final ZoneId ZONE = ZoneId.of("Africa/Ouagadougou");
    /** Guard against absurd date ranges (an event spanning months). */
    private static final int MAX_DAYS = 92;

    private final NamedParameterJdbcTemplate jdbc;
    private final EventService eventService;

    // ------------------------------------------------------------------ stats

    /** Figures for {@code jour} (or the whole event when null), plus the per-day and per-activity breakdowns. */
    @Transactional(readOnly = true)
    public EventReport eventReport(UUID eventId, @Nullable LocalDate jour) {
        Event event = eventService.loadManaged(eventId);
        List<LocalDate> days = eventDays(event);
        Instant from = jour == null ? Instant.EPOCH : startOf(jour);
        Instant to = jour == null ? Instant.parse("9999-01-01T00:00:00Z") : startOf(jour.plusDays(1));

        Metrics totals = metrics(eventId, from, to);
        List<DayMetrics> perDay = days.stream()
                .map(d -> new DayMetrics(d, metrics(eventId, startOf(d), startOf(d.plusDays(1)))))
                .toList();
        return new EventReport(event.getId(), event.getNom(), event.getDateDebut(), event.getDateFin(),
                jour, days, totals, perDay, activities(eventId, from, to));
    }

    private Metrics metrics(UUID eventId, Instant from, Instant to) {
        var p = params(eventId, from, to);
        return jdbc.queryForObject("""
                select
                  (select count(*) from tickets where event_id = :e and statut <> 'ANNULE'
                     and created_at >= :f and created_at < :t) as billets,
                  (select count(*) from registrations where event_id = :e
                     and created_at >= :f and created_at < :t) as inscriptions,
                  (select count(*) from registrations where event_id = :e and statut = 'CONFIRMEE'
                     and created_at >= :f and created_at < :t) as inscriptions_confirmees,
                  (select coalesce(sum(montant), 0) from payments where event_id = :e and statut = 'REUSSI'
                     and paid_at >= :f and paid_at < :t) as revenus,
                  (select count(*) from payments where event_id = :e and statut = 'REUSSI'
                     and paid_at >= :f and paid_at < :t) as paiements,
                  (select count(*) from checkins where event_id = :e and activity_id is null
                     and resultat = 'VALIDE' and sens = 'ENTREE'
                     and scanned_at >= :f and scanned_at < :t) as entrees,
                  (select count(*) from checkins where event_id = :e and activity_id is null
                     and resultat = 'VALIDE' and sens = 'SORTIE'
                     and scanned_at >= :f and scanned_at < :t) as sorties,
                  (select count(distinct coalesce(ticket_id, accreditation_id)) from checkins
                     where event_id = :e and resultat = 'VALIDE' and sens = 'ENTREE'
                     and scanned_at >= :f and scanned_at < :t) as visiteurs_uniques,
                  (select coalesce(sum(nombre), 0) from passages_capteur where event_id = :e
                     and sens = 'ENTREE' and passe_le >= :f and passe_le < :t) as capteur_entrees,
                  (select coalesce(sum(nombre), 0) from passages_capteur where event_id = :e
                     and sens = 'SORTIE' and passe_le >= :f and passe_le < :t) as capteur_sorties,
                  (select count(*) from visites_stand where event_id = :e
                     and created_at >= :f and created_at < :t) as visites_stands
                """, p, (rs, i) -> new Metrics(
                rs.getLong("billets"), rs.getLong("inscriptions"), rs.getLong("inscriptions_confirmees"),
                money(rs.getBigDecimal("revenus")), rs.getLong("paiements"),
                rs.getLong("entrees"), rs.getLong("sorties"), rs.getLong("visiteurs_uniques"),
                rs.getLong("capteur_entrees"), rs.getLong("capteur_sorties"), rs.getLong("visites_stands")));
    }

    private List<ActivityMetrics> activities(UUID eventId, Instant from, Instant to) {
        return jdbc.query("""
                select a.id, a.titre, a.date_debut,
                       count(c.id) filter (where c.sens = 'ENTREE') as entrees,
                       count(c.id) filter (where c.sens = 'SORTIE') as sorties,
                       count(distinct coalesce(c.ticket_id, c.accreditation_id))
                         filter (where c.sens = 'ENTREE') as uniques
                from event_activities a
                left join checkins c on c.activity_id = a.id and c.resultat = 'VALIDE'
                     and c.scanned_at >= :f and c.scanned_at < :t
                where a.event_id = :e
                group by a.id, a.titre, a.date_debut, a.ordre
                order by a.date_debut, a.ordre
                """, params(eventId, from, to), (rs, i) -> new ActivityMetrics(
                rs.getObject("id", UUID.class), rs.getString("titre"),
                rs.getTimestamp("date_debut").toInstant(),
                rs.getLong("entrees"), rs.getLong("sorties"), rs.getLong("uniques")));
    }

    // ------------------------------------------------------------------- flux

    /**
     * People flow counted by the laser sensors (and, for comparison, the QR
     * scans at the entrance): per day over the event, or per hour of {@code jour}.
     */
    @Transactional(readOnly = true)
    public FluxReport flux(UUID eventId, Granularite granularite, @Nullable LocalDate jour,
                           @Nullable UUID capteurId) {
        Event event = eventService.loadManaged(eventId);
        List<LocalDate> days = eventDays(event);

        List<Bucket> buckets = new ArrayList<>();
        LocalDate day = null;
        if (granularite == Granularite.HEURE) {
            day = jour != null ? jour : defaultDay(days);
            for (int h = 0; h < 24; h++) {
                Instant start = startOf(day).plus(h, ChronoUnit.HOURS);
                buckets.add(new Bucket(String.format("%02dh", h), start, start.plus(1, ChronoUnit.HOURS)));
            }
        } else {
            // Event days, plus any day outside them where a sensor still counted people.
            TreeSet<LocalDate> all = new TreeSet<>(days);
            all.addAll(sensorDays(eventId, capteurId));
            for (LocalDate d : all) {
                buckets.add(new Bucket(d.toString(), startOf(d), startOf(d.plusDays(1))));
            }
        }
        if (buckets.isEmpty()) {
            return new FluxReport(granularite, day, days, List.of(), 0, 0, null, sensors(eventId, null, null));
        }
        Instant from = buckets.get(0).start();
        Instant to = buckets.get(buckets.size() - 1).end();
        String unit = granularite == Granularite.HEURE ? "hour" : "day";

        Map<Instant, long[]> sensor = grouped("""
                select date_trunc(:unit, passe_le at time zone 'Africa/Ouagadougou') as b,
                       coalesce(sum(nombre) filter (where sens = 'ENTREE'), 0) as entrees,
                       coalesce(sum(nombre) filter (where sens = 'SORTIE'), 0) as sorties
                from passages_capteur
                where event_id = :e and passe_le >= :f and passe_le < :t
                  and (cast(:c as uuid) is null or capteur_id = cast(:c as uuid))
                group by 1
                """, eventId, from, to, unit, capteurId);
        Map<Instant, long[]> scans = grouped("""
                select date_trunc(:unit, scanned_at at time zone 'Africa/Ouagadougou') as b,
                       count(*) filter (where sens = 'ENTREE') as entrees,
                       count(*) filter (where sens = 'SORTIE') as sorties
                from checkins
                where event_id = :e and activity_id is null and resultat = 'VALIDE'
                  and scanned_at >= :f and scanned_at < :t and cast(:c as uuid) is null
                group by 1
                """, eventId, from, to, unit, capteurId);

        // People already inside when the window opens (sensor counts only).
        long present = baseline(eventId, from, capteurId);
        long totalIn = 0;
        long totalOut = 0;
        FluxPoint peak = null;
        List<FluxPoint> points = new ArrayList<>();
        for (Bucket b : buckets) {
            long[] s = sensor.getOrDefault(b.start(), new long[2]);
            long[] q = scans.getOrDefault(b.start(), new long[2]);
            present = Math.max(0, present + s[0] - s[1]);
            totalIn += s[0];
            totalOut += s[1];
            FluxPoint point = new FluxPoint(b.label(), b.start(), s[0], s[1], present, q[0], q[1]);
            points.add(point);
            if (s[0] > 0 && (peak == null || s[0] > peak.entrees())) {
                peak = point;
            }
        }
        return new FluxReport(granularite, day, days, points, totalIn, totalOut, peak,
                sensors(eventId, from, to));
    }

    private Map<Instant, long[]> grouped(String sql, UUID eventId, Instant from, Instant to, String unit,
                                         @Nullable UUID capteurId) {
        var p = params(eventId, from, to).addValue("unit", unit).addValue("c", capteurId);
        Map<Instant, long[]> out = new HashMap<>();
        jdbc.query(sql, p, rs -> {
            // date_trunc(... at time zone) yields a local timestamp: map it back to an instant.
            LocalDateTime local = rs.getTimestamp("b").toLocalDateTime();
            out.put(local.atZone(ZONE).toInstant(), new long[] {rs.getLong("entrees"), rs.getLong("sorties")});
        });
        return out;
    }

    private long baseline(UUID eventId, Instant before, @Nullable UUID capteurId) {
        var p = params(eventId, Instant.EPOCH, before).addValue("c", capteurId);
        Long v = jdbc.queryForObject("""
                select coalesce(sum(case when sens = 'ENTREE' then nombre else -nombre end), 0)
                from passages_capteur
                where event_id = :e and passe_le < :t
                  and (cast(:c as uuid) is null or capteur_id = cast(:c as uuid))
                """, p, Long.class);
        return v == null ? 0 : Math.max(0, v);
    }

    private List<LocalDate> sensorDays(UUID eventId, @Nullable UUID capteurId) {
        var p = new MapSqlParameterSource("e", eventId).addValue("c", capteurId);
        return jdbc.query("""
                select distinct cast(passe_le at time zone 'Africa/Ouagadougou' as date) as d
                from passages_capteur
                where event_id = :e and (cast(:c as uuid) is null or capteur_id = cast(:c as uuid))
                order by 1 limit 92
                """, p, (rs, i) -> rs.getDate("d").toLocalDate());
    }

    private List<SensorTotals> sensors(UUID eventId, @Nullable Instant from, @Nullable Instant to) {
        var p = params(eventId, from == null ? Instant.EPOCH : from,
                to == null ? Instant.parse("9999-01-01T00:00:00Z") : to);
        return jdbc.query("""
                select c.id, c.nom, c.actif, c.derniere_activite,
                       coalesce(sum(p.nombre) filter (where p.sens = 'ENTREE'), 0) as entrees,
                       coalesce(sum(p.nombre) filter (where p.sens = 'SORTIE'), 0) as sorties
                from capteurs c
                left join passages_capteur p on p.capteur_id = c.id and p.passe_le >= :f and p.passe_le < :t
                where c.event_id = :e
                group by c.id, c.nom, c.actif, c.derniere_activite, c.created_at
                order by c.created_at
                """, p, (rs, i) -> {
            Timestamp last = rs.getTimestamp("derniere_activite");
            return new SensorTotals(rs.getObject("id", UUID.class), rs.getString("nom"), rs.getBoolean("actif"),
                    last == null ? null : last.toInstant(), rs.getLong("entrees"), rs.getLong("sorties"));
        });
    }

    // ---------------------------------------------------------------- helpers

    /** Local calendar days covered by the event, from its first to its last day. */
    static List<LocalDate> eventDays(Event event) {
        LocalDate first = event.getDateDebut().atZone(ZONE).toLocalDate();
        LocalDate last = event.getDateFin() == null ? first : event.getDateFin().atZone(ZONE).toLocalDate();
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last) && days.size() < MAX_DAYS; d = d.plusDays(1)) {
            days.add(d);
        }
        return days;
    }

    private static LocalDate defaultDay(List<LocalDate> days) {
        LocalDate today = LocalDate.now(ZONE);
        if (days.isEmpty() || days.contains(today)) {
            return today;
        }
        return today.isAfter(days.get(days.size() - 1)) ? days.get(days.size() - 1) : days.get(0);
    }

    static Instant startOf(LocalDate day) {
        return day.atStartOfDay(ZONE).toInstant();
    }

    /** A local date-time typed in a form, read in the platform's time zone. */
    static Instant at(LocalDateTime local) {
        return ZonedDateTime.of(local, ZONE).toInstant();
    }

    private static MapSqlParameterSource params(UUID eventId, Instant from, Instant to) {
        return new MapSqlParameterSource("e", eventId)
                .addValue("f", Timestamp.from(from))
                .addValue("t", Timestamp.from(to));
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    static void requireOrder(@Nullable Instant from, @Nullable Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BusinessException("INVALID_PERIOD", "La fin de la période doit être après son début.");
        }
    }

    // ------------------------------------------------------------------ types

    public enum Granularite { JOUR, HEURE }

    public record Metrics(long billetsEmis, long inscriptions, long inscriptionsConfirmees,
                          BigDecimal revenus, long paiementsReussis,
                          long entrees, long sorties, long visiteursUniques,
                          long capteurEntrees, long capteurSorties, long visitesStands) {
    }

    public record DayMetrics(LocalDate jour, Metrics valeurs) {
    }

    public record ActivityMetrics(UUID id, String titre, Instant dateDebut,
                                  long entrees, long sorties, long visiteursUniques) {
    }

    /** {@code jour} null = the whole event. */
    public record EventReport(UUID eventId, String evenement, Instant dateDebut, Instant dateFin,
                              @Nullable LocalDate jour, List<LocalDate> jours, Metrics totaux,
                              List<DayMetrics> parJour, List<ActivityMetrics> parActivite) {
    }

    private record Bucket(String label, Instant start, Instant end) {
    }

    /** {@code presents}: people inside at the end of the slot, according to the sensors. */
    public record FluxPoint(String periode, Instant debut, long entrees, long sorties, long presents,
                            long scanEntrees, long scanSorties) {
    }

    public record SensorTotals(UUID id, String nom, boolean actif, @Nullable Instant derniereActivite,
                               long entrees, long sorties) {
    }

    /** {@code jour}: the day detailed hour by hour (HEURE only). {@code pic}: the busiest slot. */
    public record FluxReport(Granularite granularite, @Nullable LocalDate jour, List<LocalDate> jours,
                             List<FluxPoint> points, long totalEntrees, long totalSorties,
                             @Nullable FluxPoint pic, List<SensorTotals> capteurs) {
    }
}
