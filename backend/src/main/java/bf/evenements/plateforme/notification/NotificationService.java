package bf.evenements.plateforme.notification;

import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.user.User;
import bf.evenements.plateforme.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates in-app notifications and mirrors them by e-mail. SMS / WhatsApp are
 * additional {@link NotificationChannel}s, wired later.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final CurrentUserProvider currentUser;

    @Transactional
    public void notify(UUID userId, NotificationType type, String titre, String contenu,
                       @Nullable String lien) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        Notification inApp = new Notification();
        inApp.setUserId(userId);
        inApp.setType(type);
        inApp.setCanal(NotificationChannel.IN_APP);
        inApp.setTitre(titre);
        inApp.setContenu(contenu);
        inApp.setLien(lien);
        inApp.setStatut(Notification.Status.ENVOYEE);
        inApp.setEnvoyeLe(Instant.now());
        repository.save(inApp);

        Notification email = new Notification();
        email.setUserId(userId);
        email.setType(type);
        email.setCanal(NotificationChannel.EMAIL);
        email.setTitre(titre);
        email.setContenu(contenu);
        email.setLien(lien);
        boolean sent = emailSender.send(user.getEmail(), titre,
                contenu + (lien != null ? "\n\n" + lien : ""));
        email.setStatut(sent ? Notification.Status.ENVOYEE : Notification.Status.ECHEC);
        email.setEnvoyeLe(sent ? Instant.now() : null);
        repository.save(email);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> myNotifications(Pageable pageable) {
        return PageResponse.of(
                repository.findByUserIdAndCanalOrderByCreatedAtDesc(
                        currentUser.requireId(), NotificationChannel.IN_APP, pageable),
                NotificationView::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByUserIdAndCanalAndLuFalse(
                currentUser.requireId(), NotificationChannel.IN_APP);
    }

    @Transactional
    public void markRead(UUID id) {
        Notification n = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", id));
        if (!n.getUserId().equals(currentUser.requireId())) {
            throw new AccessDeniedException("Notification d'un autre utilisateur.");
        }
        n.setLu(true);
        n.setLuLe(Instant.now());
    }

    @Transactional
    public int markAllRead() {
        return repository.markAllRead(currentUser.requireId());
    }

    public record NotificationView(UUID id, NotificationType type, String titre, String contenu,
                                   String lien, boolean lu, Instant createdAt) {
        static NotificationView from(Notification n) {
            return new NotificationView(n.getId(), n.getType(), n.getTitre(), n.getContenu(),
                    n.getLien(), n.isLu(), n.getCreatedAt());
        }
    }
}
