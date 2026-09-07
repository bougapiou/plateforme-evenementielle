package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.common.domain.BaseEntity;
import bf.evenements.plateforme.common.money.Money;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.structure.Structure;
import bf.evenements.plateforme.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ticket_orders")
@Getter
@Setter
@NoArgsConstructor
public class TicketOrder extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structure_id")
    private Structure structure;

    @Column(name = "montant_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal montantTotal = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String devise = Money.DEFAULT_CURRENCY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketOrderStatus statut = TicketOrderStatus.EN_ATTENTE;

    @Column(name = "acheteur_nom", length = 200)
    private String acheteurNom;

    @Column(name = "acheteur_email", length = 180)
    private String acheteurEmail;

    @Column(name = "expire_le")
    private Instant expireLe;

    @Column(name = "paye_le")
    private Instant payeLe;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TicketOrderLine> lines = new ArrayList<>();

    public Money total() {
        return Money.of(montantTotal, devise);
    }

    public void addLine(TicketOrderLine line) {
        line.setOrder(this);
        lines.add(line);
    }

    public int totalQuantity() {
        return lines.stream().mapToInt(TicketOrderLine::getQuantite).sum();
    }

    public boolean isPending() {
        return statut == TicketOrderStatus.EN_ATTENTE;
    }
}
