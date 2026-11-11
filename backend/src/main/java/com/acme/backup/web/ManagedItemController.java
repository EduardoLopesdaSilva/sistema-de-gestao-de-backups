package com.acme.backup.web;

import com.acme.backup.service.ManagedStorageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/volumes/{volumeId}")
public class ManagedItemController {
    private final ManagedStorageService storage;
    public ManagedItemController(ManagedStorageService storage) { this.storage = storage; }

    @GetMapping("/items")
    public Page<ManagedStorageService.ItemView> list(@PathVariable String volumeId, @RequestParam(required = false) UUID parentId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return storage.list(volumeId, parentId, page, size);
    }

    @PostMapping("/folders") @ResponseStatus(HttpStatus.CREATED)
    public ManagedStorageService.ItemView createFolder(@PathVariable String volumeId, @Valid @RequestBody CreateFolderRequest request,
            Authentication authentication) {
        return storage.createFolder(volumeId, request.parentId(), request.name(), authentication.getName());
    }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<ManagedStorageService.ItemView> upload(@PathVariable String volumeId,
            @RequestParam(required = false) UUID parentId, @RequestParam("files") List<MultipartFile> files,
            Authentication authentication) {
        return storage.upload(volumeId, parentId, files, authentication.getName());
    }

    @GetMapping("/items/{itemId}/download")
    public ResponseEntity<Resource> download(@PathVariable String volumeId, @PathVariable UUID itemId, Authentication authentication) {
        ManagedStorageService.DownloadFile file = storage.download(volumeId, itemId, authentication.getName());
        String mediaType = file.item().getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.item().getContentType();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mediaType))
            .contentLength(file.item().getSizeBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.item().getDisplayName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(file.resource());
    }

    public record CreateFolderRequest(UUID parentId, @NotBlank @Size(max = 255) String name) {}
}
