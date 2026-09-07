package bf.evenements.plateforme.checkin;

import bf.evenements.plateforme.common.domain.BaseEntity;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "event_staff",
        uniqueConstraints = @UniqueConstraint(name = "uk_event_staff",
                columnNames = {"event_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class EventStaff extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "ajoute_par")
    private UUID ajoutePar;
}
