package com.acme.backup.web;

import com.acme.backup.service.EmailAccessService;
import com.acme.backup.service.EmailAccessSpreadsheetImporter;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/email-access")
public class EmailAccessController {
    private final EmailAccessService records;
    private final EmailAccessSpreadsheetImporter importer;

    public EmailAccessController(EmailAccessService records, EmailAccessSpreadsheetImporter importer) {
        this.records = records;
        this.importer = importer;
    }

    @GetMapping
    public List<EmailAccessService.View> list(@RequestParam String category) { return records.list(category); }

    @PostMapping("/{category}")
    @ResponseStatus(HttpStatus.CREATED)
    public EmailAccessService.View create(@PathVariable String category, @RequestBody Map<String, ?> fields) {
        return records.create(category, fields);
    }

    @PutMapping("/{id}")
    public EmailAccessService.View update(@PathVariable UUID id, @RequestBody Map<String, ?> fields) {
        return records.update(id, fields);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { records.delete(id); }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @Transactional
    public EmailAccessSpreadsheetImporter.ImportResult importSheets(@RequestPart("files") List<MultipartFile> files) {
        return importer.importFiles(files);
    }
}
