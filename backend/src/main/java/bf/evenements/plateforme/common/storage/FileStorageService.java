package bf.evenements.plateforme.common.storage;

import bf.evenements.plateforme.common.config.AppProperties;
import bf.evenements.plateforme.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Local-disk file storage. The {@code app.storage.provider} switch keeps room
 * for an S3 / MinIO implementation later; files are served under {@code /files/**}.
 */
@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/webp");
    private static final long MAX_SIZE = 15L * 1024 * 1024;

    private final Path basePath;
    private final String baseUrl;

    public FileStorageService(AppProperties props) {
        this.basePath = Path.of(props.storage().local().basePath()).toAbsolutePath().normalize();
        this.baseUrl = props.storage().local().baseUrl().replaceAll("/$", "");
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de créer le dossier de stockage : " + basePath, e);
        }
    }

    public StoredFile store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "Le fichier est vide.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("FILE_TOO_LARGE", "Le fichier dépasse 15 Mo.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED.contains(contentType)) {
            throw new BusinessException("UNSUPPORTED_FILE_TYPE",
                    "Formats acceptés : PDF, PNG, JPEG, WEBP.");
        }
        String safeFolder = folder == null ? "misc" : folder.replaceAll("[^a-zA-Z0-9/_-]", "");
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String key = safeFolder + "/" + UUID.randomUUID() + (ext != null ? "." + ext : "");
        Path target = basePath.resolve(key).normalize();
        if (!target.startsWith(basePath)) {
            throw new BusinessException("INVALID_PATH", "Chemin de fichier invalide.");
        }
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Échec de l'enregistrement du fichier", e);
        }
        return new StoredFile(key, baseUrl + "/" + key, contentType, file.getSize(),
                file.getOriginalFilename());
    }

    /** Stores raw bytes (used by the demo-data seeder for bundled images). */
    public StoredFile storeBytes(byte[] content, String originalName, String contentType,
                                 String folder) {
        if (content == null || content.length == 0) {
            throw new BusinessException("EMPTY_FILE", "Le fichier est vide.");
        }
        if (content.length > MAX_SIZE) {
            throw new BusinessException("FILE_TOO_LARGE", "Le fichier dépasse 15 Mo.");
        }
        if (contentType == null || !ALLOWED.contains(contentType)) {
            throw new BusinessException("UNSUPPORTED_FILE_TYPE",
                    "Formats acceptés : PDF, PNG, JPEG, WEBP.");
        }
        String safeFolder = folder == null ? "misc" : folder.replaceAll("[^a-zA-Z0-9/_-]", "");
        String ext = StringUtils.getFilenameExtension(originalName);
        String key = safeFolder + "/" + UUID.randomUUID() + (ext != null ? "." + ext : "");
        Path target = basePath.resolve(key).normalize();
        if (!target.startsWith(basePath)) {
            throw new BusinessException("INVALID_PATH", "Chemin de fichier invalide.");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new IllegalStateException("Échec de l'enregistrement du fichier", e);
        }
        return new StoredFile(key, baseUrl + "/" + key, contentType, content.length, originalName);
    }

    public Path resolve(String key) {
        Path p = basePath.resolve(key).normalize();
        if (!p.startsWith(basePath)) {
            throw new BusinessException("INVALID_PATH", "Chemin de fichier invalide.");
        }
        return p;
    }

    public Path basePath() {
        return basePath;
    }

    /**
     * Reads the bytes of a file previously stored here, given the public URL
     * returned by {@link #store}/{@link #storeBytes}. Returns {@code null} if
     * the URL is absent, points elsewhere, or the file can no longer be read
     * (never throws — callers use this for best-effort, cosmetic embedding).
     */
    public byte[] readIfLocal(String url) {
        if (url == null || !url.startsWith(baseUrl + "/")) {
            return null;
        }
        try {
            Path p = resolve(url.substring(baseUrl.length() + 1));
            return Files.exists(p) ? Files.readAllBytes(p) : null;
        } catch (Exception e) {
            log.warn("Impossible de lire le fichier local pour {}", url, e);
            return null;
        }
    }
}
