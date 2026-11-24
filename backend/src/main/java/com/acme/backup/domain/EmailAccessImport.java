package com.acme.backup.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "email_access_import")
public class EmailAccessImport {
    @Id @Column(name = "source_checksum", length = 64, columnDefinition = "char(64)") private String sourceChecksum;
    @Column(name = "source_name", nullable = false) private String sourceName;
    @Column(name = "imported_records", nullable = false) private int importedRecords;
    @Column(name = "imported_at", insertable = false, updatable = false) private Instant importedAt;

    public String getSourceChecksum() { return sourceChecksum; }
    public void setSourceChecksum(String sourceChecksum) { this.sourceChecksum = sourceChecksum; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public int getImportedRecords() { return importedRecords; }
    public void setImportedRecords(int importedRecords) { this.importedRecords = importedRecords; }
    public Instant getImportedAt() { return importedAt; }
}
