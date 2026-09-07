package bf.evenements.plateforme.stats;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import bf.evenements.plateforme.event.Event;

/** Aggregation queries for dashboards. Bound to {@code Event} only to have a repository. */
public interface StatsRepository extends JpaRepository<Event, UUID> {

    // ------------------------------------------------------------- global

    @Query(value = """
            select
              (select count(*) from events) as events_total,
              (select count(*) from events where statut in
                 ('PUBLIE','INSCRIPTIONS_OUVERTES','INSCRIPTIONS_FERMEES','EN_COURS')) as events_actifs,
              (select count(*) from events where statut = 'TERMINE') as events_termines,
              (select count(*) from events where statut = 'SOUMIS') as events_a_valider,
              (select count(*) from users) as users_total,
              (select count(*) from structures) as structures_total,
              (select count(*) from organizers where statut = 'ACTIF') as organisateurs_actifs,
              (select count(*) from registrations where statut = 'CONFIRMEE') as inscriptions_confirmees,
              (select coalesce(sum(quantite_vendue),0) from event_tickets) as billets_vendus,
              (select count(*) from stand_reservations where statut = 'CONFIRME') as stands_confirmes,
              (select coalesce(sum(montant),0) from payments where statut = 'REUSSI') as chiffre_affaires
            """, nativeQuery = true)
    GlobalRow globalOverview();

    // -------------------------------------------------------- per organiser

    @Query(value = """
            select
              (select count(*) from events e join organizers o on o.id = e.organizer_id
                 where o.user_id = :userId) as events_total,
              (select coalesce(sum(t.quantite_vendue),0) from event_tickets t
                 join events e on e.id = t.event_id join organizers o on o.id = e.organizer_id
                 where o.user_id = :userId) as billets_vendus,
              (select coalesce(sum(t.quantite_totale - t.quantite_vendue - t.quantite_reservee),0)
                 from event_tickets t join events e on e.id = t.event_id
                 join organizers o on o.id = e.organizer_id where o.user_id = :userId) as billets_restants,
              (select count(*) from registrations r join events e on e.id = r.event_id
                 join organizers o on o.id = e.organizer_id
                 where o.user_id = :userId and r.statut = 'CONFIRMEE') as inscriptions_confirmees,
              (select count(*) from stand_reservations sr join events e on e.id = sr.event_id
                 join organizers o on o.id = e.organizer_id
                 where o.user_id = :userId and sr.statut = 'CONFIRME') as stands_confirmes,
              (select coalesce(sum(p.montant),0) from payments p join events e on e.id = p.event_id
                 join organizers o on o.id = e.organizer_id
                 where o.user_id = :userId and p.statut = 'REUSSI') as revenus,
              (select count(*) from payments p join events e on e.id = p.event_id
                 join organizers o on o.id = e.organizer_id
                 where o.user_id = :userId and p.statut = 'EN_ATTENTE') as paiements_en_attente
            """, nativeQuery = true)
    OrganizerRow organizerOverview(@Param("userId") UUID userId);

    // ------------------------------------------------------------- per event

    @Query(value = """
            select
              (select coalesce(sum(quantite_totale),0) from event_tickets where event_id = :eventId) as billets_total,
              (select coalesce(sum(quantite_vendue),0) from event_tickets where event_id = :eventId) as billets_vendus,
              (select coalesce(sum(quantite_reservee),0) from event_tickets where event_id = :eventId) as billets_reserves,
              (select count(*) from stands where event_id = :eventId) as stands_total,
              (select count(distinct sr.stand_id) from stand_reservations sr
                 where sr.event_id = :eventId and sr.statut in
                 ('RESERVE_TEMP','ATTENTE_PAIEMENT','PAYE','CONFIRME')) as stands_reserves,
              (select count(*) from registrations where event_id = :eventId and statut = 'CONFIRMEE') as inscriptions_confirmees,
              (select count(*) from registrations where event_id = :eventId and statut = 'EN_ATTENTE') as inscriptions_en_attente,
              (select count(distinct structure_id) from registrations
                 where event_id = :eventId and structure_id is not null) as structures_participantes,
              (select coalesce(sum(montant),0) from payments where event_id = :eventId and statut = 'REUSSI') as revenus,
              (select count(*) from payments where event_id = :eventId and statut = 'EN_ATTENTE') as paiements_en_attente,
              (select count(*) from payments where event_id = :eventId and statut = 'REUSSI') as paiements_reussis,
              (select count(*) from checkins where event_id = :eventId and resultat = 'VALIDE') as entrees_validees
            """, nativeQuery = true)
    EventRow eventOverview(@Param("eventId") UUID eventId);

    @Query(value = """
            select to_char(d.jour, 'YYYY-MM-DD') as jour,
                   coalesce(i.n, 0) as inscriptions,
                   coalesce(pv.montant, 0) as revenus
            from (select generate_series(date_trunc('day', cast(:from as timestamptz)),
                                         date_trunc('day', cast(:to as timestamptz)),
                                         interval '1 day') as jour) d
            left join (select date_trunc('day', created_at) j, count(*) n
                       from registrations where event_id = :eventId group by 1) i on i.j = d.jour
            left join (select date_trunc('day', paid_at) j, sum(montant) montant
                       from payments where event_id = :eventId and statut = 'REUSSI' group by 1) pv on pv.j = d.jour
            order by d.jour
            """, nativeQuery = true)
    List<SeriesRow> eventDailySeries(@Param("eventId") UUID eventId,
                                     @Param("from") java.time.Instant from,
                                     @Param("to") java.time.Instant to);

    @Query(value = """
            select nom as label, quantite_vendue as valeur
            from event_tickets where event_id = :eventId order by ordre, prix_montant
            """, nativeQuery = true)
    List<LabelValueRow> ticketBreakdown(@Param("eventId") UUID eventId);

    interface GlobalRow {
        long getEventsTotal();
        long getEventsActifs();
        long getEventsTermines();
        long getEventsAValider();
        long getUsersTotal();
        long getStructuresTotal();
        long getOrganisateursActifs();
        long getInscriptionsConfirmees();
        long getBilletsVendus();
        long getStandsConfirmes();
        java.math.BigDecimal getChiffreAffaires();
    }

    interface OrganizerRow {
        long getEventsTotal();
        long getBilletsVendus();
        long getBilletsRestants();
        long getInscriptionsConfirmees();
        long getStandsConfirmes();
        java.math.BigDecimal getRevenus();
        long getPaiementsEnAttente();
    }

    interface EventRow {
        long getBilletsTotal();
        long getBilletsVendus();
        long getBilletsReserves();
        long getStandsTotal();
        long getStandsReserves();
        long getInscriptionsConfirmees();
        long getInscriptionsEnAttente();
        long getStructuresParticipantes();
        java.math.BigDecimal getRevenus();
        long getPaiementsEnAttente();
        long getPaiementsReussis();
        long getEntreesValidees();
    }

    interface SeriesRow {
        String getJour();
        long getInscriptions();
        java.math.BigDecimal getRevenus();
    }

    interface LabelValueRow {
        String getLabel();
        long getValeur();
    }
}
