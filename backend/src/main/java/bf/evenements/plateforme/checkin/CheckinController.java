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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Contrôle à l'entrée")
public class CheckinController {

    private final CheckinService checkinService;
    private final EventStaffService staffService;
    private final ControlService controlService;
    private final bf.evenements.plateforme.common.config.AppProperties appProperties;

    // --- hors ligne / sur place / affectation / auto-enregistrement ---

    @GetMapping("/checkins/events/{eventId}/offline-pack")
    @PreAuthorize("hasAuthority('" + Permissions.CHECKIN_SCAN + "')")
    @Operation(summary = "Télécharger la liste des billets valides (empreintes) pour scanner hors ligne")
    public ControlService.OfflinePack offlinePack(@PathVariable UUID eventId) {
        return controlService.offlinePack(eventId);
    }

    @PostMapping("/checkins/sync")
    @PreAuthorize("hasAuthority('" + Permissions.CHECKIN_SCAN + "')")
    @Operation(summary = "Synchroniser des scans réalisés hors ligne (sans doublon grâce à clientRef)")
    public List<ControlService.SyncResult> sync(@Valid @RequestBody List<@Valid ScanRequest> scans) {
        return controlService.sync(scans);
    }

    public record OnSiteRequest(UUID activityId,
                                @NotBlank @jakarta.validation.constraints.Size(max = 200) String nom,
                                @NotBlank @jakarta.validation.constraints.Pattern(regexp = "^\\+?[0-9 ]{6,20}$",
                                        message = "numéro de téléphone invalide") String telephone) {
    }

    @PostMapping("/checkins/events/{eventId}/sur-place")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.CHECKIN_SCAN + "')")
    @Operation(summary = "Inscription sur place : créer un billet gratuit pour un visiteur et le faire entrer")
    public ControlService.OnSiteResponse onSite(@PathVariable UUID eventId, @Valid @RequestBody OnSiteRequest r) {
        return controlService.registerOnSite(eventId, r.activityId(), r.nom(), r.telephone());
    }

    public record StaffActivitiesRequest(List<UUID> activityIds) {
    }

    @org.springframework.web.bind.annotation.PutMapping("/events/{eventId}/staff/{userId}/activities")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_UPDATE + "')")
    @Operation(summary = "Affecter un agent de contrôle à des activités / salles (liste vide = tout l'événement)")
    public java.util.Set<UUID> assignActivities(@PathVariable UUID eventId, @PathVariable UUID userId,
                                                @RequestBody StaffActivitiesRequest r) {
        return controlService.assignActivities(eventId, userId,
                r.activityIds() == null ? List.of() : r.activityIds());
    }

    @PostMapping("/events/{eventId}/activities/{activityId}/auto-enregistrement")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_UPDATE + "')")
    @Operation(summary = "Activer / désactiver l'auto-enregistrement par QR affiché à l'entrée de la salle")
    public ControlService.SelfCheckinConfig selfCheckin(@PathVariable UUID eventId, @PathVariable UUID activityId,
                                                        @RequestParam boolean actif) {
        return controlService.configureSelfCheckin(eventId, activityId, actif);
    }

    @GetMapping(value = "/events/{eventId}/activities/{activityId}/auto-enregistrement/qr.png",
            produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_UPDATE + "')")
    @Operation(summary = "QR d'auto-enregistrement à imprimer et afficher à l'entrée de la salle")
    public ResponseEntity<byte[]> selfCheckinQr(@PathVariable UUID eventId, @PathVariable UUID activityId) {
        var cfg = controlService.configureSelfCheckin(eventId, activityId, true);
        String url = appProperties.frontendBaseUrl() + "/presence/" + cfg.jeton();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .body(bf.evenements.plateforme.common.web.QrImages.png(url, 600));
    }

    @GetMapping("/public/auto-enregistrement/{jeton}")
    @Operation(summary = "Session ouverte à l'auto-enregistrement (page publique)")
    public ControlService.SelfCheckinInfo selfCheckinInfo(@PathVariable String jeton) {
        return controlService.selfCheckinInfo(jeton);
    }

    public record SelfCheckinRequest(@NotBlank String telephone) {
    }

    @PostMapping("/public/auto-enregistrement/{jeton}")
    @Operation(summary = "Confirmer sa présence à une session avec son numéro de téléphone")
    public ScanResponse selfCheckinConfirm(@PathVariable String jeton, @Valid @RequestBody SelfCheckinRequest r) {
        return controlService.selfCheckin(jeton, r.telephone());
    }

    @GetMapping("/checkins/events")
    @PreAuthorize("hasAuthority('" + Permissions.CHECKIN_SCAN + "')")
    @Operation(summary = "Événements que je peux contrôler (organisateur / personnel / admin)")
    public List<bf.evenements.plateforme.event.dto.EventSummary> myControllableEvents() {
        return checkinService.controllableEvents();
    }

    @GetMapping("/checkins/events/{eventId}/activities")
    @PreAuthorize("hasAuthority('" + Permissions.CHECKIN_SCAN + "')")
    @Operation(summary = "Activités d'un événement pour le contrôle par activité")
    public List<bf.evenements.plateforme.event.dto.ActivityResponse> controllableActivities(
            @PathVariable UUID eventId) {
        return checkinService.controllableActivities(eventId);
    }

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
    @Operation(summary = "Statistiques de contrôle d'un événement (ou d'une activité)")
    public Map<String, Long> stats(@PathVariable UUID eventId,
                                   @RequestParam(required = false) UUID activityId) {
        return activityId == null
                ? checkinService.stats(eventId)
                : checkinService.statsForActivity(eventId, activityId);
    }

    @GetMapping("/events/{eventId}/attendance")
    @PreAuthorize("hasAuthority('" + Permissions.CHECKIN_SCAN + "') or hasAuthority('"
            + Permissions.EVENT_VALIDATE + "')")
    @Operation(summary = "Présence temps réel : flux par événement et par activité")
    public CheckinService.AttendanceView attendance(@PathVariable UUID eventId) {
        return checkinService.attendance(eventId);
    }

    @GetMapping("/events/{eventId}/checkin-details")
    @PreAuthorize("hasAuthority('" + Permissions.CHECKIN_SCAN + "') or hasAuthority('"
            + Permissions.EVENT_VALIDATE + "')")
    @Operation(summary = "Détail des entrées / sorties / ré-entrées, billet par billet")
    public List<CheckinService.TicketFlowView> ticketDetails(@PathVariable UUID eventId,
            @RequestParam(required = false) UUID activityId) {
        return checkinService.ticketDetails(eventId, activityId);
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
        return staffService.add(eventId, request.email(), request.fonction());
    }

    @DeleteMapping("/events/{eventId}/staff/{userId}")
    @PreAuthorize("hasAuthority('" + Permissions.EVENT_UPDATE + "')")
    @Operation(summary = "Retirer un membre du personnel de contrôle")
    public ResponseEntity<Void> removeStaff(@PathVariable UUID eventId, @PathVariable UUID userId) {
        staffService.remove(eventId, userId);
        return ResponseEntity.noContent().build();
    }

    /** {@code fonction}: CONTROLE (default) or PROGRAMME (programme manager). */
    public record AddStaffRequest(@NotBlank @Email String email, StaffFonction fonction) {
    }
}
