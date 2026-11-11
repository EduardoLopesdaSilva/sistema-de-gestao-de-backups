package com.acme.backup.service;

import com.acme.backup.domain.AuditEvent;
import com.acme.backup.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditEventRepository events;
    public AuditService(AuditEventRepository events) { this.events = events; }
    @Transactional public void record(String actor, String volumeId, UUID itemId, String action, String result, Map<String, Object> details) {
        AuditEvent event = new AuditEvent(); event.setId(UUID.randomUUID()); event.setActor(actor);
        event.setVolumeId(volumeId); event.setItemId(itemId); event.setAction(action); event.setResult(result); event.setDetails(details);
        events.save(event);
    }
}
