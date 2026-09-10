package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.rbac.Permissions;
import bf.evenements.plateforme.ticket.dto.TicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
@Tag(name = "Billets électroniques")
public class ActivityAttendanceController {

    private final ActivityAttendanceService attendanceService;

    @PostMapping("/{activityId}/attend")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.TICKET_PURCHASE + "')")
    @Operation(summary = "Participer à une activité en accès gratuit (billet + QR immédiats)")
    public TicketResponse attend(@PathVariable UUID activityId) {
        return attendanceService.attend(activityId);
    }
}
