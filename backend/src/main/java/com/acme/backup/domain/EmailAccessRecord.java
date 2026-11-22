package com.acme.backup.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_access_record")
public class EmailAccessRecord {
    @Id private UUID id;
    @Column(nullable = false, length = 40) private String category;
    @Column(name = "data_ciphertext", nullable = false, columnDefinition = "text") private String dataCiphertext;
    @Column(name = "source_file") private String sourceFile;
    @Column(name = "source_row") private Integer sourceRow;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDataCiphertext() { return dataCiphertext; }
    public void setDataCiphertext(String dataCiphertext) { this.dataCiphertext = dataCiphertext; }
    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
    public Integer getSourceRow() { return sourceRow; }
    public void setSourceRow(Integer sourceRow) { this.sourceRow = sourceRow; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
