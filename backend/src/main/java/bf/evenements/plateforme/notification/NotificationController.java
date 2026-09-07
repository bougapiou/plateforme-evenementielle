package bf.evenements.plateforme.notification;

import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.notification.NotificationService.NotificationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    @Operation(summary = "Mes notifications (dans la plateforme)")
    public PageResponse<NotificationView> mine(@PageableDefault(size = 20) Pageable pageable) {
        return service.myNotifications(pageable);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Nombre de notifications non lues")
    public Map<String, Long> unreadCount() {
        return Map.of("count", service.unreadCount());
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Marquer une notification comme lue")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        service.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @Operation(summary = "Tout marquer comme lu")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", service.markAllRead());
    }
}
