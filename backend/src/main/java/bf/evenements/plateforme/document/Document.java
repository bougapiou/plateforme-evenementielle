package bf.evenements.plateforme.document;

import bf.evenements.plateforme.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A file attached to another entity (registration, stand reservation…). */
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class Document extends BaseEntity {

    @Column(name = "owner_type", nullable = false, length = 40)
    private String ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String nom;

    @Column(name = "type_document", length = 60)
    private String typeDocument;

    @Column(nullable = false, length = 600)
    private String url;

    @Column(length = 120)
    private String mime;

    private Long taille;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;
}
