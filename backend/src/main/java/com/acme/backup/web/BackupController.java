package com.acme.backup.web;

import com.acme.backup.domain.BackupVolume;
import com.acme.backup.repository.BackupVolumeRepository;
import com.acme.backup.repository.BackupRecordRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BackupController {
    private final BackupVolumeRepository volumes;
    private final BackupRecordRepository records;

    public BackupController(BackupVolumeRepository volumes, BackupRecordRepository records) {
        this.volumes = volumes;
        this.records = records;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/volumes")
    public List<BackupVolume> volumes() {
        return volumes.findAllByOrderByNameAsc();
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String volume, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginação inválida.");
        var results = records.search(q.strip(), volume.strip(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return Map.of("records", results.getContent(), "page", page, "size", size, "total", results.getTotalElements());
    }

    @GetMapping("/reports")
    public Map<String, Object> reports() {
        List<BackupVolume> all = volumes.findAllByOrderByNameAsc();
        BigDecimal occupied = all.stream().map(BackupVolume::getOccupiedGb).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal capacity = all.stream().map(BackupVolume::getCapacityGb).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long backupRecordCount = all.stream().mapToLong(volume -> records.countByVolumeId(volume.getId())).sum();
        var recordCounts = all.stream()
                .map(volume -> Map.of("volumeId", volume.getId(), "count", records.countByVolumeId(volume.getId())))
                .toList();
        return Map.of("volumes", all, "backupRecordsByVolume", recordCounts, "backupRecordCount", backupRecordCount,
                "volumeCount", all.size(), "occupiedGb", occupied, "capacityGb", capacity,
                "source", "RESUMO", "measurementType", "importado");
    }
}
