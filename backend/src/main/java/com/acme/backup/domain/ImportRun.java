package com.acme.backup.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "import_run")
public class ImportRun {
    @Id private UUID id;
    @Column(name = "source_checksum", nullable = false, unique = true, length = 64, columnDefinition = "char(64)") private String sourceChecksum;
    @Column(name = "source_name", nullable = false) private String sourceName;
    @Column(nullable = false) private String outcome;
    @Column(name = "imported_volumes", nullable = false) private int importedVolumes;
    @Column(name = "imported_items", nullable = false) private int importedItems;
    public ImportRun() {}
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public String getSourceChecksum() { return sourceChecksum; }
    public void setSourceChecksum(String value) { sourceChecksum = value; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String value) { sourceName = value; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String value) { outcome = value; }
    public int getImportedVolumes() { return importedVolumes; }
    public void setImportedVolumes(int value) { importedVolumes = value; }
    public int getImportedItems() { return importedItems; }
    public void setImportedItems(int value) { importedItems = value; }
}
