package bf.evenements.plateforme.report;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.auth.AuthService;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds participant lists of an event, one of its activities or one of its
 * stands, over an optional time window, with or without duplicates, and
 * renders them as Excel, Word or PDF.
 */
@Service
@RequiredArgsConstructor
public class ParticipantListService {

    private static final int PREVIEW_ROWS = 50;
    private static final DateTimeFormatter LABEL = DateTimeFormatter
            .ofPattern("d MMM yyyy HH:mm", Locale.FRENCH).withZone(ReportService.ZONE);

    private final NamedParameterJdbcTemplate jdbc;
    private final EventService eventService;
    private final ParticipantFileWriter writer;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;

    /** What the list is about. */
    public enum Perimetre { EVENEMENT, ACTIVITE, STAND }

    /** Where the people come from. */
    public enum Source {
        /** Ticket holders. */
        BILLETS,
        /** People actually let in (QR scan validated at the entrance). */
        PRESENTS,
        /** Participants named on registrations. */
        INSCRITS,
        /** Visitors who signalled their visit to a stand. */
        VISITES
    }

    public enum Format { XLSX, DOCX, PDF, CSV }

    /** {@code du}/{@code au}: local date-times, both optional. {@code doublons}: keep repeated people. */
    public record Criteria(Perimetre perimetre, @Nullable UUID cibleId, Source source,
                           @Nullable LocalDateTime du, @Nullable LocalDateTime au, boolean doublons) {
    }

    public record Row(String nom, String prenom, String telephone, String email, String reference,
                      String categorie, String detail, Instant date, int occurrences) {
    }

    public record Preview(String titre, String filtres, long total, long uniques, long doublons,
                          List<Row> lignes) {
    }

    public record ExportFile(String filename, String contentType, byte[] content) {
    }

    // ----------------------------------------------------------------- public

    @Transactional(readOnly = true)
    public Preview preview(UUID eventId, Criteria c) {
        Event event = eventService.loadManaged(eventId);
        List<Row> all = fetch(event, c);
        List<Row> unique = dedupe(all);
        List<Row> shown = c.doublons() ? all : unique;
        return new Preview(title(event, c), filters(c), all.size(), unique.size(),
                all.size() - unique.size(), shown.stream().limit(PREVIEW_ROWS).toList());
    }

