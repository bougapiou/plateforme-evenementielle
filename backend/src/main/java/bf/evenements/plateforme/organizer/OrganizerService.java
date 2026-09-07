package bf.evenements.plateforme.organizer;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ConflictException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.organizer.dto.OrganizerApplyRequest;
import bf.evenements.plateforme.organizer.dto.OrganizerResponse;
import bf.evenements.plateforme.organizer.dto.OrganizerUpdateRequest;
import bf.evenements.plateforme.rbac.RoleNames;
import bf.evenements.plateforme.rbac.RoleRepository;
import bf.evenements.plateforme.structure.Structure;
import bf.evenements.plateforme.structure.StructureMember;
import bf.evenements.plateforme.structure.StructureMemberRepository;
import bf.evenements.plateforme.structure.StructureRepository;
import bf.evenements.plateforme.user.User;
import bf.evenements.plateforme.user.UserRepository;
import bf.evenements.plateforme.user.UserType;
import java.time.Instant;
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
public class OrganizerService {

    private final OrganizerRepository organizerRepository;
    private final StructureRepository structureRepository;
    private final StructureMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;

    @Transactional
    public OrganizerResponse apply(OrganizerApplyRequest request) {
        User me = currentUserEntity();
        if (organizerRepository.existsByUserId(me.getId())) {
            throw new ConflictException("ORGANIZER_EXISTS",
                    "Une demande ou un profil organisateur existe déjà pour ce compte.");
        }
        Organizer organizer = new Organizer();
        organizer.setUser(me);
        organizer.setNomAffichage(request.nomAffichage().trim());
        organizer.setDescription(trimToNull(request.description()));
        organizer.setContactEmail(trimToNull(request.contactEmail()));
        organizer.setContactTelephone(trimToNull(request.contactTelephone()));
        organizer.setSiteWeb(trimToNull(request.siteWeb()));
        organizer.setStructure(resolveManageableStructure(request.structureId(), me.getId()));
        organizer.setStatut(OrganizerStatus.EN_ATTENTE);
        organizer = organizerRepository.save(organizer);

        auditService.record(me.getId(), me.getEmail(), "ORGANIZER_APPLIED", "Organizer",
                organizer.getId().toString(), null, null);
        return OrganizerResponse.from(organizer);
    }

    @Transactional(readOnly = true)
    public OrganizerResponse me() {
        return OrganizerResponse.from(requireMine());
    }

    @Transactional(readOnly = true)
    public boolean hasProfile() {
        return organizerRepository.existsByUserId(currentUser.requireId());
    }

    @Transactional
    public OrganizerResponse updateMe(OrganizerUpdateRequest request) {
        Organizer organizer = requireMine();
        organizer.setNomAffichage(request.nomAffichage().trim());
        organizer.setDescription(trimToNull(request.description()));
        organizer.setLogoUrl(trimToNull(request.logoUrl()));
        organizer.setContactEmail(trimToNull(request.contactEmail()));
        organizer.setContactTelephone(trimToNull(request.contactTelephone()));
        organizer.setSiteWeb(trimToNull(request.siteWeb()));
        organizer.setStructure(resolveManageableStructure(request.structureId(),
                currentUser.requireId()));
        return OrganizerResponse.from(organizer);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizerResponse> adminList(@Nullable OrganizerStatus statut,
                                                     Pageable pageable) {
        Specification<Organizer> spec = statut == null ? null
                : (root, query, cb) -> cb.equal(root.get("statut"), statut);
        return PageResponse.of(organizerRepository.findAll(spec, pageable), OrganizerResponse::from);
    }

    @Transactional(readOnly = true)
    public OrganizerResponse get(UUID id) {
        return OrganizerResponse.from(load(id));
    }

    @Transactional
    public OrganizerResponse approve(UUID id) {
        Organizer organizer = load(id);
        organizer.setStatut(OrganizerStatus.ACTIF);
        organizer.setApprouvePar(currentUser.requireId());
        organizer.setApprouveLe(Instant.now());

        User user = organizer.getUser();
        if (!user.roleNames().contains(RoleNames.ORGANISATEUR)) {
            roleRepository.findByName(RoleNames.ORGANISATEUR).ifPresent(user::addRole);
        }
        if (user.getType() == UserType.PARTICULIER) {
            user.setType(UserType.ORGANISATEUR);
        }
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "ORGANIZER_APPROVED", "Organizer", id.toString(), null, "user=" + user.getEmail());
        return OrganizerResponse.from(organizer);
    }

    @Transactional
    public OrganizerResponse suspend(UUID id) {
        Organizer organizer = load(id);
        organizer.setStatut(OrganizerStatus.SUSPENDU);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "ORGANIZER_SUSPENDED", "Organizer", id.toString(), null, null);
        return OrganizerResponse.from(organizer);
    }

    // --- helpers ---

    private Organizer load(UUID id) {
        return organizerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Organisateur", id));
    }

    private Organizer requireMine() {
        return organizerRepository.findByUserId(currentUser.requireId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun profil organisateur pour ce compte."));
    }

    @Nullable
    private Structure resolveManageableStructure(@Nullable UUID structureId, UUID userId) {
        if (structureId == null) {
            return null;
        }
        Structure structure = structureRepository.findById(structureId)
                .orElseThrow(() -> ResourceNotFoundException.of("Structure", structureId));
        boolean canManage = memberRepository.findByStructureIdAndUserId(structureId, userId)
                .filter(StructureMember::isActive)
                .map(StructureMember::canManage)
                .orElse(false);
        if (!canManage) {
            throw new AccessDeniedException(
                    "Vous devez être administrateur de la structure pour l'associer.");
        }
        return structure;
    }

    private User currentUserEntity() {
        return userRepository.findById(currentUser.requireId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur courant introuvable"));
    }

    @Nullable
    private static String trimToNull(@Nullable String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    /** Guard used by other modules (events) to ensure the acting user is an active organiser. */
    @Transactional(readOnly = true)
    public Organizer requireActiveOrganizer(UUID userId) {
        Organizer organizer = organizerRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("NOT_ORGANIZER",
                        "Vous n'avez pas de profil organisateur actif."));
        if (!organizer.isActive()) {
            throw new BusinessException("ORGANIZER_NOT_ACTIVE",
                    "Votre profil organisateur n'est pas encore approuvé.");
        }
        return organizer;
    }
}
