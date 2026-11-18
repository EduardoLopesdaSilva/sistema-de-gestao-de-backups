package com.acme.backup.web;

import com.acme.backup.domain.BackupRecord;
import com.acme.backup.repository.BackupRecordRepository;
import com.acme.backup.repository.BackupVolumeRepository;
import com.acme.backup.service.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/backups")
public class BackupRecordController {
    private static final String ACTOR = "acesso-direto";
    private final BackupRecordRepository records;
    private final BackupVolumeRepository volumes;
    private final AuditService audit;

    public BackupRecordController(BackupRecordRepository records, BackupVolumeRepository volumes, AuditService audit) {
        this.records = records;
        this.volumes = volumes;
        this.audit = audit;
    }

    @GetMapping
    public List<BackupRecord> list(@RequestParam(required = false) String volume) {
        return volume == null || volume.isBlank() ? records.findAllByOrderByCreatedAtDesc()
                : records.findByVolumeIdOrderByCreatedAtDesc(volume);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public BackupRecord create(@Valid @RequestBody BackupRequest request) {
        requireVolume(request.volumeId());
        BackupRecord record = apply(new BackupRecord(), request);
        record.setId(UUID.randomUUID());
        record.setCreatedBy(ACTOR);
        records.save(record);
        audit.record(ACTOR, record.getVolumeId(), record.getId(), "BACKUP_CREATE", "SUCCESS", details(record));
        return record;
    }

    @PutMapping("/{id}")
    @Transactional
    public BackupRecord update(@PathVariable UUID id, @Valid @RequestBody BackupRequest request) {
        requireVolume(request.volumeId());
        BackupRecord record = require(id);
        apply(record, request);
        records.save(record);
        audit.record(ACTOR, record.getVolumeId(), id, "BACKUP_UPDATE", "SUCCESS", details(record));
        return record;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable UUID id) {
        BackupRecord record = require(id);
        Map<String, Object> snapshot = details(record);
        String volume = record.getVolumeId();
        records.delete(record);
        audit.record(ACTOR, volume, id, "BACKUP_DELETE", "SUCCESS", snapshot);
    }

    private BackupRecord require(UUID id) {
        return records.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro de backup não encontrado."));
    }

    private void requireVolume(String id) {
        if (!volumes.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Volume não encontrado.");
    }

    private static BackupRecord apply(BackupRecord r, BackupRequest q) {
        r.setVolumeId(q.volumeId());
        r.setUsuarioSetor(q.usuarioSetor().trim());
        r.setCreatedText(q.createdText());
        r.setTestStatus(q.testStatus());
        r.setSizeText(q.sizeText());
        r.setUnit(q.unit());
        return r;
    }

    private static Map<String, Object> details(BackupRecord r) {
        return Map.of("usuario_setor", r.getUsuarioSetor(), "volumeId", r.getVolumeId(), "data",
                r.getCreatedText() == null ? "" : r.getCreatedText(), "status",
                r.getTestStatus() == null ? "" : r.getTestStatus(), "tamanho",
                r.getSizeText() == null ? "" : r.getSizeText(), "unidade", r.getUnit() == null ? "" : r.getUnit());
    }

    public record BackupRequest(@NotBlank @Size(max = 80) String volumeId,
            @NotBlank @Size(max = 255) String usuarioSetor,
            @Size(max = 120) String createdText, @Size(max = 2000) String testStatus, @Size(max = 100) String sizeText,
            @Size(max = 32) String unit) {
    }
}
