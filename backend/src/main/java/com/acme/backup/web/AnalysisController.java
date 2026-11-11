package com.acme.backup.web;

import com.acme.backup.domain.ManagedItem;
import com.acme.backup.repository.ManagedItemRepository;
import com.acme.backup.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {
    private final ManagedItemRepository items;
    private final AuditService audit;
    public AnalysisController(ManagedItemRepository items, AuditService audit) { this.items = items; this.audit = audit; }

    @GetMapping("/duplicates") @Transactional
    public List<DuplicateSet> duplicates(Authentication authentication) {
        List<DuplicateSet> result = items.findDuplicateHashGroups().stream().map(group -> {
            String hash = (String) group[0]; List<ManagedItem> files = items.findBySha256(hash);
            long retainedSize = files.stream().mapToLong(ManagedItem::getSizeBytes).max().orElse(0);
            long totalSize = files.stream().mapToLong(ManagedItem::getSizeBytes).sum();
            return new DuplicateSet(hash, files.size(), Math.max(0, totalSize - retainedSize), files.stream().map(AnalysisController::view).toList());
        }).toList();
        audit.record(authentication.getName(), null, null, "ANALYZE_DUPLICATES", "SUCCESS", Map.of("duplicateGroups", result.size()));
        return result;
    }

    @GetMapping("/old-files") @Transactional
    public Page<AnalysisItem> oldFiles(@RequestParam(defaultValue = "365") int days,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size, Authentication authentication) {
        if (days < 1 || days > 3650 || page < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período ou paginação inválidos.");
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        Page<AnalysisItem> result = items.findByItemKindAndCreatedAtBefore("FILE", cutoff,
            PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"))).map(AnalysisController::view);
        audit.record(authentication.getName(), null, null, "ANALYZE_OLD_FILES", "SUCCESS", Map.of("days", days, "cutoff", cutoff.toString(), "matches", result.getTotalElements()));
        return result;
    }

    private static AnalysisItem view(ManagedItem item) {
        return new AnalysisItem(item.getId(), item.getVolumeId(), item.getDisplayName(), item.getRelativePath(), item.getSizeBytes(), item.getSha256(), item.getCreatedAt());
    }
    public record AnalysisItem(java.util.UUID id, String volumeId, String name, String relativePath, long sizeBytes, String sha256, Instant createdAt) {}
    public record DuplicateSet(String sha256, int copies, long potentialBytes, List<AnalysisItem> items) {}
}
