package com.acme.backup.service;

import com.acme.backup.domain.BackupVolume;
import com.acme.backup.domain.ManagedItem;
import com.acme.backup.repository.BackupVolumeRepository;
import com.acme.backup.repository.ManagedItemRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Map;

@Service
public class ManagedStorageService {
    private final ManagedItemRepository items;
    private final BackupVolumeRepository volumes;
    private final AuditService audit;
    private final Path configuredRoot;
    private final long maxFileBytes;
    private final Set<String> allowedExtensions;

    public ManagedStorageService(ManagedItemRepository items, BackupVolumeRepository volumes, AuditService audit,
            @Value("${app.storage-root}") String root,
            @Value("${app.upload.max-file-bytes}") long maxFileBytes,
            @Value("${app.upload.allowed-extensions}") List<String> allowedExtensions) {
        this.items = items; this.volumes = volumes; this.audit = audit;
        this.configuredRoot = Path.of(root).toAbsolutePath().normalize(); this.maxFileBytes = maxFileBytes;
        this.allowedExtensions = allowedExtensions.stream().map(s -> s.toLowerCase(Locale.ROOT).replaceFirst("^\\.", "")).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (this.configuredRoot.getParent() == null || maxFileBytes < 1) throw new IllegalArgumentException("Configuração de armazenamento inválida.");
    }

