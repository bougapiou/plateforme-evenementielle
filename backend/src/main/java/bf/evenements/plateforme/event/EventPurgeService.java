package bf.evenements.plateforme.event;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Suppression TOTALE d'un événement par un administrateur, avec tout ce qui en dépend :
 * entrées scannées, factures, paiements, inscriptions (et leurs documents), commandes,
 * billets et QR codes, réservations de stands. Le reste (programme, intervenants, partenaires,
 * catégories de billets, stands, accréditations, personnel, capteurs, passages aux stands)
 * part avec l'événement grâce aux « on delete cascade » de la base.
 *
 * Volontairement explicite : une requête par table, dans l'ordre des clés étrangères, et
 * toujours restreinte à CET événement — rien d'autre n'est touché.
 */
@Service
@RequiredArgsConstructor
public class EventPurgeService {

    private final EntityManager em;

    /** Ce qu'une suppression totale effacerait — montré à l'admin avant confirmation. */
    public record Bilan(long commandes, long billets, long inscriptions, long paiements, long factures,
                        long reservationsStands, long entrees) {

        public boolean vide() {
            return commandes + billets + inscriptions + paiements + factures + reservationsStands + entrees == 0;
        }

        public String resume() {
            StringBuilder sb = new StringBuilder();
            ajouter(sb, billets, "billet(s)");
            ajouter(sb, commandes, "commande(s)");
            ajouter(sb, inscriptions, "inscription(s)");
            ajouter(sb, paiements, "paiement(s)");
            ajouter(sb, factures, "facture(s)");
            ajouter(sb, reservationsStands, "réservation(s) de stand");
            ajouter(sb, entrees, "entrée(s) scannée(s)");
            return sb.toString();
        }

        private static void ajouter(StringBuilder sb, long n, String libelle) {
            if (n > 0) {
                sb.append(sb.length() > 0 ? ", " : "").append(n).append(' ').append(libelle);
            }
        }
    }

    /** Paiements rattachés à l'événement, directement ou via ses commandes / réservations / inscriptions. */
    private static final String PAIEMENTS =
            "select p.id from payments p where p.event_id = :e"
                    + " or p.ticket_order_id in (select id from ticket_orders where event_id = :e)"
                    + " or p.stand_reservation_id in (select id from stand_reservations where event_id = :e)"
                    + " or p.registration_id in (select id from registrations where event_id = :e)";

    public Bilan bilan(UUID eventId) {
        return new Bilan(
                count("select count(*) from ticket_orders where event_id = :e", eventId),
                count("select count(*) from tickets where event_id = :e", eventId),
                count("select count(*) from registrations where event_id = :e", eventId),
                count("select count(*) from (" + PAIEMENTS + ") x", eventId),
                count("select count(*) from invoices where event_id = :e or payment_id in (" + PAIEMENTS + ")", eventId),
                count("select count(*) from stand_reservations where event_id = :e", eventId),
                count("select count(*) from checkins where event_id = :e", eventId));
    }

    /** Comptes concernés (billets, inscriptions, réservations de stand) — à prévenir avant la suppression. */
    @SuppressWarnings("unchecked")
    public java.util.List<UUID> personnesConcernees(UUID eventId) {
        return ((java.util.List<Object>) em.createNativeQuery(
                        "select user_id from ticket_orders where event_id = :e and statut = 'PAYEE'"
                                + " union select user_id from registrations where event_id = :e"
                                + " union select user_id from stand_reservations where event_id = :e")
                .setParameter("e", eventId).getResultList())
                .stream().map(o -> o instanceof UUID u ? u : UUID.fromString(o.toString())).toList();
    }

    /** Supprime l'événement et toutes ses données. À appeler dans une transaction. */
    public void purge(UUID eventId) {
        em.flush();
        exec("delete from checkins where event_id = :e"
                + " or ticket_id in (select id from tickets where event_id = :e)", eventId);
        exec("delete from invoices where event_id = :e or payment_id in (" + PAIEMENTS + ")", eventId);
        exec("delete from payments where id in (" + PAIEMENTS + ")", eventId);
        exec("delete from documents where owner_type = 'REGISTRATION'"
                + " and owner_id in (select id from registrations where event_id = :e)", eventId);
        exec("delete from registrations where event_id = :e", eventId);       // participants : cascade
        exec("delete from ticket_orders where event_id = :e", eventId);       // lignes, billets, QR : cascade
        exec("delete from tickets where event_id = :e", eventId);             // sécurité : billets orphelins
        exec("delete from stand_reservations where event_id = :e", eventId);
        exec("delete from events where id = :e", eventId);                    // programme, stands… : cascade
        em.clear();
    }

    private long count(String sql, UUID eventId) {
        return ((Number) em.createNativeQuery(sql).setParameter("e", eventId).getSingleResult()).longValue();
    }

    private void exec(String sql, UUID eventId) {
        em.createNativeQuery(sql).setParameter("e", eventId).executeUpdate();
    }
}
