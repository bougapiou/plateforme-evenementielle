package bf.evenements.plateforme.invoice;

import bf.evenements.plateforme.common.domain.BaseEntity;
import bf.evenements.plateforme.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceType type;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false, length = 3)
    private String devise = Money.DEFAULT_CURRENCY;

    @Column(name = "client_nom", length = 200)
    private String clientNom;

    @Column(name = "client_details", length = 500)
    private String clientDetails;

    @Column(columnDefinition = "text")
    private String lignes;

    @Column(name = "emise_le", nullable = false)
    private Instant emiseLe = Instant.now();

    public Money amount() {
        return Money.of(montant, devise);
    }

    public enum InvoiceType {
        FACTURE, RECU
    }
}
