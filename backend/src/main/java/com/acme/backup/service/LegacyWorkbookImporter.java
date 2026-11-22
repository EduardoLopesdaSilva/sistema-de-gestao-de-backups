package com.acme.backup.service;

import com.acme.backup.domain.BackupRecord;
import com.acme.backup.domain.BackupRecordImport;
import com.acme.backup.domain.BackupVolume;
import com.acme.backup.repository.BackupRecordImportRepository;
import com.acme.backup.repository.BackupRecordRepository;
import com.acme.backup.repository.BackupVolumeRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LegacyWorkbookImporter implements ApplicationRunner {
    private static final Pattern IMAGE_VOLUME = Pattern.compile("(?i)^BKP IMAGEM (\\d{2})$");
    private static final Pattern CAPACITY = Pattern.compile("(?i)HD\\s*([0-9.,]+)\\s*GB");
    private static final Pattern OCCUPIED = Pattern.compile("(?i)Espaço\\s+Total\\s+Ocupado:?\\s*([0-9.,]+)");
    private static final Pattern FREE = Pattern.compile("(?i)Espaço\\s+Atual:?\\s*([0-9.,]+)");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("d/M/uu"),
            DateTimeFormatter.ofPattern("M/d/uuuu"), DateTimeFormatter.ofPattern("M/d/uu"),
            DateTimeFormatter.ISO_LOCAL_DATE);

    private final BackupVolumeRepository volumes;
    private final BackupRecordRepository records;
    private final BackupRecordImportRepository imports;
    private final AuditService audit;

    public LegacyWorkbookImporter(BackupVolumeRepository volumes, BackupRecordRepository records,
            BackupRecordImportRepository imports, AuditService audit) {
        this.volumes = volumes;
        this.records = records;
        this.imports = imports;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        byte[] source;
        try (var stream = new ClassPathResource("import/Controle-Dos-Backups-Copia.xlsx").getInputStream()) {
            source = stream.readAllBytes();
        }
        String checksum = sha256(source);
        if (imports.existsById(checksum)) return;

        int volumeCount = 0;
        int recordCount = 0;
        Map<String, String> volumeIds = new HashMap<>();
        DataFormatter formatter = new DataFormatter(new Locale("pt", "BR"));
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(source))) {
            Sheet dados = workbook.getSheet("DADOS");
            if (dados == null) throw new IllegalStateException("A aba DADOS não existe na planilha de importação.");
            for (Row row : dados) {
                String volumeName = text(row, 0, formatter);
                String user = text(row, 1, formatter).trim();
                String dateOrHeader = text(row, 2, formatter);

                Matcher image = IMAGE_VOLUME.matcher(volumeName);
                Matcher capacity = CAPACITY.matcher(user);
                if (image.matches() && capacity.find() && dateOrHeader.toLowerCase(Locale.ROOT).contains("espaço total ocupado")) {
                    int number = Integer.parseInt(image.group(1));
                    if (number > 13) continue;
                    String id = "vol-" + number;
                    BackupVolume volume = volumes.findById(id).orElseGet(BackupVolume::new);
                    volume.setId(id);
                    volume.setName(volumeName);
                    volume.setCapacityGb(decimal(capacity.group(1)));
                    volume.setOccupiedGb(capture(OCCUPIED, dateOrHeader));
                    volume.setReportedFreeGb(capture(FREE, text(row, 3, formatter)));
                    volume.setNote(null);
                    volume.setMeasurementSource("IMPORTADO: DADOS");
                    volumes.save(volume);
                    volumeIds.put(volumeName, id);
                    volumeCount++;
                    continue;
                }

                if (volumeName.equalsIgnoreCase("BADBLOCK NÃO FUNCIONA")) {
                    String id = "vol-badblock";
                    BackupVolume volume = volumes.findById(id).orElseGet(BackupVolume::new);
                    volume.setId(id);
                    volume.setName("BADBLOCK NÃO FUNCIONA");
                    volume.setCapacityGb(null);
                    volume.setOccupiedGb(null);
                    volume.setReportedFreeGb(null);
                    volume.setNote("Unidade não funciona");
                    volume.setMeasurementSource("IMPORTADO: DADOS");
                    volumes.save(volume);
                    volumeIds.put(volume.getName(), id);
                    volumeCount++;
                }

                if (user.isBlank() || volumeName.isBlank()) continue;
                String volumeId = volumeIds.get(volumeName);
                if (volumeId == null) continue;

                int sourceRow = row.getRowNum() + 1;
                BackupRecord record = records.findBySourceSheetAndSourceRow("DADOS", sourceRow)
                        .orElseGet(BackupRecord::new);
                boolean fresh = record.getId() == null;
                if (fresh) {
                    record.setId(UUID.nameUUIDFromBytes(("DADOS:" + sourceRow).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    record.setCreatedBy("importação legada");
                }
                record.setVolumeId(volumeId);
                record.setUsuarioSetor(user);
                record.setCreatedDate(date(row.getCell(2), formatter));
                record.setTestStatus(status(text(row, 3, formatter)));
                record.setSizeValue(decimalValue(text(row, 4, formatter)));
                String unit = text(row, 5, formatter).trim().toUpperCase(Locale.ROOT);
                record.setUnit(List.of("KB", "MB", "GB", "TB").contains(unit) ? unit : "GB");
                record.setBlocked(isRed(row.getCell(1), workbook));
                record.setSourceSheet("DADOS");
                record.setSourceRow(sourceRow);
                record.setSourceDetails(sourceDetails(row, formatter));
                records.save(record);
                if (fresh) recordCount++;
            }
        }

        BackupRecordImport marker = new BackupRecordImport();
        marker.setSourceChecksum(checksum);
        marker.setImportedRecords(recordCount);
        imports.save(marker);
        audit.record("system", null, null, "BACKUP_RECORD_IMPORT", "SUCCESS",
                Map.of("source", "Controle-Dos-Backups-Copia.xlsx", "sheet", "DADOS",
                        "records", recordCount, "volumes", volumeCount));
    }

    private static boolean isRed(Cell cell, Workbook workbook) {
        if (cell == null) return false;
        Font font = workbook.getFontAt(cell.getCellStyle().getFontIndexAsInt());
        if (!(font instanceof XSSFFont xssfFont) || xssfFont.getXSSFColor() == null) return false;
        byte[] rgb = xssfFont.getXSSFColor().getRGB();
        if (rgb == null || rgb.length < 3) return false;
        int red = rgb[rgb.length - 3] & 0xff;
        int green = rgb[rgb.length - 2] & 0xff;
        int blue = rgb[rgb.length - 1] & 0xff;
        return red >= 128 && red > green * 1.35 && red > blue * 1.35;
    }

    private static Map<String, Object> sourceDetails(Row row, DataFormatter formatter) {
        Map<String, Object> values = new HashMap<>();
        for (int column = 0; column < 6; column++) values.put("coluna" + (column + 1), text(row, column, formatter));
        return values;
    }

    private static LocalDate date(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))
            return cell.getDateCellValue().toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        String value = formatter.formatCellValue(cell).trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try { return LocalDate.parse(value, format); } catch (DateTimeParseException ignored) { }
        }
        return null;
    }

    private static String status(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.contains("NÃO FEITO") || normalized.contains("PENDENTE")) return "PENDENTE";
        if (normalized.contains("ERRO")) return "COM ERRO";
        if (normalized.contains("CONFERIDO") || normalized.equals("FEITO") || normalized.contains("COMPLETO")) return "CONFERIDO";
        return "PENDENTE";
    }

    private static BigDecimal decimalValue(String value) {
        String numeric = value.trim().replaceAll("(?i)\\s*(GB|MB|KB|TB)$", "").replace(',', '.');
        try { return new BigDecimal(numeric); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static BigDecimal capture(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? decimal(matcher.group(1)) : null;
    }

    private static BigDecimal decimal(String value) {
        try { return new BigDecimal(value.replace(',', '.')); } catch (NumberFormatException ignored) { return BigDecimal.ZERO; }
    }

    private static String text(Row row, int column, DataFormatter formatter) {
        if (row == null || row.getCell(column) == null) return "";
        return formatter.formatCellValue(row.getCell(column)).trim();
    }

    private static String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 indisponível.", ex); }
    }
}
