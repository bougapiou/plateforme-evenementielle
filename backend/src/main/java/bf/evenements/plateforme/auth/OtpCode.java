package bf.evenements.plateforme.auth;

import bf.evenements.plateforme.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One-time code sent by SMS to open « Mes billets » with a phone number (PART-04). */
@Entity
@Table(name = "otp_codes")
@Getter
@Setter
@NoArgsConstructor
public class OtpCode extends BaseEntity {

    /** Phone number, digits only. */
    @Column(nullable = false, length = 30)
    private String telephone;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "expire_le", nullable = false)
    private Instant expireLe;

    @Column(nullable = false)
    private int tentatives = 0;

    @Column(name = "utilise_le")
    private Instant utiliseLe;
}
