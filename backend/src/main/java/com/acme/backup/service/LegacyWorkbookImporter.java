package com.acme.backup.service;

import com.acme.backup.domain.BackupVolume;
import com.acme.backup.domain.LegacyItem;
import com.acme.backup.domain.ImportRun;
import com.acme.backup.domain.BackupRecord;
import com.acme.backup.domain.BackupRecordImport;
import com.acme.backup.repository.BackupRecordRepository;
import com.acme.backup.repository.BackupRecordImportRepository;
import com.acme.backup.repository.BackupVolumeRepository;
import com.acme.backup.repository.ImportRunRepository;
import com.acme.backup.repository.LegacyItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Component
public class LegacyWorkbookImporter implements ApplicationRunner {
    private final ObjectMapper mapper;
    private final BackupVolumeRepository volumes;
    private final LegacyItemRepository items;
    private final ImportRunRepository imports;
    private final AuditService audit;
    private final BackupRecordRepository backupRecords;
    private final BackupRecordImportRepository recordImports;

    public LegacyWorkbookImporter(ObjectMapper mapper, BackupVolumeRepository volumes, LegacyItemRepository items,
            ImportRunRepository imports, AuditService audit, BackupRecordRepository backupRecords,
            BackupRecordImportRepository recordImports) {
        this.mapper = mapper;
        this.volumes = volumes;
        this.items = items;
        this.imports = imports;
        this.audit = audit;
        this.backupRecords = backupRecords;
        this.recordImports = recordImports;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        byte[] source;
        try (var stream = new ClassPathResource("import/legacy-workbook.json").getInputStream()) {
            source = stream.readAllBytes();
        }
        String checksum = sha256(source);
        JsonNode root = mapper.readTree(source);
        int volumeCount = 0;
        int itemCount = 0;
        boolean workbookAlreadyImported = imports.existsBySourceChecksum(checksum);
        if (!workbookAlreadyImported)
            for (JsonNode row : root.path("volumes")) {
                String id = text(row, "id");
                BackupVolume volume = volumes.findById(id).orElseGet(BackupVolume::new);
                volume.setId(id);
                volume.setName(text(row, "name"));
                volume.setCapacityGb(decimal(row, "capacityGb"));
                volume.setOccupiedGb(decimal(row, "occupiedGb"));
                volume.setReportedFreeGb(decimal(row, "freeGb"));
                volume.setNote(text(row, "note"));
                volume.setMeasurementSource("IMPORTADO: RESUMO");
                volumes.save(volume);
                volumeCount++;
            }
        if (!workbookAlreadyImported)
            for (JsonNode row : root.path("legacyItems")) {
                String id = text(row, "id");
                LegacyItem item = items.findById(id).orElseGet(LegacyItem::new);
                item.setId(id);
                item.setVolumeName(text(row, "volume"));
                item.setLabel(text(row, "owner"));
                item.setCreatedText(text(row, "created"));
                item.setTestStatus(text(row, "status"));
                item.setSizeText(text(row, "amount"));
                item.setUnit(text(row, "unit"));
                item.setSourceSheet("DADOS");
                item.setSourceRow(row.path("sourceRow").asInt());
                item.setKind("LEGACY_AGGREGATE");
                items.save(item);
                itemCount++;
            }
        if (!workbookAlreadyImported) {
            ImportRun run = new ImportRun();
            run.setId(UUID.randomUUID());
            run.setSourceChecksum(checksum);
            run.setSourceName(text(root, "workbook"));
            run.setOutcome("SUCCESS");
            run.setImportedVolumes(volumeCount);
            run.setImportedItems(itemCount);
            imports.save(run);
            audit.record("system", null, null, "IMPORT", "SUCCESS",
                    Map.of("source", run.getSourceName(), "sha256", checksum, "volumes", volumeCount, "items",
                            itemCount));
        }
        if (!recordImports.existsById(checksum)) {
            var volumeIds = volumes.findAllByOrderByNameAsc().stream()
                    .collect(java.util.stream.Collectors.toMap(BackupVolume::getName, BackupVolume::getId));
            int recordCount = 0;
            for (JsonNode row : root.path("legacyItems")) {
                String volumeId = volumeIds.get(text(row, "volume"));
                if (volumeId == null)
                    continue;
                int sourceRow = row.path("sourceRow").asInt();
                BackupRecord record = new BackupRecord();
                record.setId(UUID
                        .nameUUIDFromBytes(("DADOS:" + sourceRow).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                record.setVolumeId(volumeId);
                record.setUsuarioSetor(text(row, "owner"));
                if (record.getUsuarioSetor().isBlank())
                    record.setUsuarioSetor("Não informado");
                record.setCreatedText(text(row, "created"));
                record.setTestStatus(text(row, "status"));
                record.setSizeText(text(row, "amount"));
                record.setUnit(text(row, "unit"));
                record.setSourceSheet("DADOS");
                record.setSourceRow(sourceRow);
                record.setCreatedBy("importação legada");
                backupRecords.save(record);
                recordCount++;
            }
            BackupRecordImport marker = new BackupRecordImport();
            marker.setSourceChecksum(checksum);
            marker.setImportedRecords(recordCount);
            recordImports.save(marker);
            audit.record("system", null, null, "BACKUP_RECORD_IMPORT", "SUCCESS",
                    Map.of("source", text(root, "workbook"), "records", recordCount));
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível.", exception);
        }
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static BigDecimal decimal(JsonNode node, String key) {
        try {
            return new BigDecimal(text(node, key).replace(',', '.'));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}
