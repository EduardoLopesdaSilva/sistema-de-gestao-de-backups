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
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
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
        var volumeIds = volumes.findAllByOrderByNameAsc().stream()
                .collect(java.util.stream.Collectors.toMap(BackupVolume::getName, BackupVolume::getId));
        String expandedChecksum = sha256((checksum + ":ALL_WORKSHEETS_V1").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!recordImports.existsById(expandedChecksum)) {
            int imported = importDados(root, volumeIds);
            imported += importPlanilha1(root, volumeIds);
            imported += importConferir(root, volumeIds);
            BackupRecordImport marker = new BackupRecordImport();
            marker.setSourceChecksum(expandedChecksum);
            marker.setImportedRecords(imported);
            recordImports.save(marker);
            audit.record("system", null, null, "BACKUP_RECORD_IMPORT", "SUCCESS",
                    Map.of("source", text(root, "workbook"), "records", imported, "sheets", List.of("DADOS", "Planilha1", "CONFERIR")));
        }
    }

    private int importDados(JsonNode root, Map<String, String> volumeIds) {
        int count = 0;
        for (JsonNode row : root.path("legacyItems")) {
            int sourceRow = row.path("sourceRow").asInt();
            JsonNode raw = root.path("sheets").path("DADOS").path("rows").path(sourceRow - 1);
            String sourceVolume = raw.path(0).asText(text(row, "volume"));
            // The sheet uses BADBLOCK NÃO FUNCIONA in the volume column for records below BKP IMAGEM 12.
            if ("BADBLOCK NÃO FUNCIONA".equalsIgnoreCase(sourceVolume)) sourceVolume = "BKP IMAGEM 12";
            String volumeId = volumeIds.get(sourceVolume);
            if (volumeId == null) throw new IllegalStateException("Volume de DADOS não encontrado na planilha: " + sourceVolume + " (linha " + sourceRow + ")");
            BackupRecord record = backupRecords.findBySourceSheetAndSourceRow("DADOS", sourceRow).orElseGet(BackupRecord::new);
            boolean fresh = record.getId() == null;
            if (fresh) {
                record.setId(UUID.nameUUIDFromBytes(("DADOS:" + sourceRow).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                record.setUsuarioSetor(text(row, "owner"));
                if (record.getUsuarioSetor().isBlank()) record.setUsuarioSetor("Não informado");
                record.setCreatedText(text(row, "created"));
                record.setTestStatus(text(row, "status"));
                record.setSizeText(text(row, "amount"));
                record.setUnit(text(row, "unit"));
                record.setCreatedBy("importação legada");
                count++;
            }
            record.setVolumeId(volumeId);
            record.setSourceSheet("DADOS");
            record.setSourceRow(sourceRow);
            if (record.getSourceDetails() == null) record.setSourceDetails(sourceDetails(raw, null));
            backupRecords.save(record);
        }
        return count;
    }

    private int importPlanilha1(JsonNode root, Map<String, String> volumeIds) {
        int count = 0;
        String activeVolume = null;
        JsonNode activeSection = null;
        JsonNode rows = root.path("sheets").path("Planilha1").path("rows");
        for (int i = 0; i < rows.size(); i++) {
            JsonNode row = rows.get(i);
            String first = cell(row, 0).trim();
            if (volumeIds.containsKey(first)) { activeVolume = first; activeSection = row; continue; }
            if (!first.matches("(?i)Id-.+") ) continue;
            if (activeVolume == null) throw new IllegalStateException("Identificador sem volume em Planilha1, linha " + (i + 1));
            BackupRecord record = backupRecords.findBySourceSheetAndSourceRow("Planilha1", i + 1).orElseGet(BackupRecord::new);
            if (record.getId() != null) continue;
            record.setId(UUID.nameUUIDFromBytes(("Planilha1:" + (i + 1)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            record.setVolumeId(volumeIds.get(activeVolume));
            record.setUsuarioSetor(first);
            record.setSizeText(cell(row, 4));
            record.setUnit(record.getSizeText().isBlank() ? "" : embeddedUnit(record.getSizeText()));
            record.setSourceSheet("Planilha1"); record.setSourceRow(i + 1); record.setCreatedBy("importação legada");
            Map<String, Object> details = new HashMap<>(); details.put("volumeSecao", activeVolume); details.put("idInformativo", first);
            details.put("metadadosVolumeSecao", sourceDetails(activeSection, null).get("celulasOriginais"));
            record.setSourceDetails(sourceDetails(row, details));
            backupRecords.save(record); count++;
        }
        return count;
    }

    private int importConferir(JsonNode root, Map<String, String> volumeIds) {
        int count = 0;
        JsonNode rows = root.path("sheets").path("CONFERIR").path("rows");
        for (int i = 1; i < rows.size(); i++) {
            JsonNode row = rows.get(i);
            String target = cell(row, 2).replaceFirst("(?i)^VIROU\\s+", "").trim();
            if (target.isBlank()) continue;
            String volumeId = volumeIds.get(target);
            if (volumeId == null) throw new IllegalStateException("Volume de destino não encontrado em CONFERIR, linha " + (i + 1) + ": " + target);
            BackupRecord record = backupRecords.findBySourceSheetAndSourceRow("CONFERIR", i + 1).orElseGet(BackupRecord::new);
            if (record.getId() != null) continue;
            record.setId(UUID.nameUUIDFromBytes(("CONFERIR:" + (i + 1)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            record.setVolumeId(volumeId); record.setUsuarioSetor(cell(row, 0).trim());
            record.setSizeText(cell(row, 3)); record.setUnit(cell(row, 4));
            String dateOrNote = cell(row, 5);
            if (dateOrNote.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) record.setCreatedText(dateOrNote);
            record.setTestStatus(""); record.setSourceSheet("CONFERIR"); record.setSourceRow(i + 1); record.setCreatedBy("importação legada");
            Map<String, Object> details = new HashMap<>(); details.put("arquivoAntigo", cell(row, 1)); details.put("arquivoNovo", cell(row, 2)); details.put("dataOuObservacao", dateOrNote); details.put("volumeDestino", target);
            record.setSourceDetails(sourceDetails(row, details)); backupRecords.save(record); count++;
        }
        return count;
    }

    private Map<String, Object> sourceDetails(JsonNode row, Map<String, ?> extra) {
        Map<String, Object> details = new HashMap<>();
        List<String> cells = new ArrayList<>(); row.forEach(value -> cells.add(value.isNull() ? "" : value.asText()));
        details.put("celulasOriginais", cells);
        if (extra != null) details.putAll(extra);
        return details;
    }

    private static String cell(JsonNode row, int index) { JsonNode value = row.path(index); return value.isMissingNode() || value.isNull() ? "" : value.asText().trim(); }
    private static String embeddedUnit(String size) { return size.toUpperCase().matches(".*(?:GB|MB|KB)$") ? size.replaceAll("^[0-9,. ]+", "").trim() : "GB"; }

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
