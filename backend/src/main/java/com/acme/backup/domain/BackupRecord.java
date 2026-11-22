package com.acme.backup.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "backup_record")
public class BackupRecord {
    @Id private UUID id;
    @Column(name = "volume_id", nullable = false) private String volumeId;
    @Column(name = "usuario_setor", nullable = false) private String usuarioSetor;
    @Column(name = "created_date") private LocalDate createdDate;
    @Column(name = "test_status", nullable = false, length = 20) private String testStatus;
    @Column(name = "size_value", precision = 18, scale = 3) private BigDecimal sizeValue;
    private String unit;
    @Column(nullable = false) private boolean blocked;
    @Column(name = "source_sheet") private String sourceSheet;
    @Column(name = "source_row") private Integer sourceRow;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "source_details", columnDefinition = "jsonb") private Map<String, Object> sourceDetails;
    @Column(name = "created_by", nullable = false) private String createdBy;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    public BackupRecord() {}
    public UUID getId() { return id; } public void setId(UUID v) { id=v; }
    public String getVolumeId() { return volumeId; } public void setVolumeId(String v) { volumeId=v; }
    public String getUsuarioSetor() { return usuarioSetor; } public void setUsuarioSetor(String v) { usuarioSetor=v; }
    public LocalDate getCreatedDate() { return createdDate; } public void setCreatedDate(LocalDate v) { createdDate=v; }
    public String getTestStatus() { return testStatus; } public void setTestStatus(String v) { testStatus=v; }
    public BigDecimal getSizeValue() { return sizeValue; } public void setSizeValue(BigDecimal v) { sizeValue=v; }
    public String getUnit() { return unit; } public void setUnit(String v) { unit=v; }
    public boolean isBlocked() { return blocked; } public void setBlocked(boolean v) { blocked=v; }
    public String getSourceSheet() { return sourceSheet; } public void setSourceSheet(String v) { sourceSheet=v; }
    public Integer getSourceRow() { return sourceRow; } public void setSourceRow(Integer v) { sourceRow=v; }
    public Map<String, Object> getSourceDetails() { return sourceDetails; } public void setSourceDetails(Map<String, Object> v) { sourceDetails=v; }
    public String getCreatedBy() { return createdBy; } public void setCreatedBy(String v) { createdBy=v; }
    public Instant getCreatedAt() { return createdAt; }
}
