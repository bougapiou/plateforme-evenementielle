package bf.evenements.plateforme.checkin;

import bf.evenements.plateforme.checkin.CheckinService.CheckinView;
import bf.evenements.plateforme.checkin.EventStaffService.StaffView;
import bf.evenements.plateforme.checkin.dto.ScanRequest;
import bf.evenements.plateforme.checkin.dto.ScanResponse;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.rbac.Permissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Contrôle à l'entrée")
public class CheckinController {

    private final CheckinService checkinService;
    private final EventStaffService staffService;

    @PostMapping("/checkins/scan")
    @PreAuthorize("hasAuthority('" + Permissions.CHECKIN_SCAN + "')")
    @Operation(summary = "Scanner un QR code (VALIDE / DÉJÀ UTILISÉ / INVALIDE)")
    public ScanResponse scan(@Valid @RequestBody ScanRequest request) {
        return checkinService.scan(request);
    }

    @GetMapping("/events/{eventId}/checkins")
    @PreAuthorize("hasAuthority('" + Permissions.CHECKIN_SCAN + "') or hasAuthority('"
            + Permissions.EVENT_READ + "')")
    @Operation(summary = "Journal des contrôles d'un événement")
    public PageResponse<CheckinView> checkins(@PathVariable UUID eventId,
                                              @PageableDefault(size = 30) Pageable pageable) {
        return checkinService.list(eventId, pageable);
    }

    @GetMapping("/events/{eventId}/checkin-stats")
    @Operation(summary = "Statistiques de contrôle d'un événement")
    public Map<String, Long> stats(@PathVariable UUID eventId) {
        return checkinService.stats(eventId);
    }

    // --- personnel de contrôle ---

    @GetMapping("/events/{eventId}/staff")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_UPDATE + "')")
    @Operation(summary = "Personnel de contrôle d'un événement")
    public List<StaffView> staff(@PathVariable UUID eventId) {
        return staffService.list(eventId);
    }

    @PostMapping("/events/{eventId}/staff")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_UPDATE + "')")
    @Operation(summary = "Ajouter un membre du personnel de contrôle")
    public StaffView addStaff(@PathVariable UUID eventId, @Valid @RequestBody AddStaffRequest request) {
        return staffService.add(eventId, request.email());
    }

    @DeleteMapping("/events/{eventId}/staff/{userId}")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_UPDATE + "')")
    @Operation(summary = "Retirer un membre du personnel de contrôle")
    public ResponseEntity<Void> removeStaff(@PathVariable UUID eventId, @PathVariable UUID userId) {
        staffService.remove(eventId, userId);
        return ResponseEntity.noContent().build();
    }

    public record AddStaffRequest(@NotBlank @Email String email) {
    }
}
