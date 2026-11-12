package com.acme.backup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name="backup_record_import")
public class BackupRecordImport {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(name="source_checksum", length=64) private String sourceChecksum;
    @Column(name="imported_at", insertable=false, updatable=false) private Instant importedAt;
    @Column(name="imported_records", nullable=false) private int importedRecords;
    public BackupRecordImport() {}
    public String getSourceChecksum() { return sourceChecksum; }
    public void setSourceChecksum(String value) { sourceChecksum=value; }
    public int getImportedRecords() { return importedRecords; }
    public void setImportedRecords(int value) { importedRecords=value; }
}
