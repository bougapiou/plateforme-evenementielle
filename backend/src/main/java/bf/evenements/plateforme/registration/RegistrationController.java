package bf.evenements.plateforme.registration;

import bf.evenements.plateforme.common.security.AuthenticatedUser;
import bf.evenements.plateforme.common.security.CurrentUser;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.document.DocumentService;
import bf.evenements.plateforme.event.dto.RejectRequest;
import bf.evenements.plateforme.rbac.Permissions;
import bf.evenements.plateforme.registration.dto.RegisterParticipationRequest;
import bf.evenements.plateforme.registration.dto.RegistrationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Inscriptions")
public class RegistrationController {

    private static final String OWNER = "REGISTRATION";

    private final RegistrationService registrationService;
    private final DocumentService documentService;

    @PostMapping("/events/{eventId}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.REGISTRATION_CREATE + "')")
    @Operation(summary = "S'inscrire à un événement (particulier ou structure)")
    public RegistrationResponse register(@PathVariable UUID eventId,
                                         @Valid @RequestBody RegisterParticipationRequest request) {
        return registrationService.register(eventId, request);
    }

    @GetMapping("/registrations/my")
    @Operation(summary = "Mes inscriptions")
    public PageResponse<RegistrationResponse> mine(@PageableDefault(size = 20) Pageable pageable) {
        return registrationService.myRegistrations(pageable);
    }

    @GetMapping("/registrations/{id}")
    @Operation(summary = "Détail d'une inscription")
    public RegistrationResponse get(@PathVariable UUID id) {
        return registrationService.get(id);
    }

    @PostMapping("/registrations/{id}/cancel")
    @Operation(summary = "Annuler une inscription")
    public RegistrationResponse cancel(@PathVariable UUID id) {
        return registrationService.cancel(id);
    }

    @GetMapping(value = "/registrations/{id}/confirmation.pdf",
            produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Confirmation d'inscription (PDF)")
    public ResponseEntity<byte[]> confirmation(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(registrationService.confirmationPdf(id));
    }

    @GetMapping("/events/{eventId}/registrations")
    @PreAuthorize("hasAuthority('" + Permissions.REGISTRATION_MANAGE + "')")
    @Operation(summary = "Inscriptions d'un événement (organisateur)")
    public PageResponse<RegistrationResponse> forEvent(@PathVariable UUID eventId,
                                                       @PageableDefault(size = 20) Pageable pageable) {
        return registrationService.forEvent(eventId, pageable);
    }

    @PostMapping("/events/{eventId}/broadcast")
    @PreAuthorize("hasAuthority('" + Permissions.REGISTRATION_MANAGE + "')")
    @Operation(summary = "Message de l'organisateur à tous les inscrits confirmés")
    public java.util.Map<String, Integer> broadcast(@PathVariable UUID eventId,
                                                    @Valid @RequestBody BroadcastRequest request) {
        return java.util.Map.of("destinataires",
                registrationService.broadcast(eventId, request.titre(), request.contenu()));
    }

    public record BroadcastRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String titre,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 2000) String contenu) {
    }

    @PostMapping("/registrations/{id}/confirm")
    @PreAuthorize("hasAuthority('" + Permissions.REGISTRATION_MANAGE + "')")
    @Operation(summary = "Valider une inscription (organisateur)")
    public RegistrationResponse confirm(@PathVariable UUID id) {
        return registrationService.organiserConfirm(id);
    }

    @PostMapping("/registrations/{id}/reject")
    @PreAuthorize("hasAuthority('" + Permissions.REGISTRATION_MANAGE + "')")
    @Operation(summary = "Refuser une inscription (organisateur)")
    public RegistrationResponse reject(@PathVariable UUID id,
                                       @Valid @RequestBody RejectRequest request) {
        return registrationService.organiserReject(id, request.motif());
    }

    // --- documents demandés ---

    @GetMapping("/registrations/{id}/documents")
    @Operation(summary = "Documents d'une inscription")
    public List<DocumentService.DocumentView> documents(@PathVariable UUID id) {
        registrationService.get(id); // access check
        return documentService.list(OWNER, id);
    }

    @PostMapping(value = "/registrations/{id}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter un document à une inscription (PDF / image)")
    public DocumentService.DocumentView addDocument(@PathVariable UUID id,
                                                    @RequestParam("file") MultipartFile file,
                                                    @RequestParam(value = "type", required = false) String type,
                                                    @CurrentUser AuthenticatedUser user) {
        registrationService.get(id); // access check
        return documentService.attach(OWNER, id, file, type, user != null ? user.id() : null);
    }

    @DeleteMapping("/registrations/{id}/documents/{documentId}")
    @Operation(summary = "Supprimer un document d'inscription")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
        registrationService.get(id); // access check
        documentService.delete(documentId);
        return ResponseEntity.noContent().build();
    }
}
