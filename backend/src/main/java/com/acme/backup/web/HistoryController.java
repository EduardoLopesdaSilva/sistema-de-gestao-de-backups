package com.acme.backup.web;

import com.acme.backup.domain.AuditEvent;
import com.acme.backup.repository.AuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/history")
public class HistoryController {
    private final AuditEventRepository events;
    public HistoryController(AuditEventRepository events) { this.events = events; }
    @GetMapping public Page<AuditEvent> list(@RequestParam(defaultValue = "") String volume,
            @RequestParam(defaultValue = "") String action, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginação inválida.");
        PageRequest request = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        if (volume.isBlank() && action.isBlank()) return events.findAll(request);
        return events.findByVolumeIdContainingIgnoreCaseAndActionContainingIgnoreCase(volume, action, request);
    }
}
