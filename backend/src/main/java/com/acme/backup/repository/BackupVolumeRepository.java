package com.acme.backup.repository;

import com.acme.backup.domain.BackupVolume;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BackupVolumeRepository extends JpaRepository<BackupVolume, String> {
    List<BackupVolume> findAllByOrderByNameAsc();
}
