package bf.evenements.plateforme.user;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Permanent removal of a user together with the business data hanging off the
 * account. Deliberately explicit (one statement per table, in FK order) so that
 * nothing outside the user's own data is deleted.
 */
@Service
@RequiredArgsConstructor
public class UserDeletionService {

    private final EntityManager em;

    /** What would be lost if the account were deleted — shown to the admin as a warning. */
    public record History(long commandes, long billets, long inscriptions, long paiements,
                          long factures, long reservationsStands, long structures,
                          long evenementsOrganises) {

        public boolean any() {
            return commandes + billets + inscriptions + paiements + factures
                    + reservationsStands + structures + evenementsOrganises > 0;
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            append(sb, commandes, "commande(s)");
            append(sb, billets, "billet(s)");
            append(sb, inscriptions, "inscription(s)");
            append(sb, paiements, "paiement(s)");
            append(sb, factures, "facture(s)");
            append(sb, reservationsStands, "réservation(s) de stand");
            append(sb, structures, "structure(s)");
            append(sb, evenementsOrganises, "événement(s) organisé(s)");
            return sb.toString();
        }

        private static void append(StringBuilder sb, long n, String label) {
            if (n > 0) {
                sb.append(sb.length() > 0 ? ", " : "").append(n).append(' ').append(label);
            }
        }
    }

    public History history(UUID userId) {
        return new History(
                count("select count(*) from ticket_orders where user_id = :u", userId),
                count("select count(*) from tickets t join ticket_orders o on o.id = t.order_id "
                        + "where o.user_id = :u", userId),
                count("select count(*) from registrations where user_id = :u", userId),
                count("select count(*) from payments where user_id = :u", userId),
                count("select count(*) from invoices where user_id = :u", userId),
                count("select count(*) from stand_reservations where user_id = :u", userId),
                count("select count(*) from structures where owner_user_id = :u", userId),
                count("select count(*) from events e join organizers o on o.id = e.organizer_id "
                        + "where o.user_id = :u", userId));
    }

    /**
     * Deletes the user and everything that is theirs: orders / tickets / QR codes and their
     * scans, registrations, payments, invoices, stand reservations, owned structures and
     * the (event-less) organiser profile. Sold-ticket counters are given back to the
     * events. Caller must have refused users that organise events.
     */
    public void purge(UUID userId) {
        em.flush();

        // Give the sold / held quota back to the events before the orders disappear.
        exec("update event_tickets et set quantite_vendue = greatest(0, et.quantite_vendue - s.q) "
                + "from (select l.event_ticket_id as id, sum(l.quantite) as q "
                + "      from ticket_order_lines l join ticket_orders o on o.id = l.order_id "
                + "      where o.user_id = :u and o.statut = 'PAYEE' group by l.event_ticket_id) s "
                + "where et.id = s.id", userId);
        exec("update event_tickets et set quantite_reservee = greatest(0, et.quantite_reservee - s.q) "
                + "from (select l.event_ticket_id as id, sum(l.quantite) as q "
                + "      from ticket_order_lines l join ticket_orders o on o.id = l.order_id "
                + "      where o.user_id = :u and o.statut = 'EN_ATTENTE' group by l.event_ticket_id) s "
                + "where et.id = s.id", userId);

        // Scans of this user's tickets, then scans done BY this user (kept, anonymised).
        exec("delete from checkins where ticket_id in (select t.id from tickets t "
                + "join ticket_orders o on o.id = t.order_id where o.user_id = :u)", userId);
        exec("update checkins set scanned_by = null where scanned_by = :u", userId);

        exec("delete from invoices where user_id = :u or payment_id in "
                + "(select id from payments where user_id = :u)", userId);
        exec("delete from payments where user_id = :u", userId);
        exec("delete from registrations where user_id = :u", userId);   // participants cascade
        exec("delete from ticket_orders where user_id = :u", userId);   // lines, tickets, QR cascade
        exec("delete from stand_reservations where user_id = :u", userId);

        exec("update event_staff set ajoute_par = null where ajoute_par = :u", userId);

        // Structures owned by the user: detach whatever else points at them, then delete.
        String mine = "(select id from structures where owner_user_id = :u)";
        exec("update organizers set structure_id = null where structure_id in " + mine, userId);
        exec("update ticket_orders set structure_id = null where structure_id in " + mine, userId);
        exec("update stand_reservations set structure_id = null where structure_id in " + mine, userId);
        exec("update registrations set structure_id = null where structure_id in " + mine, userId);
        exec("delete from structures where owner_user_id = :u", userId);
        exec("update structures set created_by = null where created_by = :u", userId);

        exec("update organizers set approuve_par = null where approuve_par = :u", userId);
        exec("delete from organizers where user_id = :u", userId);

        // roles, refresh / reset tokens, notifications, staff and memberships cascade.
        exec("delete from users where id = :u", userId);
        em.clear();
    }

    private long count(String sql, UUID userId) {
        return ((Number) em.createNativeQuery(sql).setParameter("u", userId).getSingleResult())
                .longValue();
    }

    private void exec(String sql, UUID userId) {
        em.createNativeQuery(sql).setParameter("u", userId).executeUpdate();
    }
}
