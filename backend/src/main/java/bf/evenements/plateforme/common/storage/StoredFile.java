package bf.evenements.plateforme.common.storage;

public record StoredFile(String key, String url, String contentType, long size, String originalName) {
}
