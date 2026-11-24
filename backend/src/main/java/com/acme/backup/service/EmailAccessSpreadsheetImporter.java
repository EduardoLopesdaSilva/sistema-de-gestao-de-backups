package com.acme.backup.service;

import com.acme.backup.domain.EmailAccessImport;
import com.acme.backup.repository.EmailAccessImportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class EmailAccessSpreadsheetImporter {
    private static final Set<String> EXPECTED_FILES = Set.of(
            "emailscpanelxlsx", "emailsoffice365xlsx", "emailsskypexlsx", "emailsverificarxlsx",
            "encaminhamentosxlsx", "gerentesemailxlsx", "gmailoutlookeasexlsx", "lavanderiaxlsx");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]");

    private final EmailAccessService service;
    private final EmailAccessImportRepository imports;

    public EmailAccessSpreadsheetImporter(EmailAccessService service, EmailAccessImportRepository imports) {
        this.service = service;
        this.imports = imports;
    }

    @Transactional
    public ImportResult importFiles(List<MultipartFile> files) {
        Map<String, MultipartFile> byName = new HashMap<>();
        for (MultipartFile file : files) {
            String normalized = fileKey(file.getOriginalFilename());
            if (!EXPECTED_FILES.contains(normalized))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Arquivo XLSX não reconhecido: " + safeName(file.getOriginalFilename()));
            if (byName.put(normalized, file) != null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Foi enviado mais de um arquivo da mesma categoria.");
        }
        if (!byName.keySet().containsAll(EXPECTED_FILES))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selecione os oito arquivos XLSX do módulo antes de importar.");

        List<String> order = List.of("emailscpanelxlsx", "emailsoffice365xlsx", "emailsskypexlsx",
                "emailsverificarxlsx", "encaminhamentosxlsx", "gerentesemailxlsx",
                "gmailoutlookeasexlsx", "lavanderiaxlsx");
        Set<String> redirectKeys = service.existingRedirectKeys();
        List<FileResult> results = new ArrayList<>();
        int total = 0;
        for (String key : order) {
            MultipartFile file = byName.get(key);
            try {
                String filename = safeName(file.getOriginalFilename());
                byte[] bytes = file.getBytes();
                String checksum = sha256(bytes);
                if (imports.existsById(checksum)) {
                    results.add(new FileResult(filename, 0, true));
                    continue;
                }
                int imported = parseWorkbook(filename, bytes, redirectKeys);
                EmailAccessImport marker = new EmailAccessImport();
                marker.setSourceChecksum(checksum);
                marker.setSourceName(filename);
                marker.setImportedRecords(imported);
                imports.save(marker);
                service.recordImport(checksum, filename, imported);
                results.add(new FileResult(filename, imported, false));
                total += imported;
            } catch (Exception ex) {
                if (ex instanceof ResponseStatusException responseStatusException)
                    throw responseStatusException;
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Não foi possível importar " + safeName(file.getOriginalFilename())
                                + ". Confira o formato e tente novamente.");
            }
        }
        return new ImportResult(total, results);
    }

    private int parseWorkbook(String filename, byte[] bytes, Set<String> redirectKeys) throws Exception {
        String key = fileKey(filename);
        int count = 0;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter(new Locale("pt", "BR"));
            switch (key) {
                case "emailscpanelxlsx" -> {
                    Sheet active = workbook.getSheet("E-mail");
                    Sheet inactive = workbook.getSheet("Inativos");
                    if (active == null || inactive == null)
                        throw new IllegalArgumentException();
                    count += importSheet(active, filename + "#E-mail", "EMAIL_MAIN", formatter,
                            Map.of("email", aliases("email", "emails"), "password", aliases("senha", "password"),
                                    "systems", aliases("integracaosistemas"), "active", aliases("ativo")),
                            "email", "Sim", redirectKeys);
                    count += importSheet(inactive, filename + "#Inativos", "EMAIL_MAIN", formatter,
                            Map.of("email", aliases("email", "emails"), "password", aliases("senha", "password"),
                                    "systems", aliases("integracaosistemas"), "active", aliases("ativo")),
                            "email", "Não", redirectKeys);
                }
                case "emailsoffice365xlsx" ->
                    count += importSheet(requireSheet(workbook, "Planilha1"), filename, "OFFICE365", formatter,
                            Map.of("email", aliases("email"), "password", aliases("senha", "password"),
                                    "sector", aliases("setor"), "previousUser", aliases("usuarioanterior")),
                            "email", "", redirectKeys);
                case "emailsskypexlsx" -> count += importSheet(requireSheet(workbook, "Planilha1"), filename, "SKYPE",
                        formatter,
                        Map.of("user", aliases("usuario"), "skypeEmail", aliases("emailskype"),
                                "password", aliases("senha", "password"), "recoveryEmail", aliases("emailrecuperacao")),
                        "user", "", redirectKeys);
                case "emailsverificarxlsx" ->
                    count += importSheet(requireSheet(workbook, "Planilha1"), filename, "REDIRECTS", formatter,
                            Map.of("sourceEmail", aliases("de"), "targetEmail", aliases("para")), "sourceEmail", "",
                            redirectKeys);
                case "encaminhamentosxlsx" -> count += importForwardingSheet(requireSheet(workbook, "Planilha1"),
                        filename, formatter, redirectKeys);
                case "gerentesemailxlsx" ->
                    count += importSheet(requireSheet(workbook, "Planilha1"), filename, "MANAGERS", formatter,
                            Map.of("fullName", aliases("nomecompleto"), "managerSector", aliases("gerentedosetor"),
                                    "email", aliases("email")),
                            "email", "", redirectKeys);
                case "gmailoutlookeasexlsx" -> {
                    count += importSheet(requireSheet(workbook, "Planilha1"), filename + "#Planilha1",
                            "EXTERNAL_ACCOUNTS", formatter,
                            Map.of("description", aliases("descricao"), "email", aliases("email"),
                                    "password", aliases("senha"), "samsungPassword", aliases("senhasamsung")),
                            "email", "", redirectKeys);
                    Sheet devices = workbook.getSheet("Celulares-Ease");
                    if (devices == null)
                        throw new IllegalArgumentException();
                    count += importSheet(devices, filename + "#Celulares-Ease", "DEVICES", formatter,
                            Map.of("deviceSector", aliases("celularsetor"), "emailUsed", aliases("emailusado"),
                                    "gmailPassword", aliases("senhagmail"), "samsungPassword", aliases("senhasamsung"),
                                    "pin", aliases("pin"), "user", aliases("usuario")),
                            "deviceSector", "", redirectKeys);
                }
                case "lavanderiaxlsx" ->
                    count += importSheet(requireSheet(workbook, "Plan1"), filename, "LAUNDRY", formatter,
                            Map.of("user", aliases("usuario"), "cpanel", aliases("cpanel"), "office365",
                                    aliases("office365"),
                                    "cpanelPassword", aliases("senhacpanel"), "office365Password", aliases("senha365"),
                                    "sector", aliases("setor")),
                            "user", "", redirectKeys);
                default -> throw new IllegalArgumentException();
            }
        }
        return count;
    }

    private int importForwardingSheet(Sheet sheet, String filename, DataFormatter formatter, Set<String> seen) {
        Map<String, String[]> columns = Map.of("sourceEmail", aliases("encaminhamentos"), "targetEmail",
                aliases("emailprincipal"));
        return importSheet(sheet, filename, "REDIRECTS", formatter, columns, "sourceEmail", "", seen);
    }

    private int importSheet(Sheet sheet, String sourceName, String category, DataFormatter formatter,
            Map<String, String[]> fields, String required, String fallbackActive, Set<String> redirectKeys) {
        Header header = findHeader(sheet, fields, formatter);
        int count = 0;
        for (int index = header.rowIndex + 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null || rowIsEmpty(row, fields.values(), header.columns, formatter))
                continue;
            Map<String, String> values = new LinkedHashMap<>();
            for (String field : fields.keySet())
                values.put(field, cell(row, header.columns.get(field), formatter));
            if (values.values().stream().allMatch(String::isBlank) || values.getOrDefault(required, "").isBlank())
                continue;
            if (category.equals("EMAIL_MAIN")) {
                if (values.get("active").isBlank())
                    values.put("active", fallbackActive);
                values.put("specialTag", rowTag(row));
            }
            if (category.equals("REDIRECTS")) {
                String redirectKey = EmailAccessService.staticRedirectKey(values.get("sourceEmail"),
                        values.get("targetEmail"));
                if (values.get("targetEmail").isBlank() || !redirectKeys.add(redirectKey))
                    continue;
            }
            service.importRow(category, values, sourceName, index + 1);
            count++;
        }
        return count;
    }

    private Header findHeader(Sheet sheet, Map<String, String[]> fields, DataFormatter formatter) {
        int limit = Math.min(sheet.getLastRowNum(), 18);
        for (int rowIndex = 0; rowIndex <= limit; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null)
                continue;
            Map<String, Integer> columns = new HashMap<>();
            for (Cell cell : row) {
                String name = normalize(formatter.formatCellValue(cell));
                for (Map.Entry<String, String[]> entry : fields.entrySet()) {
                    if (Arrays.stream(entry.getValue()).anyMatch(alias -> name.contains(alias)))
                        columns.putIfAbsent(entry.getKey(), cell.getColumnIndex());
                }
            }
            if (columns.keySet().containsAll(fields.keySet()))
                return new Header(rowIndex, columns);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Não foi possível reconhecer os cabeçalhos da planilha " + sheet.getSheetName() + ".");
    }

    private static String rowTag(Row row) {
        for (int column = 0; column < 4; column++) {
            Cell cell = row.getCell(column);
            if (cell == null || !(cell.getCellStyle() instanceof XSSFCellStyle style))
                continue;
            XSSFColor color = style.getFillForegroundXColor();
            if (color == null || color.getRGB() == null)
                continue;
            byte[] rgb = color.getRGB();
            if (rgb.length < 3)
                continue;
            String hex = String.format("%02X%02X%02X", rgb[rgb.length - 3] & 0xff,
                    rgb[rgb.length - 2] & 0xff, rgb[rgb.length - 1] & 0xff);
            switch (hex) {
                case "FF0000" -> {
                    return "Inativar";
                }
                case "FFFF00" -> {
                    return "Incluir";
                }
                case "7030A0" -> {
                    return "Não Mexer";
                }
                case "00B0F0" -> {
                    return "Filial";
                }
                default -> {
                }
            }
        }
        return "";
    }

    private static boolean rowIsEmpty(Row row, Collection<String[]> fields, Map<String, Integer> columns,
            DataFormatter formatter) {
        for (Integer column : columns.values())
            if (!cell(row, column, formatter).isBlank())
                return false;
        return true;
    }

    private static String cell(Row row, Integer column, DataFormatter formatter) {
        if (row == null || column == null || row.getCell(column) == null)
            return "";
        return formatter.formatCellValue(row.getCell(column)).strip();
    }

    private static Sheet requireSheet(Workbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A aba " + name + " não foi encontrada.");
        return sheet;
    }

    private static String[] aliases(String... values) {
        return Arrays.stream(values).map(EmailAccessSpreadsheetImporter::normalize).toArray(String[]::new);
    }

    private static String normalize(String value) {
        String noAccent = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}+",
                "");
        return NON_ALNUM.matcher(noAccent.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private static String fileKey(String filename) {
        return normalize(safeName(filename));
    }

    private static String safeName(String filename) {
        if (filename == null)
            return "arquivo.xlsx";
        String name = filename.replace('\\', '/');
        return name.substring(name.lastIndexOf('/') + 1).strip();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record Header(int rowIndex, Map<String, Integer> columns) {
    }

    public record FileResult(String file, int imported, boolean alreadyImported) {
    }

    public record ImportResult(int imported, List<FileResult> files) {
    }
}
