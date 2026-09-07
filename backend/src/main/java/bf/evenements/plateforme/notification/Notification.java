package bf.evenements.plateforme.notification;

import bf.evenements.plateforme.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel canal = NotificationChannel.IN_APP;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(nullable = false, length = 2000)
    private String contenu;

    @Column(length = 500)
    private String lien;

    @Column(nullable = false)
    private boolean lu = false;

    @Column(name = "lu_le")
    private Instant luLe;

    @Column(name = "envoye_le")
    private Instant envoyeLe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status statut = Status.EN_ATTENTE;

    public enum Status {
        EN_ATTENTE, ENVOYEE, ECHEC
    }
}
