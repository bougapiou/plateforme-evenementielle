package bf.evenements.plateforme.checkin;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.common.exception.ConflictException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventService;
import bf.evenements.plateforme.rbac.RoleNames;
import bf.evenements.plateforme.rbac.RoleRepository;
import bf.evenements.plateforme.user.User;
import bf.evenements.plateforme.user.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventStaffService {

    private final EventStaffRepository staffRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EventService eventService;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<StaffView> list(UUID eventId) {
        eventService.loadManaged(eventId);
        return staffRepository.findByEventId(eventId).stream().map(StaffView::from).toList();
    }

    @Transactional
    public StaffView add(UUID eventId, String email) {
        Event event = eventService.loadManaged(eventId);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun utilisateur avec cet e-mail : " + email));
        if (staffRepository.existsByEventIdAndUserId(eventId, user.getId())) {
            throw new ConflictException("ALREADY_STAFF", "Cet utilisateur est déjà membre du personnel.");
        }
        EventStaff staff = new EventStaff();
        staff.setEvent(event);
        staff.setUser(user);
        staff.setAjoutePar(currentUser.requireId());
        staff = staffRepository.save(staff);

        if (!user.roleNames().contains(RoleNames.PERSONNEL_CONTROLE)) {
            roleRepository.findByName(RoleNames.PERSONNEL_CONTROLE).ifPresent(user::addRole);
        }
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "EVENT_STAFF_ADDED", "Event", eventId.toString(), null, "user=" + user.getEmail());
        return StaffView.from(staff);
    }

    @Transactional
    public void remove(UUID eventId, UUID userId) {
        eventService.loadManaged(eventId);
        EventStaff staff = staffRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Membre du personnel introuvable"));
        staffRepository.delete(staff);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "EVENT_STAFF_REMOVED", "Event", eventId.toString(), null, "user=" + userId);
    }

    public record StaffView(UUID id, UUID userId, String fullName, String email) {
        static StaffView from(EventStaff s) {
            return new StaffView(s.getId(), s.getUser().getId(), s.getUser().getFullName(),
                    s.getUser().getEmail());
        }
    }
}
