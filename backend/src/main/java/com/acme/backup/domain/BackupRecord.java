package com.acme.backup.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "backup_record")
public class BackupRecord {
    @Id private UUID id;
    @Column(name = "volume_id", nullable = false) private String volumeId;
    @Column(name = "usuario_setor", nullable = false) private String usuarioSetor;
    @Column(name = "created_text") private String createdText;
    @Column(name = "test_status", columnDefinition = "text") private String testStatus;
    @Column(name = "size_text") private String sizeText;
    private String unit;
    @Column(name = "source_sheet") private String sourceSheet;
    @Column(name = "source_row") private Integer sourceRow;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "source_details", columnDefinition = "jsonb") private Map<String, Object> sourceDetails;
    @Column(name = "created_by", nullable = false) private String createdBy;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    public BackupRecord() {}
    public UUID getId() { return id; } public void setId(UUID v) { id=v; }
    public String getVolumeId() { return volumeId; } public void setVolumeId(String v) { volumeId=v; }
    public String getUsuarioSetor() { return usuarioSetor; } public void setUsuarioSetor(String v) { usuarioSetor=v; }
    public String getCreatedText() { return createdText; } public void setCreatedText(String v) { createdText=v; }
    public String getTestStatus() { return testStatus; } public void setTestStatus(String v) { testStatus=v; }
    public String getSizeText() { return sizeText; } public void setSizeText(String v) { sizeText=v; }
    public String getUnit() { return unit; } public void setUnit(String v) { unit=v; }
    public String getSourceSheet() { return sourceSheet; } public void setSourceSheet(String v) { sourceSheet=v; }
    public Integer getSourceRow() { return sourceRow; } public void setSourceRow(Integer v) { sourceRow=v; }
    public Map<String, Object> getSourceDetails() { return sourceDetails; } public void setSourceDetails(Map<String, Object> v) { sourceDetails=v; }
    public String getCreatedBy() { return createdBy; } public void setCreatedBy(String v) { createdBy=v; }
    public Instant getCreatedAt() { return createdAt; }
}
