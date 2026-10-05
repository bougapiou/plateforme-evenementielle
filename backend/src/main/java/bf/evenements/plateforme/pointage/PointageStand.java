package bf.evenements.plateforme.pointage;

import bf.evenements.plateforme.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un point de comptage nommé (ex. un ministère) pour le module Pointage des visiteurs — pas un
 * stand réservable du module billetterie, et sans lien avec un événement.
 */
@Entity
@Table(name = "pointage_stands")
@Getter
@Setter
@NoArgsConstructor
public class PointageStand extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String nom;

    @Column(nullable = false)
    private int ordre = 0;

    public PointageStand(String nom, int ordre) {
        this.nom = nom;
        this.ordre = ordre;
    }
}
