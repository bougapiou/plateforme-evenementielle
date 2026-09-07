package bf.evenements.plateforme.event;

import bf.evenements.plateforme.common.domain.BaseEntity;
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

@Entity
@Table(name = "partners")
@Getter
@Setter
@NoArgsConstructor
public class Partner extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 150)
    private String nom;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "site_web", length = 255)
    private String siteWeb;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PartnerLevel niveau;

    @Column(nullable = false)
    private int ordre = 0;
}
