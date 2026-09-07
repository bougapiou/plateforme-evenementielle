package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ticket_order_lines")
@Getter
@Setter
@NoArgsConstructor
public class TicketOrderLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private TicketOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_ticket_id", nullable = false)
    private EventTicket eventTicket;

    @Column(nullable = false)
    private int quantite;

    @Column(name = "prix_unitaire", nullable = false, precision = 14, scale = 2)
    private BigDecimal prixUnitaire;

    public BigDecimal lineTotal() {
        return prixUnitaire.multiply(BigDecimal.valueOf(quantite));
    }
}
