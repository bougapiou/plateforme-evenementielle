package bf.evenements.plateforme.accreditation;

import bf.evenements.plateforme.accreditation.dto.AccreditationRequest;
import bf.evenements.plateforme.accreditation.dto.AccreditationResponse;
import bf.evenements.plateforme.rbac.Permissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@Tag(name = "Badges / accréditations")
@PreAuthorize("hasAuthority('" + Permissions.EVENT_UPDATE + "')")
public class AccreditationController {

    private final AccreditationService service;

    @GetMapping("/events/{eventId}/accreditations")
    @Operation(summary = "Lister les accréditations d'un événement")
    public List<AccreditationResponse> list(@PathVariable UUID eventId) {
        return service.list(eventId);
    }

    @PostMapping("/events/{eventId}/accreditations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Délivrer une accréditation (badge)")
    public AccreditationResponse issue(@PathVariable UUID eventId,
                                       @Valid @RequestBody AccreditationRequest request) {
        return service.issue(eventId, request);
    }

    @PostMapping("/accreditations/{id}/revoke")
    @Operation(summary = "Révoquer une accréditation")
    public AccreditationResponse revoke(@PathVariable UUID id) {
        return service.revoke(id);
    }

    @GetMapping(value = "/accreditations/{id}/badge.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Badge d'accréditation en PDF")
    public ResponseEntity<byte[]> badge(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", ContentDisposition.attachment()
                        .filename("badge-" + id + ".pdf").build().toString())
                .body(service.badgePdf(id));
    }

    @GetMapping(value = "/accreditations/{id}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Image QR code d'une accréditation")
    public ResponseEntity<byte[]> qr(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(service.qrPng(id));
    }
}
