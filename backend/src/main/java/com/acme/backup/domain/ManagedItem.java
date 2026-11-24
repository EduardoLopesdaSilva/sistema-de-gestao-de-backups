package com.acme.backup.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "managed_item", uniqueConstraints = @UniqueConstraint(columnNames = {"volume_id", "relative_path"}))
public class ManagedItem {
    @Id private UUID id;
    @Column(name = "volume_id", nullable = false) private String volumeId;
    @Column(name = "parent_id") private UUID parentId;
    @Column(name = "relative_path", nullable = false, length = 680) private String relativePath;
    @Column(name = "display_name", nullable = false) private String displayName;
    @Column(name = "content_type") private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "sha256", length = 64) private String sha256;
    @Column(name = "storage_key", nullable = false, unique = true) private UUID storageKey;
    @Column(name = "created_by", nullable = false) private String createdBy;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "item_kind", nullable = false) private String itemKind;
    public ManagedItem() {}
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public String getVolumeId() { return volumeId; }
    public void setVolumeId(String value) { volumeId = value; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID value) { parentId = value; }
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String value) { relativePath = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { displayName = value; }
    public String getContentType() { return contentType; }
    public void setContentType(String value) { contentType = value; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long value) { sizeBytes = value; }
    public String getSha256() { return sha256; }
    public void setSha256(String value) { sha256 = value; }
    public UUID getStorageKey() { return storageKey; }
    public void setStorageKey(UUID value) { storageKey = value; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { createdBy = value; }
    public Instant getCreatedAt() { return createdAt; }
    public String getItemKind() { return itemKind; }
    public void setItemKind(String value) { itemKind = value; }
}
