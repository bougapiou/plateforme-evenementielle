package bf.evenements.plateforme.event;

import bf.evenements.plateforme.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "event_categories")
@Getter
@Setter
@NoArgsConstructor
public class EventCategory extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(nullable = false, length = 140)
    private String slug;

    @Column(length = 500)
    private String description;

    @Column(length = 60)
    private String icone;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(nullable = false)
    private int ordre = 0;

    public EventCategory(String nom, String slug, String description, String icone, int ordre) {
        this.nom = nom;
        this.slug = slug;
        this.description = description;
        this.icone = icone;
        this.ordre = ordre;
    }
}
