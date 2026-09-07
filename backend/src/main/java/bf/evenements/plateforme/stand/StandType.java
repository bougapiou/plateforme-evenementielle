package bf.evenements.plateforme.stand;

import bf.evenements.plateforme.common.domain.BaseEntity;
import bf.evenements.plateforme.common.money.Money;
import bf.evenements.plateforme.event.Event;
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
@Table(name = "stand_types")
@Getter
@Setter
@NoArgsConstructor
public class StandType extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(length = 1000)
    private String description;

    @Column(length = 60)
    private String dimensions;

    @Column(name = "prix_montant", nullable = false, precision = 14, scale = 2)
    private BigDecimal prixMontant = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String devise = Money.DEFAULT_CURRENCY;

    @Column(name = "quantite_totale", nullable = false)
    private int quantiteTotale;

    @Column(length = 1000)
    private String equipements;

    @Column(length = 1000)
    private String conditions;

    @Column(nullable = false)
    private int ordre = 0;

    public Money price() {
        return Money.of(prixMontant, devise);
    }
}
