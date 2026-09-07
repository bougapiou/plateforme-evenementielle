package bf.evenements.plateforme.registration;

import bf.evenements.plateforme.common.domain.BaseEntity;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.structure.Structure;
import bf.evenements.plateforme.ticket.TicketOrder;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "registrations")
@Getter
@Setter
@NoArgsConstructor
public class Registration extends BaseEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationType type = RegistrationType.PARTICULIER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus statut = RegistrationStatus.EN_ATTENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_order_id")
    private TicketOrder ticketOrder;

    @Column(name = "contact_nom", length = 200)
    private String contactNom;

    @Column(name = "contact_email", length = 180)
    private String contactEmail;

    @Column(name = "contact_telephone", length = 30)
    private String contactTelephone;

    @Column(name = "nombre_participants", nullable = false)
    private int nombreParticipants = 1;

    @Column(columnDefinition = "text")
    private String informations;

    @Column(name = "motif_refus", length = 1000)
    private String motifRefus;

    @Column(name = "confirmee_le")
    private Instant confirmeeLe;

    @OneToMany(mappedBy = "registration", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Participant> participants = new ArrayList<>();

    public void addParticipant(Participant p) {
        p.setRegistration(this);
        participants.add(p);
    }

    public boolean belongsTo(UUID userId) {
        return user != null && user.getId().equals(userId);
    }
}
