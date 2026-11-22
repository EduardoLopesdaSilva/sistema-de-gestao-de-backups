package com.acme.backup.service;

import com.acme.backup.domain.EmailAccessRecord;
import com.acme.backup.repository.EmailAccessRecordRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
public class EmailAccessService {
    private static final Map<String, Set<String>> FIELDS = Map.of(
            "EMAIL_MAIN", Set.of("email", "password", "systems", "active", "specialTag"),
            "OFFICE365", Set.of("email", "password", "sector", "previousUser"),
            "SKYPE", Set.of("user", "skypeEmail", "password", "recoveryEmail"),
            "REDIRECTS", Set.of("sourceEmail", "targetEmail"),
            "MANAGERS", Set.of("fullName", "managerSector", "email"),
            "EXTERNAL_ACCOUNTS", Set.of("description", "email", "password", "samsungPassword"),
            "DEVICES", Set.of("deviceSector", "emailUsed", "gmailPassword", "samsungPassword", "pin", "user"),
            "LAUNDRY", Set.of("user", "cpanel", "office365", "cpanelPassword", "office365Password", "sector"));
    private static final Map<String, Set<String>> REQUIRED = Map.of(
            "EMAIL_MAIN", Set.of("email"), "OFFICE365", Set.of("email"),
            "SKYPE", Set.of("user"), "REDIRECTS", Set.of("sourceEmail", "targetEmail"),
            "MANAGERS", Set.of("email"), "EXTERNAL_ACCOUNTS", Set.of("email"),
            "DEVICES", Set.of("deviceSector"), "LAUNDRY", Set.of("user"));

    private final EmailAccessRecordRepository records;
    private final EmailAccessCrypto crypto;
    private final ObjectMapper mapper;
    private final AuditService audit;

    public EmailAccessService(EmailAccessRecordRepository records, EmailAccessCrypto crypto,
            ObjectMapper mapper, AuditService audit) {
        this.records = records;
        this.crypto = crypto;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<View> list(String category) {
        requireCategory(category);
        return records.findByCategoryOrderByCreatedAtDesc(category).stream().map(this::view).toList();
    }

    @Transactional
    public View create(String category, Map<String, ?> fields) {
        Map<String, String> normalized = normalize(category, fields);
        EmailAccessRecord record = new EmailAccessRecord();
        record.setId(UUID.randomUUID());
        record.setCategory(category);
        record.setDataCiphertext(crypto.encrypt(normalized));
        records.save(record);
        audit.record("email-access", null, record.getId(), "EMAIL_ACCESS_CREATE", "SUCCESS", Map.of("category", category));
        return view(record);
    }

    @Transactional
    public View update(UUID id, Map<String, ?> fields) {
        EmailAccessRecord record = require(id);
        Map<String, String> normalized = normalize(record.getCategory(), fields);
        record.setDataCiphertext(crypto.encrypt(normalized));
        records.save(record);
        audit.record("email-access", null, id, "EMAIL_ACCESS_UPDATE", "SUCCESS", Map.of("category", record.getCategory()));
        return view(record);
    }

    @Transactional
    public void delete(UUID id) {
        EmailAccessRecord record = require(id);
        String category = record.getCategory();
        records.delete(record);
        audit.record("email-access", null, id, "EMAIL_ACCESS_DELETE", "SUCCESS", Map.of("category", category));
    }

    @Transactional(readOnly = true)
    public Set<String> existingRedirectKeys() {
        Set<String> keys = new HashSet<>();
        for (EmailAccessRecord record : records.findByCategoryOrderByCreatedAtDesc("REDIRECTS")) {
            Map<String, String> fields = crypto.decrypt(record.getDataCiphertext());
            keys.add(redirectKey(fields.get("sourceEmail"), fields.get("targetEmail")));
        }
        return keys;
    }

    @Transactional
    public View importRow(String category, Map<String, String> fields, String sourceFile, int sourceRow) {
        EmailAccessRecord record = records.findBySourceFileAndSourceRow(sourceFile, sourceRow).orElseGet(EmailAccessRecord::new);
        boolean fresh = record.getId() == null;
        Map<String, String> normalized = normalize(category, fields);
        if (fresh) {
            record.setId(UUID.randomUUID());
            record.setSourceFile(sourceFile);
            record.setSourceRow(sourceRow);
        }
        record.setCategory(category);
        record.setDataCiphertext(crypto.encrypt(normalized));
        records.save(record);
        return view(record);
    }

    @Transactional
    public void recordImport(String checksum, String filename, int count) {
        // Import marker is stored by the caller's repository in the same transaction.
        audit.record("email-access", null, null, "EMAIL_ACCESS_IMPORT", "SUCCESS",
                Map.of("source", filename, "records", count, "checksum", checksum));
    }

    public String redirectKey(String source, String target) {
        return staticRedirectKey(source, target);
    }

    public static String staticRedirectKey(String source, String target) {
        return (source == null ? "" : source.strip().toLowerCase(Locale.ROOT)) + "\u0000"
                + (target == null ? "" : target.strip().toLowerCase(Locale.ROOT));
    }

    public static boolean supportedCategory(String category) { return FIELDS.containsKey(category); }

    private View view(EmailAccessRecord record) {
        return new View(record.getId(), record.getCategory(), crypto.decrypt(record.getDataCiphertext()), record.getCreatedAt());
    }

    private EmailAccessRecord require(UUID id) {
        return records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado."));
    }

    private static Map<String, String> normalize(String category, Map<String, ?> input) {
        requireCategory(category);
        if (input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe os campos do registro.");
        Set<String> allowed = FIELDS.get(category);
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : allowed) {
            Object raw = input.get(key);
            if (raw != null && !(raw instanceof String))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Os campos devem ser textos.");
            values.put(key, raw == null ? "" : ((String) raw).strip());
        }
        for (String key : input.keySet()) if (!allowed.contains(key))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo não permitido: " + key);
        for (String key : REQUIRED.get(category)) if (values.get(key).isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preencha o campo obrigatório.");
        if (category.equals("EMAIL_MAIN") && !Set.of("Sim", "Não").contains(values.get("active")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O campo Ativo deve ser Sim ou Não.");
        if (category.equals("EMAIL_MAIN") && values.get("specialTag").length() > 80)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A tag especial excede o limite permitido.");
        return values;
    }

    private static void requireCategory(String category) {
        if (!FIELDS.containsKey(category)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria inválida.");
    }

    public record View(UUID id, String category, Map<String, String> fields, Instant createdAt) { }
}
