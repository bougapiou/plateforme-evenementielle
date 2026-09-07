package bf.evenements.plateforme.structure;

import org.springframework.data.jpa.domain.Specification;

public final class StructureSpecifications {

    private StructureSpecifications() {
    }

    public static Specification<Structure> textSearch(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String like = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("raisonSociale")), like),
                cb.like(cb.lower(root.get("sigle")), like),
                cb.like(cb.lower(root.get("rccm")), like),
                cb.like(cb.lower(root.get("ifu")), like));
    }

    public static Specification<Structure> hasStatus(StructureStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("statut"), status);
    }

    public static Specification<Structure> hasType(StructureType type) {
        return type == null ? null : (root, query, cb) -> cb.equal(root.get("typeStructure"), type);
    }
}
