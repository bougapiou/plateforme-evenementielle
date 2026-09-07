package bf.evenements.plateforme.notification;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdAndCanalOrderByCreatedAtDesc(UUID userId,
                                                               NotificationChannel canal,
                                                               Pageable pageable);

    long countByUserIdAndCanalAndLuFalse(UUID userId, NotificationChannel canal);

    @Modifying
    @Transactional
    @Query("update Notification n set n.lu = true, n.luLe = CURRENT_TIMESTAMP "
            + "where n.userId = :userId and n.lu = false and n.canal = "
            + "bf.evenements.plateforme.notification.NotificationChannel.IN_APP")
    int markAllRead(@Param("userId") UUID userId);
}