    @Transactional
    public List<ItemView> upload(String volumeId, UUID parentId, List<MultipartFile> uploads, String actor) {
        requireVolume(volumeId); ManagedItem parent = requireParent(volumeId, parentId);
        if (uploads == null || uploads.isEmpty() || uploads.size() > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione de 1 a 50 arquivos.");
        List<Path> stored = new ArrayList<>(); List<Path> temporary = new ArrayList<>();
        try {
            Path root = storageRoot();
            long requestedBytes = uploads.stream().mapToLong(MultipartFile::getSize).sum();
            FileStore store = Files.getFileStore(root);
            if (requestedBytes > store.getUsableSpace()) throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE, "Espaço físico insuficiente no armazenamento.");
            List<ItemView> result = new ArrayList<>();
            for (MultipartFile upload : uploads) {
                String name = safeName(upload.getOriginalFilename());
                String extension = extension(name);
                if (!allowedExtensions.contains(extension)) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de arquivo não permitido.");
                if (upload.getSize() < 1 || upload.getSize() > maxFileBytes) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo vazio ou acima do limite configurado.");
                String mediaType = validateContent(upload, extension);
                if (items.existsByVolumeIdAndParentIdAndDisplayName(volumeId, parentId, name)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um item com esse nome nesta pasta.");
                String relativePath = parent == null ? name : parent.getRelativePath() + "/" + name;
                Path staging = root.resolve(".staging"); Files.createDirectories(staging); restrictPermissions(staging, true);
                UUID storageKey = UUID.randomUUID(); Path temp = staging.resolve(storageKey.toString() + ".part");
                Path destination = resolveStorageKey(root, storageKey);
                temporary.add(temp);
                upload.transferTo(temp);
                long actualSize = Files.size(temp);
                if (actualSize < 1 || actualSize > maxFileBytes) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo vazio ou acima do limite configurado.");
                if (Files.getFileStore(root).getUsableSpace() < actualSize) throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE, "Espaço físico insuficiente durante a gravação.");
                String checksum = sha256(temp);
                moveAtomically(temp, destination); temporary.remove(temp); stored.add(destination); restrictPermissions(destination, false);
                ManagedItem item = new ManagedItem(); item.setId(UUID.randomUUID()); item.setVolumeId(volumeId); item.setParentId(parentId);
                item.setRelativePath(relativePath); item.setDisplayName(name); item.setContentType(mediaType); item.setSizeBytes(actualSize);
                item.setSha256(checksum); item.setStorageKey(storageKey); item.setCreatedBy(actor); item.setItemKind("FILE");
                items.save(item);
                audit.record(actor, volumeId, item.getId(), "UPLOAD", "SUCCESS", Map.of("name", name, "sizeBytes", actualSize, "sha256", checksum));
                result.add(view(item));
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) stored.forEach(ManagedStorageService::deleteQuietly);
                    temporary.forEach(ManagedStorageService::deleteQuietly);
                }
            });
            return result;
        } catch (IOException | NoSuchAlgorithmException exception) {
            temporary.forEach(ManagedStorageService::deleteQuietly); stored.forEach(ManagedStorageService::deleteQuietly);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao armazenar o arquivo.");
        } catch (RuntimeException exception) {
            temporary.forEach(ManagedStorageService::deleteQuietly); stored.forEach(ManagedStorageService::deleteQuietly); throw exception;
        }
    }

    @Transactional
    public ItemView createFolder(String volumeId, UUID parentId, String requestedName, String actor) {
        requireVolume(volumeId); ManagedItem parent = requireParent(volumeId, parentId); String name = safeName(requestedName);
        if (items.existsByVolumeIdAndParentIdAndDisplayName(volumeId, parentId, name)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um item com esse nome nesta pasta.");
        ManagedItem folder = new ManagedItem(); folder.setId(UUID.randomUUID()); folder.setVolumeId(volumeId); folder.setParentId(parentId);
        folder.setRelativePath(parent == null ? name : parent.getRelativePath() + "/" + name); folder.setDisplayName(name);
        folder.setSizeBytes(0); folder.setStorageKey(UUID.randomUUID()); folder.setCreatedBy(actor); folder.setItemKind("FOLDER");
        items.save(folder); audit.record(actor, volumeId, folder.getId(), "CREATE_FOLDER", "SUCCESS", Map.of("name", name));
        return view(folder);
    }

    @Transactional
    public DownloadFile download(String volumeId, UUID itemId, String actor) {
        ManagedItem item = items.findById(itemId).filter(i -> i.getVolumeId().equals(volumeId) && "FILE".equals(i.getItemKind()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado."));
        Path file = resolveStorageKey(storageRoot(), item.getStorageKey());
        if (!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conteúdo não está disponível no armazenamento.");
        audit.record(actor, volumeId, item.getId(), "DOWNLOAD", "SUCCESS", Map.of("name", item.getDisplayName(), "sizeBytes", item.getSizeBytes()));
        return new DownloadFile(item, new FileSystemResource(file));
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ItemView> list(String volumeId, UUID parentId, int page, int size) {
        requireVolume(volumeId); requireParent(volumeId, parentId);
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginação inválida.");
        return items.findByVolumeIdAndParentId(volumeId, parentId, org.springframework.data.domain.PageRequest.of(page, size,
            org.springframework.data.domain.Sort.by("itemKind").ascending().and(org.springframework.data.domain.Sort.by("displayName").ascending()))).map(ManagedStorageService::view);
    }

    private BackupVolume requireVolume(String id) { return volumes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Volume não encontrado.")); }
    private ManagedItem requireParent(String volumeId, UUID parentId) {
        if (parentId == null) return null;
        ManagedItem parent = items.findById(parentId).filter(i -> i.getVolumeId().equals(volumeId) && "FOLDER".equals(i.getItemKind()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pasta não encontrada neste volume."));
        return parent;
    }
    private Path storageRoot() {
        try { Files.createDirectories(configuredRoot); Path root = configuredRoot.toRealPath(); restrictPermissions(root, true); return root; }
        catch (IOException exception) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Raiz de armazenamento indisponível."); }
    }
    private static Path resolveStorageKey(Path root, UUID key) {
        Path path = root.resolve(key.toString()).normalize();
        if (!path.startsWith(root)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Referência de armazenamento inválida.");
        return path;
    }
    static String safeName(String input) {
        if (input == null || input.isBlank() || input.length() > 255 || input.contains("/") || input.contains("\\") || input.equals(".") || input.equals("..") || input.codePoints().anyMatch(Character::isISOControl))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome de arquivo ou pasta inválido.");
        return input;
    }
    private static String extension(String name) { int dot = name.lastIndexOf('.'); return dot < 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT); }
    static String validateContent(MultipartFile file, String extension) throws IOException {
        byte[] header; try (InputStream input = file.getInputStream()) { header = input.readNBytes(8192); }
        boolean zip = starts(header, 0x50, 0x4b, 0x03, 0x04);
        boolean png = starts(header, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
        boolean jpeg = starts(header, 0xff, 0xd8, 0xff);
        boolean pdf = starts(header, 0x25, 0x50, 0x44, 0x46, 0x2d);
        boolean mp4 = header.length >= 8 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p';
        boolean mp3 = starts(header, 0x49, 0x44, 0x33) || starts(header, 0xff, 0xfb);
        boolean sevenZip = starts(header, 0x37, 0x7a, 0xbc, 0xaf, 0x27, 0x1c);
        String mime = switch (extension) {
            case "pdf" -> pdf ? "application/pdf" : null;
            case "png" -> png ? "image/png" : null;
            case "jpg", "jpeg" -> jpeg ? "image/jpeg" : null;
            case "zip" -> zip ? "application/zip" : null;
            case "docx" -> zip ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document" : null;
            case "xlsx" -> zip ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : null;
            case "pptx" -> zip ? "application/vnd.openxmlformats-officedocument.presentationml.presentation" : null;
            case "7z" -> sevenZip ? "application/x-7z-compressed" : null;
            case "mp4" -> mp4 ? "video/mp4" : null;
            case "mp3" -> mp3 ? "audio/mpeg" : null;
            case "txt", "csv", "json" -> validUtf8Text(header) ? switch (extension) { case "csv" -> "text/csv"; case "json" -> "application/json"; default -> "text/plain"; } : null;
            default -> null;
        };
        if (mime == null) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "O conteúdo não corresponde a uma extensão permitida.");
        return mime;
    }
    private static boolean starts(byte[] data, int... values) { if (data.length < values.length) return false; for (int i=0;i<values.length;i++) if ((data[i] & 0xff) != values[i]) return false; return true; }
    private static boolean validUtf8Text(byte[] bytes) {
        if (bytes.length == 0) return false;
        for (byte value : bytes) if (value == 0) return false;
        try { StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)); return true; }
        catch (java.nio.charset.CharacterCodingException exception) { return false; }
    }
    private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) { byte[] buffer = new byte[64 * 1024]; int read; while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read); }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void moveAtomically(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException exception) { Files.move(source, target); }
    }
    private static void restrictPermissions(Path path, boolean directory) {
        try { Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------")); }
        catch (UnsupportedOperationException | IOException ignored) { }
    }
    private static void deleteQuietly(Path path) { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }
    private static ItemView view(ManagedItem item) { return new ItemView(item.getId(), item.getVolumeId(), item.getParentId(), item.getDisplayName(), item.getRelativePath(), item.getItemKind(), item.getContentType(), item.getSizeBytes(), item.getSha256(), item.getCreatedAt()); }
    public record ItemView(UUID id, String volumeId, UUID parentId, String name, String relativePath, String kind, String contentType, long sizeBytes, String sha256, java.time.Instant createdAt) {}
    public record DownloadFile(ManagedItem item, Resource resource) {}
}
