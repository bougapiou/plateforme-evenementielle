package bf.evenements.plateforme.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limited retention of personal data (SEC-08, loi n°001-2021/AN): every night,
 * the participants of events finished for more than
 * {@code app.data.retention-months} are anonymised — names, phones and e-mails
 * on tickets, orders, registrations, stand visits and badges, plus the guest
 * accounts that only served those events. Counts and statistics are kept
 * (rows stay, only identities are removed). Disabled when the value is 0.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionJob {

    static final String ANONYME = "Anonyme";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;

    @Value("${app.data.retention-months:12}")
    private int retentionMonths = 12;

    @Scheduled(cron = "${app.data.retention-cron:0 30 3 * * *}", zone = "Africa/Ouagadougou")
    public void nightly() {
        if (retentionMonths > 0) {
            anonymiseExpired(Instant.now());
        }
    }

    /** Anonymises the events finished before {@code now − retention}; returns their number. */
    @Transactional
    public int anonymiseExpired(Instant now) {
        Instant cutoff = now.atOffset(ZoneOffset.UTC).minusMonths(retentionMonths).toInstant();
        List<UUID> events = jdbc.queryForList("""
                select id from events
                where date_fin < :cutoff and donnees_anonymisees_le is null
                """, new MapSqlParameterSource("cutoff", Timestamp.from(cutoff)), UUID.class);
        for (UUID eventId : events) {
            anonymiseEvent(eventId, now);
        }
        // Stand-alone visitor counting (no event): same retention on the passage date.
        jdbc.update("""
                update pointage_passages set nom = null, prenom = null, telephone = null
                where created_at < :cutoff and (nom is not null or prenom is not null or telephone is not null)
                """, new MapSqlParameterSource("cutoff", Timestamp.from(cutoff)));
        // One-time SMS codes are useless after a day.
        jdbc.update("delete from otp_codes where created_at < :d",
                new MapSqlParameterSource("d", Timestamp.from(now.minusSeconds(86_400))));
        if (!events.isEmpty()) {
            log.info("Anonymisation : {} événement(s) terminé(s) depuis plus de {} mois", events.size(),
                    retentionMonths);
        }
        return events.size();
    }

    /** Anonymises one event right away (also used by the admin and the tests). */
    @Transactional
    public void anonymiseEvent(UUID eventId) {
        anonymiseEvent(eventId, Instant.now());
    }

    private void anonymiseEvent(UUID eventId, Instant now) {
        var p = new MapSqlParameterSource("e", eventId).addValue("anon", ANONYME)
                .addValue("now", Timestamp.from(now));
        jdbc.update("""
                update tickets set participant_nom = :anon, participant_email = null, participant_telephone = null
                where event_id = :e""", p);
        jdbc.update("""
                update ticket_orders set acheteur_nom = :anon, acheteur_email = null
                where event_id = :e""", p);
        jdbc.update("""
                update participants set nom = :anon, prenom = null, email = null, telephone = null
                where registration_id in (select id from registrations where event_id = :e)""", p);
        jdbc.update("""
                update registrations set contact_nom = :anon, contact_email = null, contact_telephone = null
                where event_id = :e""", p);
        jdbc.update("""
                update visites_stand set nom = null, prenom = null, telephone = null
                where event_id = :e""", p);
        jdbc.update("""
                update accreditations set personne_nom = :anon, personne_email = null, photo_url = null
                where event_id = :e""", p);
        // Guest accounts whose every order belongs to anonymised events.
        int guests = jdbc.update("""
                update users u set first_name = '', last_name = :anon, phone = null,
                       email = 'anonyme-' || u.id || '@guest.plateforme.local'
                where u.guest and u.last_name <> :anon
                  and exists (select 1 from ticket_orders o where o.user_id = u.id and o.event_id = :e)
                  and not exists (
                        select 1 from ticket_orders o join events ev on ev.id = o.event_id
                        where o.user_id = u.id and ev.id <> :e and ev.donnees_anonymisees_le is null)
                """, p);
        jdbc.update("update events set donnees_anonymisees_le = :now where id = :e", p);
        auditService.record(null, null, "DATA_ANONYMIZED", "Event", eventId.toString(), null,
                "comptes invites anonymises=" + guests);
    }
}
