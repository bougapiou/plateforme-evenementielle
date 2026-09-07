package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.common.domain.BaseEntity;
import bf.evenements.plateforme.event.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An individual electronic ticket, generated once its order is paid. */
@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
public class Ticket extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private TicketOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_ticket_id", nullable = false)
    private EventTicket eventTicket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 40)
    private String numero;

    @Column(name = "participant_nom", length = 200)
    private String participantNom;

    @Column(name = "participant_email", length = 180)
    private String participantEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus statut = TicketStatus.EMISE;
}
