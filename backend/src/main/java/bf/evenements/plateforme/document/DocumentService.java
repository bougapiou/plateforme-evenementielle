package bf.evenements.plateforme.document;

import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.storage.FileStorageService;
import bf.evenements.plateforme.common.storage.StoredFile;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository repository;
    private final FileStorageService storage;

    @Transactional
    public DocumentView attach(String ownerType, UUID ownerId, MultipartFile file,
                               String typeDocument, UUID uploaderId) {
        StoredFile stored = storage.store(file, ownerType.toLowerCase() + "/" + ownerId);
        Document doc = new Document();
        doc.setOwnerType(ownerType);
        doc.setOwnerId(ownerId);
        doc.setNom(stored.originalName() != null ? stored.originalName() : stored.key());
        doc.setTypeDocument(typeDocument);
        doc.setUrl(stored.url());
        doc.setMime(stored.contentType());
        doc.setTaille(stored.size());
        doc.setUploadedBy(uploaderId);
        return DocumentView.from(repository.save(doc));
    }

    @Transactional(readOnly = true)
    public List<DocumentView> list(String ownerType, UUID ownerId) {
        return repository.findByOwnerTypeAndOwnerIdOrderByCreatedAtAsc(ownerType, ownerId).stream()
                .map(DocumentView::from).toList();
    }

    @Transactional
    public void delete(UUID id) {
        Document doc = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Document", id));
        repository.delete(doc);
    }

    public record DocumentView(UUID id, String nom, String typeDocument, String url, String mime,
                               Long taille, Instant createdAt) {
        static DocumentView from(Document d) {
            return new DocumentView(d.getId(), d.getNom(), d.getTypeDocument(), d.getUrl(),
                    d.getMime(), d.getTaille(), d.getCreatedAt());
        }
    }
}
