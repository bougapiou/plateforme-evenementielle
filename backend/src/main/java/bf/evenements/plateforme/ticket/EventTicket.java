package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.common.domain.BaseEntity;
import bf.evenements.plateforme.common.money.Money;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventActivity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A ticket category of an event: price, quota and (optionally) the activities it
 * grants access to. Counters are updated under a pessimistic lock.
 */
@Entity
@Table(name = "event_tickets")
@Getter
@Setter
@NoArgsConstructor
public class EventTicket extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(length = 1000)
    private String description;

    @Column(name = "prix_montant", nullable = false, precision = 14, scale = 2)
    private BigDecimal prixMontant = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String devise = Money.DEFAULT_CURRENCY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketScope portee = TicketScope.EVENEMENT;

    @Column(name = "quantite_totale", nullable = false)
    private int quantiteTotale;

    @Column(name = "quantite_vendue", nullable = false)
    private int quantiteVendue = 0;

    @Column(name = "quantite_reservee", nullable = false)
    private int quantiteReservee = 0;

    @Column(name = "limite_par_utilisateur", nullable = false)
    private int limiteParUtilisateur = 10;

    @Column(name = "vente_debut")
    private Instant venteDebut;

    @Column(name = "vente_fin")
    private Instant venteFin;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(nullable = false)
    private int ordre = 0;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "event_ticket_activities",
            joinColumns = @JoinColumn(name = "event_ticket_id"),
            inverseJoinColumns = @JoinColumn(name = "activity_id"))
    private Set<EventActivity> activities = new LinkedHashSet<>();

    public Money price() {
        return Money.of(prixMontant, devise);
    }

    public int quantiteRestante() {
        return quantiteTotale - quantiteVendue - quantiteReservee;
    }

    public boolean onSale(Instant now) {
        if (!actif) {
            return false;
        }
        if (venteDebut != null && now.isBefore(venteDebut)) {
            return false;
        }
        if (venteFin != null && now.isAfter(venteFin)) {
            return false;
        }
        return quantiteRestante() > 0;
    }
}
