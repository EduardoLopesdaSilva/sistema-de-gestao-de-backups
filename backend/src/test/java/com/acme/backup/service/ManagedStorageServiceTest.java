package com.acme.backup.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManagedStorageServiceTest {
    @Test void acceptsSafeRelativeNamesAndRejectsTraversal() {
        assertEquals("backup 2026.pdf", ManagedStorageService.safeName("backup 2026.pdf"));
        assertThrows(ResponseStatusException.class, () -> ManagedStorageService.safeName("../secret.txt"));
        assertThrows(ResponseStatusException.class, () -> ManagedStorageService.safeName("folder\\secret.txt"));
    }
    @Test void matchesAllowedExtensionToContentSignature() throws Exception {
        var pdf = new MockMultipartFile("file", "report.pdf", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        assertEquals("application/pdf", ManagedStorageService.validateContent(pdf, "pdf"));
        var spoofed = new MockMultipartFile("file", "report.pdf", "application/pdf", "plain text".getBytes(StandardCharsets.UTF_8));
        assertThrows(ResponseStatusException.class, () -> ManagedStorageService.validateContent(spoofed, "pdf"));
    }
    @Test void acceptsUtf8TextWithoutNulBytes() throws Exception {
        var text = new MockMultipartFile("file", "notes.txt", "text/plain", "backup notes".getBytes(StandardCharsets.UTF_8));
        assertEquals("text/plain", ManagedStorageService.validateContent(text, "txt"));
        var binaryText = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[]{65, 0, 66});
        assertThrows(ResponseStatusException.class, () -> ManagedStorageService.validateContent(binaryText, "txt"));
    }
}
