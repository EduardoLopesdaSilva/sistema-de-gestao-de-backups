package com.acme.backup.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "backup_volume")
public class BackupVolume {
    @Id private String id;
    @Column(nullable = false, unique = true) private String name;
    @Column(name = "capacity_gb") private BigDecimal capacityGb;
    @Column(name = "occupied_gb") private BigDecimal occupiedGb;
    @Column(name = "reported_free_gb") private BigDecimal reportedFreeGb;
    @Column(columnDefinition = "text") private String note;
    @Column(name = "measurement_source", nullable = false) private String measurementSource;
    public BackupVolume() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getCapacityGb() { return capacityGb; }
    public void setCapacityGb(BigDecimal value) { this.capacityGb = value; }
    public BigDecimal getOccupiedGb() { return occupiedGb; }
    public void setOccupiedGb(BigDecimal value) { this.occupiedGb = value; }
    public BigDecimal getReportedFreeGb() { return reportedFreeGb; }
    public void setReportedFreeGb(BigDecimal value) { this.reportedFreeGb = value; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getMeasurementSource() { return measurementSource; }
    public void setMeasurementSource(String source) { this.measurementSource = source; }
}
