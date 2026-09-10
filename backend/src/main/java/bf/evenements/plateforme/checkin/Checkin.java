package bf.evenements.plateforme.checkin;

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

/** Journal entry for one QR scan at the entrance. */
@Entity
@Table(name = "checkins")
@Getter
@Setter
@NoArgsConstructor
public class Checkin extends BaseEntity {

    @Column(name = "qr_code_id")
    private UUID qrCodeId;

    @Column(name = "ticket_id")
    private UUID ticketId;

    /** Set instead of {@link #ticketId} when the scan matched an accreditation badge. */
    @Column(name = "accreditation_id")
    private UUID accreditationId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    /** Activity this scan controlled, or null for a general entry. */
    @Column(name = "activity_id")
    private UUID activityId;

    @Column(name = "scanned_by")
    private UUID scannedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckinResult resultat;

    /** ENTREE (default) or SORTIE — only meaningful when the event tracks exits. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CheckinDirection sens = CheckinDirection.ENTREE;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt = Instant.now();

    @Column(length = 255)
    private String detail;
}
