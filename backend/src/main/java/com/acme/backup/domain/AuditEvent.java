package com.acme.backup.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
public class AuditEvent {
    @Id private UUID id;
    @Column(name = "occurred_at", insertable = false, updatable = false) private Instant occurredAt;
    private String actor;
    @Column(name = "volume_id") private String volumeId;
    @Column(name = "item_id") private UUID itemId;
    @Column(nullable = false) private String action;
    @Column(nullable = false) private String result;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private Map<String, Object> details;
    public AuditEvent() {}
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActor() { return actor; }
    public void setActor(String value) { actor = value; }
    public String getVolumeId() { return volumeId; }
    public void setVolumeId(String value) { volumeId = value; }
    public UUID getItemId() { return itemId; }
    public void setItemId(UUID value) { itemId = value; }
    public String getAction() { return action; }
    public void setAction(String value) { action = value; }
    public String getResult() { return result; }
    public void setResult(String value) { result = value; }
    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> value) { details = value; }
}
