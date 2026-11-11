package com.acme.backup.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "legacy_item", uniqueConstraints = @UniqueConstraint(columnNames = {"source_sheet", "source_row"}))
public class LegacyItem {
    @Id private String id;
    @Column(name = "volume_name", nullable = false) private String volumeName;
    @Column(columnDefinition = "text") private String label;
    @Column(name = "created_text") private String createdText;
    @Column(name = "test_status", columnDefinition = "text") private String testStatus;
    @Column(name = "size_text") private String sizeText;
    private String unit;
    @Column(name = "source_sheet", nullable = false) private String sourceSheet;
    @Column(name = "source_row", nullable = false) private int sourceRow;
    @Column(nullable = false) private String kind;
    public LegacyItem() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getVolumeName() { return volumeName; }
    public void setVolumeName(String value) { volumeName = value; }
    public String getLabel() { return label; }
    public void setLabel(String value) { label = value; }
    public String getCreatedText() { return createdText; }
    public void setCreatedText(String value) { createdText = value; }
    public String getTestStatus() { return testStatus; }
    public void setTestStatus(String value) { testStatus = value; }
    public String getSizeText() { return sizeText; }
    public void setSizeText(String value) { sizeText = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
    public String getSourceSheet() { return sourceSheet; }
    public void setSourceSheet(String value) { sourceSheet = value; }
    public int getSourceRow() { return sourceRow; }
    public void setSourceRow(int value) { sourceRow = value; }
    public String getKind() { return kind; }
    public void setKind(String value) { kind = value; }
}
