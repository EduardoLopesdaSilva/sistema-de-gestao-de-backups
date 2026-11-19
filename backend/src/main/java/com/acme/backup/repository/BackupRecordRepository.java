package com.acme.backup.repository;

import com.acme.backup.domain.BackupRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface BackupRecordRepository extends JpaRepository<BackupRecord, UUID> {
    List<BackupRecord> findAllByOrderByCreatedAtDesc();

    List<BackupRecord> findByVolumeIdOrderByCreatedAtDesc(String volumeId);

    long countByVolumeId(String volumeId);

    Optional<BackupRecord> findBySourceSheetAndSourceRow(String sourceSheet, Integer sourceRow);

    @Query("select r from BackupRecord r where (:q = '' or lower(r.usuarioSetor) like lower(concat('%', :q, '%')) or lower(coalesce(r.testStatus, '')) like lower(concat('%', :q, '%'))) and (:volume = '' or lower(r.volumeId) like lower(concat('%', :volume, '%')) or exists (select v.id from BackupVolume v where v.id = r.volumeId and lower(v.name) like lower(concat('%', :volume, '%'))))")
    Page<BackupRecord> search(String q, String volume, Pageable pageable);
}
