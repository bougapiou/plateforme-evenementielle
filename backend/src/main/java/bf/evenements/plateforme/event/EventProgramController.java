package bf.evenements.plateforme.event;

import bf.evenements.plateforme.event.dto.ActivityRequest;
import bf.evenements.plateforme.event.dto.ActivityResponse;
import bf.evenements.plateforme.event.dto.PartnerRequest;
import bf.evenements.plateforme.event.dto.PartnerResponse;
import bf.evenements.plateforme.event.dto.SpeakerRequest;
import bf.evenements.plateforme.event.dto.SpeakerResponse;
import bf.evenements.plateforme.rbac.Permissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Programme (activities), speakers and partners of an event — organiser / admin. */
@RestController
@RequestMapping("/api/events/{eventId}")
@RequiredArgsConstructor
@Tag(name = "Programme & intervenants")
@PreAuthorize("hasAuthority('" + Permissions.EVENT_UPDATE + "')")
public class EventProgramController {

    private final EventActivityService activityService;
    private final SpeakerService speakerService;
    private final PartnerService partnerService;

    // --- activities / programme ---

    @GetMapping("/activities")
    @Operation(summary = "Lister les activités (programme)")
    public List<ActivityResponse> activities(@PathVariable UUID eventId) {
        return activityService.list(eventId);
    }

    @PostMapping("/activities")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter une activité au programme")
    public ActivityResponse createActivity(@PathVariable UUID eventId,
                                           @Valid @RequestBody ActivityRequest request) {
        return activityService.create(eventId, request);
    }

    @PutMapping("/activities/{activityId}")
    @Operation(summary = "Modifier une activité")
    public ActivityResponse updateActivity(@PathVariable UUID eventId, @PathVariable UUID activityId,
                                           @Valid @RequestBody ActivityRequest request) {
        return activityService.update(eventId, activityId, request);
    }

    @DeleteMapping("/activities/{activityId}")
    @Operation(summary = "Supprimer une activité")
    public ResponseEntity<Void> deleteActivity(@PathVariable UUID eventId,
                                               @PathVariable UUID activityId) {
        activityService.delete(eventId, activityId);
        return ResponseEntity.noContent().build();
    }

    // --- speakers ---

    @GetMapping("/speakers")
    @Operation(summary = "Lister les intervenants")
    public List<SpeakerResponse> speakers(@PathVariable UUID eventId) {
        return speakerService.list(eventId);
    }

    @PostMapping("/speakers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter un intervenant")
    public SpeakerResponse createSpeaker(@PathVariable UUID eventId,
                                         @Valid @RequestBody SpeakerRequest request) {
        return speakerService.create(eventId, request);
    }

    @PutMapping("/speakers/{speakerId}")
    @Operation(summary = "Modifier un intervenant")
    public SpeakerResponse updateSpeaker(@PathVariable UUID eventId, @PathVariable UUID speakerId,
                                         @Valid @RequestBody SpeakerRequest request) {
        return speakerService.update(eventId, speakerId, request);
    }

    @DeleteMapping("/speakers/{speakerId}")
    @Operation(summary = "Supprimer un intervenant")
    public ResponseEntity<Void> deleteSpeaker(@PathVariable UUID eventId,
                                              @PathVariable UUID speakerId) {
        speakerService.delete(eventId, speakerId);
        return ResponseEntity.noContent().build();
    }

    // --- partners ---

    @GetMapping("/partners")
    @Operation(summary = "Lister les partenaires")
    public List<PartnerResponse> partners(@PathVariable UUID eventId) {
        return partnerService.list(eventId);
    }

    @PostMapping("/partners")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter un partenaire")
    public PartnerResponse createPartner(@PathVariable UUID eventId,
                                         @Valid @RequestBody PartnerRequest request) {
        return partnerService.create(eventId, request);
    }

    @PutMapping("/partners/{partnerId}")
    @Operation(summary = "Modifier un partenaire")
    public PartnerResponse updatePartner(@PathVariable UUID eventId, @PathVariable UUID partnerId,
                                         @Valid @RequestBody PartnerRequest request) {
        return partnerService.update(eventId, partnerId, request);
    }

    @DeleteMapping("/partners/{partnerId}")
    @Operation(summary = "Supprimer un partenaire")
    public ResponseEntity<Void> deletePartner(@PathVariable UUID eventId,
                                              @PathVariable UUID partnerId) {
        partnerService.delete(eventId, partnerId);
        return ResponseEntity.noContent().build();
    }
}
