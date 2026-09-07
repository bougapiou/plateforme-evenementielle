package bf.evenements.plateforme.structure;

/** Role of a user inside a structure (independent of platform RBAC roles). */
public enum StructureMemberRole {
    /** Full control, cannot be removed; exactly one per structure. */
    PROPRIETAIRE,
    /** Can manage the structure profile and its members. */
    ADMINISTRATEUR,
    /** Can act on behalf of the structure (registrations, stand reservations). */
    MEMBRE
}
