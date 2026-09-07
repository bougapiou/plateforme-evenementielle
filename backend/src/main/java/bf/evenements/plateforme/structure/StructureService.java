package bf.evenements.plateforme.structure;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ConflictException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.rbac.Permissions;
import bf.evenements.plateforme.rbac.RoleNames;
import bf.evenements.plateforme.rbac.RoleRepository;
import bf.evenements.plateforme.structure.dto.AddMemberRequest;
import bf.evenements.plateforme.structure.dto.StructureMemberResponse;
import bf.evenements.plateforme.structure.dto.StructureRequest;
import bf.evenements.plateforme.structure.dto.StructureResponse;
import bf.evenements.plateforme.structure.dto.StructureSummary;
import bf.evenements.plateforme.user.User;
import bf.evenements.plateforme.user.UserRepository;
import bf.evenements.plateforme.user.UserType;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StructureService {

    private final StructureRepository structureRepository;
    private final StructureMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;

    @Transactional
    public StructureResponse create(StructureRequest request) {
        User me = currentUserEntity();
        if (StringUtils.hasText(request.rccm())
                && structureRepository.existsByRccmIgnoreCase(request.rccm().trim())) {
            throw new ConflictException("RCCM_ALREADY_USED", "Ce numero RCCM est deja enregistre.");
        }

        Structure structure = new Structure();
        apply(structure, request);
        structure.setStatut(StructureStatus.EN_ATTENTE);
        structure.setOwner(me);
        structure.setCreatedBy(me.getId());
        structure.addMember(new StructureMember(structure, me, StructureMemberRole.PROPRIETAIRE,
                "Représentant légal"));
        structure = structureRepository.save(structure);

        grantStructureRole(me);

        auditService.record(me.getId(), me.getEmail(), "STRUCTURE_CREATED", "Structure",
                structure.getId().toString(), null, "raisonSociale=" + structure.getRaisonSociale());
        return StructureResponse.from(structure, StructureMemberRole.PROPRIETAIRE);
    }

    @Transactional(readOnly = true)
    public List<StructureSummary> mine() {
        return structureRepository.findAllForMember(currentUser.requireId()).stream()
                .map(StructureSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StructureResponse get(UUID id) {
        Structure structure = load(id);
        StructureMemberRole role = requireReadAccess(structure);
        return StructureResponse.from(structure, role);
    }

    @Transactional
    public StructureResponse update(UUID id, StructureRequest request) {
        Structure structure = load(id);
        StructureMemberRole role = requireManageAccess(structure);
        apply(structure, request);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "STRUCTURE_UPDATED", "Structure", id.toString(), null, null);
        return StructureResponse.from(structure, role);
    }

    @Transactional(readOnly = true)
    public PageResponse<StructureSummary> adminList(@Nullable String search,
                                                   @Nullable StructureStatus statut,
                                                   @Nullable StructureType type, Pageable pageable) {
        Specification<Structure> spec = Specification.allOf(
                StructureSpecifications.textSearch(search),
                StructureSpecifications.hasStatus(statut),
                StructureSpecifications.hasType(type));
        return PageResponse.of(structureRepository.findAll(spec, pageable), StructureSummary::from);
    }

    @Transactional
    public StructureResponse updateStatus(UUID id, StructureStatus statut) {
        Structure structure = load(id);
        structure.setStatut(statut);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "STRUCTURE_STATUS_CHANGED", "Structure", id.toString(), null, "statut=" + statut);
        return StructureResponse.from(structure, currentMemberRole(structure));
    }

    // --- members ---

    @Transactional(readOnly = true)
    public List<StructureMemberResponse> listMembers(UUID structureId) {
        Structure structure = load(structureId);
        requireReadAccess(structure);
        return memberRepository.findByStructureIdOrderByRoleInterneAscCreatedAtAsc(structureId).stream()
                .map(StructureMemberResponse::from)
                .toList();
    }

    @Transactional
    public StructureMemberResponse addMember(UUID structureId, AddMemberRequest request) {
        Structure structure = load(structureId);
        requireManageAccess(structure);
        if (request.roleInterne() == StructureMemberRole.PROPRIETAIRE) {
            throw new BusinessException("OWNER_UNIQUE",
                    "Le rôle propriétaire ne peut pas être attribué à un second membre.");
        }
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun utilisateur avec cet e-mail : " + request.email()));
        memberRepository.findByStructureIdAndUserId(structureId, user.getId()).ifPresent(m -> {
            throw new ConflictException("ALREADY_MEMBER", "Cet utilisateur est déjà membre.");
        });
        StructureMember member = new StructureMember(structure, user, request.roleInterne(),
                request.fonction());
        structure.getMembers().add(member);
        member = memberRepository.save(member);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "STRUCTURE_MEMBER_ADDED", "Structure", structureId.toString(), null,
                "user=" + user.getEmail() + " role=" + request.roleInterne());
        return StructureMemberResponse.from(member);
    }

    @Transactional
    public void removeMember(UUID structureId, UUID userId) {
        Structure structure = load(structureId);
        requireManageAccess(structure);
        StructureMember member = memberRepository.findByStructureIdAndUserId(structureId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Membre introuvable"));
        if (member.getRoleInterne() == StructureMemberRole.PROPRIETAIRE) {
            throw new BusinessException("OWNER_LOCKED", "Le propriétaire ne peut pas être retiré.");
        }
        structure.getMembers().remove(member);
        memberRepository.delete(member);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "STRUCTURE_MEMBER_REMOVED", "Structure", structureId.toString(), null,
                "user=" + userId);
    }

    // --- helpers ---

    Structure load(UUID id) {
        return structureRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Structure", id));
    }

    /** Returns the active member of {@code structure} for the current user, verifying a
     *  {@link StructureMember} exists is left to the caller. */
    @Nullable
    private StructureMemberRole currentMemberRole(Structure structure) {
        return memberRepository.findByStructureIdAndUserId(structure.getId(), currentUser.requireId())
                .filter(StructureMember::isActive)
                .map(StructureMember::getRoleInterne)
                .orElse(null);
    }

    @Nullable
    StructureMemberRole requireReadAccess(Structure structure) {
        StructureMemberRole role = currentMemberRole(structure);
        if (role == null && !currentUser.hasAuthority(Permissions.STRUCTURE_READ)) {
            throw new AccessDeniedException("Accès à la structure refusé.");
        }
        return role;
    }

    StructureMemberRole requireManageAccess(Structure structure) {
        StructureMemberRole role = currentMemberRole(structure);
        boolean canManage = role == StructureMemberRole.PROPRIETAIRE
                || role == StructureMemberRole.ADMINISTRATEUR;
        if (!canManage && !currentUser.hasAuthority(Permissions.STRUCTURE_MANAGE)) {
            throw new AccessDeniedException("Gestion de la structure réservée aux administrateurs de la structure.");
        }
        return role;
    }

    private User currentUserEntity() {
        return userRepository.findById(currentUser.requireId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur courant introuvable"));
    }

    private void grantStructureRole(User user) {
        if (user.roleNames().contains(RoleNames.STRUCTURE)) {
            return;
        }
        roleRepository.findByName(RoleNames.STRUCTURE).ifPresent(user::addRole);
        if (user.getType() == UserType.PARTICULIER) {
            user.setType(UserType.STRUCTURE);
        }
    }

    private void apply(Structure s, StructureRequest r) {
        s.setRaisonSociale(r.raisonSociale().trim());
        s.setSigle(trimToNull(r.sigle()));
        s.setTypeStructure(r.typeStructure());
        s.setSecteurActivite(trimToNull(r.secteurActivite()));
        s.setRccm(trimToNull(r.rccm()));
        s.setIfu(trimToNull(r.ifu()));
        s.setAdresse(trimToNull(r.adresse()));
        s.setVille(trimToNull(r.ville()));
        s.setPays(StringUtils.hasText(r.pays()) ? r.pays().trim() : "Burkina Faso");
        s.setTelephone(trimToNull(r.telephone()));
        s.setEmail(trimToNull(r.email()));
        s.setSiteWeb(trimToNull(r.siteWeb()));
        s.setLogoUrl(trimToNull(r.logoUrl()));
        s.setDescription(trimToNull(r.description()));
    }

    @Nullable
    private static String trimToNull(@Nullable String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
