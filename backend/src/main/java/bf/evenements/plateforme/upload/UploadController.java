package bf.evenements.plateforme.upload;

import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.storage.FileStorageService;
import bf.evenements.plateforme.common.storage.StoredFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Generic image upload used by the event editor (cover / logo / activity visuals)
 * and by structure logos. Returns a public URL served under {@code /files/**}.
 */
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
@Tag(name = "Fichiers")
public class UploadController {

    private static final Set<String> IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp");

    private final FileStorageService storage;

    @PostMapping(value = "/image", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Téléverser une image (PNG / JPEG / WEBP, 15 Mo max)")
    public UploadResponse image(@RequestParam("file") MultipartFile file,
                                @RequestParam(value = "dossier", required = false) String dossier) {
        if (file.getContentType() == null || !IMAGE_TYPES.contains(file.getContentType())) {
            throw new BusinessException("UNSUPPORTED_FILE_TYPE",
                    "Formats d'image acceptés : PNG, JPEG, WEBP.");
        }
        String folder = switch (dossier == null ? "" : dossier) {
            case "evenements", "activites", "structures", "organisateurs" -> dossier;
            default -> "images";
        };
        StoredFile stored = storage.store(file, folder);
        return new UploadResponse(stored.url(), stored.key(), stored.contentType(), stored.size());
    }

    public record UploadResponse(String url, String key, String contentType, long size) {
    }
}