    @Transactional(readOnly = true)
    public ExportFile export(UUID eventId, Criteria c, Format format) {
        Event event = eventService.loadManaged(eventId);
        List<Row> all = fetch(event, c);
        List<Row> rows = c.doublons() ? all : dedupe(all);
        String title = title(event, c);
        String filters = filters(c) + " · " + rows.size() + " ligne" + (rows.size() > 1 ? "s" : "");
        byte[] content = switch (format) {
            case XLSX -> writer.xlsx(title, filters, rows, !c.doublons());
            case DOCX -> writer.docx(title, filters, rows, !c.doublons());
            case PDF -> writer.pdf(title, filters, rows, !c.doublons());
            case CSV -> writer.csv(rows, !c.doublons());
        };
        auditService.record(currentUser.requireId(), currentUser.require().email(), "PARTICIPANTS_EXPORTED",
                "Event", eventId.toString(), null,
                c.perimetre() + "/" + c.source() + " " + format + " lignes=" + rows.size());
        String base = "participants-" + slug(event.getSlug()) + "-" + c.source().name().toLowerCase();
        return switch (format) {
            case XLSX -> new ExportFile(base + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
            case DOCX -> new ExportFile(base + ".docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content);
            case PDF -> new ExportFile(base + ".pdf", "application/pdf", content);
            case CSV -> new ExportFile(base + ".csv", "text/csv; charset=UTF-8", content);
        };
    }

    // ------------------------------------------------------------------ query

    private List<Row> fetch(Event event, Criteria c) {
        validate(event, c);
        Instant from = c.du() == null ? Instant.EPOCH : ReportService.at(c.du());
        Instant to = c.au() == null ? Instant.parse("9999-01-01T00:00:00Z") : ReportService.at(c.au());
        ReportService.requireOrder(from, to);
        var p = new MapSqlParameterSource("e", event.getId())
                .addValue("f", Timestamp.from(from))
                .addValue("t", Timestamp.from(to))
                .addValue("cible", c.cibleId())
                .addValue("guest", "%" + AuthService.PLACEHOLDER_EMAIL_DOMAIN);
        String sql = switch (c.source()) {
            case BILLETS -> """
                    select coalesce(t.participant_nom, case when u.guest then null
                             else nullif(trim(u.first_name || ' ' || u.last_name), '') end) as nom,
                           null as prenom, coalesce(t.participant_telephone, u.phone) as telephone,
                           coalesce(t.participant_email,
                             case when u.email like :guest then null else u.email end) as email,
                           t.numero as reference, et.nom as categorie,
                           (case when ent.premiere is null then 'Absent'
                                 else 'Présent — entrée ' || to_char(ent.premiere at time zone 'Africa/Ouagadougou',
                                                                     'DD/MM HH24:MI') end)
                             || case when t.sur_place then ' (inscrit sur place)' else '' end as detail,
                           t.created_at as quand
                    from tickets t
                    join ticket_orders o on o.id = t.order_id
                    join users u on u.id = o.user_id
                    join event_tickets et on et.id = t.event_ticket_id
                    left join lateral (
                        select min(c.scanned_at) as premiere from checkins c
                        where c.ticket_id = t.id and c.sens = 'ENTREE' and c.resultat = 'VALIDE'
                          and ((cast(:cible as uuid) is null and c.activity_id is null)
                               or c.activity_id = cast(:cible as uuid)
                               or (cast(:cible as uuid) is null and et.portee = 'ACTIVITE'))
                    ) ent on true
                    where t.event_id = :e and t.statut <> 'ANNULE'
                      and t.created_at >= :f and t.created_at < :t
                      and (cast(:cible as uuid) is null or exists (
                            select 1 from event_ticket_activities eta
                            where eta.event_ticket_id = t.event_ticket_id
                              and eta.activity_id = cast(:cible as uuid)))
                    order by t.created_at
                    """;
            case PRESENTS -> """
                    select coalesce(t.participant_nom, a.personne_nom, case when u.guest then null
                             else nullif(trim(u.first_name || ' ' || u.last_name), '') end) as nom,
                           null as prenom, u.phone as telephone,
                           coalesce(t.participant_email, a.personne_email,
                             case when u.email like :guest then null else u.email end) as email,
                           coalesce(t.numero, a.numero) as reference,
                           coalesce(et.nom, 'Badge ' || lower(a.fonction)) as categorie,
                           'Entrée' as detail, c.scanned_at as quand
                    from checkins c
                    left join tickets t on t.id = c.ticket_id
                    left join ticket_orders o on o.id = t.order_id
                    left join users u on u.id = o.user_id
                    left join event_tickets et on et.id = t.event_ticket_id
                    left join accreditations a on a.id = c.accreditation_id
                    where c.event_id = :e and c.resultat = 'VALIDE' and c.sens = 'ENTREE'
                      and c.scanned_at >= :f and c.scanned_at < :t
                      and ((cast(:cible as uuid) is null and c.activity_id is null)
                           or c.activity_id = cast(:cible as uuid))
                    order by c.scanned_at
                    """;
            case INSCRITS -> """
                    select p.nom, p.prenom, coalesce(p.telephone, r.contact_telephone) as telephone,
                           p.email, r.reference, p.fonction as categorie,
                           case r.statut when 'CONFIRMEE' then 'Confirmée' else 'En attente' end as detail,
                           r.created_at as quand
                    from participants p
                    join registrations r on r.id = p.registration_id
                    where r.event_id = :e and r.statut in ('CONFIRMEE', 'EN_ATTENTE')
                      and r.created_at >= :f and r.created_at < :t
                    order by r.created_at, p.created_at
                    """;
            case VISITES -> """
                    select v.nom, v.prenom, v.telephone, null as email,
                           s.numero as reference, st.nom as categorie, 'Visite' as detail,
                           v.created_at as quand
                    from visites_stand v
                    join stands s on s.id = v.stand_id
                    join stand_types st on st.id = s.stand_type_id
                    where v.event_id = :e
                      and (cast(:cible as uuid) is null or v.stand_id = cast(:cible as uuid))
                      and v.created_at >= :f and v.created_at < :t
                    order by v.created_at
                    """;
        };
        return jdbc.query(sql, p, (rs, i) -> new Row(
                nz(rs.getString("nom")), nz(rs.getString("prenom")), nz(rs.getString("telephone")),
                nz(rs.getString("email")), nz(rs.getString("reference")), nz(rs.getString("categorie")),
                nz(rs.getString("detail")), rs.getTimestamp("quand").toInstant(), 1));
    }

    private void validate(Event event, Criteria c) {
        boolean ok = switch (c.perimetre()) {
            case EVENEMENT -> c.cibleId() == null;
            case ACTIVITE -> c.cibleId() != null
                    && (c.source() == Source.BILLETS || c.source() == Source.PRESENTS);
            case STAND -> c.cibleId() != null && c.source() == Source.VISITES;
        };
        if (!ok) {
            throw new BusinessException("INVALID_EXPORT",
                    "Combinaison invalide : une activité se liste par billets ou présents, "
                            + "un stand par ses visiteurs, et l'événement entier sans cible.");
        }
        if (c.cibleId() != null) {
            String table = c.perimetre() == Perimetre.ACTIVITE ? "event_activities" : "stands";
            Integer n = jdbc.queryForObject("select count(*) from " + table + " where id = :id and event_id = :e",
                    new MapSqlParameterSource("id", c.cibleId()).addValue("e", event.getId()), Integer.class);
            if (n == null || n == 0) {
                throw new BusinessException("INVALID_EXPORT", "Cette cible n'appartient pas à l'événement.");
            }
        }
    }

    // ------------------------------------------------------------- duplicates

    /**
     * Keeps the first row of each person, counting how many times they appear.
     * A person is recognised by phone number (last 8 digits, so +226 and local
     * forms match), else e-mail, else full name, else the reference.
     */
    static List<Row> dedupe(List<Row> rows) {
        Map<String, Row> firsts = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Row r : rows) {
            String key = identity(r);
            firsts.putIfAbsent(key, r);
            counts.merge(key, 1, Integer::sum);
        }
        List<Row> out = new ArrayList<>(firsts.size());
        firsts.forEach((k, r) -> out.add(new Row(r.nom(), r.prenom(), r.telephone(), r.email(), r.reference(),
                r.categorie(), r.detail(), r.date(), counts.get(k))));
        return out;
    }

    static String identity(Row r) {
        String digits = r.telephone().replaceAll("\\D", "");
        if (digits.length() >= 8) {
            return "tel:" + digits.substring(digits.length() - 8);
        }
        if (!r.email().isBlank()) {
            return "mail:" + r.email().trim().toLowerCase(Locale.ROOT);
        }
        String name = (r.nom() + " " + r.prenom()).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (!name.isBlank()) {
            return "nom:" + name;
        }
        return "ref:" + r.reference();
    }

    // ----------------------------------------------------------------- labels

    private String title(Event event, Criteria c) {
        String what = switch (c.source()) {
            case BILLETS -> "Détenteurs de billets";
            case PRESENTS -> "Personnes présentes (entrées contrôlées)";
            case INSCRITS -> "Participants inscrits";
            case VISITES -> "Visiteurs des stands";
        };
        String target = "";
        if (c.perimetre() == Perimetre.ACTIVITE) {
            target = " — " + jdbc.queryForObject("select titre from event_activities where id = :id",
                    new MapSqlParameterSource("id", c.cibleId()), String.class);
        } else if (c.perimetre() == Perimetre.STAND) {
            target = " — stand " + jdbc.queryForObject("select numero from stands where id = :id",
                    new MapSqlParameterSource("id", c.cibleId()), String.class);
        }
        return what + " · " + event.getNom() + target;
    }

    private static String filters(Criteria c) {
        String period;
        if (c.du() == null && c.au() == null) {
            period = "Toute la période";
        } else {
            period = (c.du() == null ? "Depuis le début" : "Du " + LABEL.format(ReportService.at(c.du())))
                    + (c.au() == null ? " à aujourd'hui" : " au " + LABEL.format(ReportService.at(c.au())));
        }
        return period + " · " + (c.doublons() ? "doublons inclus" : "sans doublons");
    }

    private static String nz(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    private static String slug(@Nullable String s) {
        return s == null || s.isBlank() ? "evenement" : s;
    }
}
