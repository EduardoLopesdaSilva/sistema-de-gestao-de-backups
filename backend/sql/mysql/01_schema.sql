-- Ease Confecções | Schema do painel de backups e acessos
-- Compatível com MySQL 8.0.18+ e MySQL Workbench.
-- Execute este arquivo em uma instância MySQL vazia ou previamente preparada.
-- Ele cria apenas objetos que ainda não existem; não apaga nem altera dados existentes.

CREATE DATABASE IF NOT EXISTS ease_backup_control
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE ease_backup_control;

CREATE TABLE IF NOT EXISTS backup_volume (
  id VARCHAR(80) NOT NULL,
  name VARCHAR(240) NOT NULL,
  capacity_gb DECIMAL(14,3) NULL,
  occupied_gb DECIMAL(14,3) NULL,
  reported_free_gb DECIMAL(14,3) NULL,
  note TEXT NULL,
  measurement_source VARCHAR(40) NOT NULL,
  measured_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_backup_volume_name (name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS legacy_item (
  id VARCHAR(120) NOT NULL,
  volume_name VARCHAR(240) NOT NULL,
  label TEXT NULL,
  created_text VARCHAR(120) NULL,
  test_status TEXT NULL,
  size_text VARCHAR(100) NULL,
  unit VARCHAR(32) NULL,
  source_sheet VARCHAR(40) NOT NULL,
  source_row INT NOT NULL,
  kind VARCHAR(40) NOT NULL DEFAULT 'LEGACY_AGGREGATE',
  PRIMARY KEY (id),
  UNIQUE KEY uq_legacy_source (source_sheet, source_row),
  KEY idx_legacy_volume (volume_name),
  KEY idx_legacy_label (label(191))
) ENGINE=InnoDB;

-- Mantida para compatibilidade com metadados antigos; não representa upload ativo.
CREATE TABLE IF NOT EXISTS managed_item (
  id BINARY(16) NOT NULL,
  volume_id VARCHAR(80) NOT NULL,
  parent_id BINARY(16) NULL,
  relative_path VARCHAR(680) NOT NULL,
  display_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(160) NULL,
  size_bytes BIGINT NOT NULL,
  sha256 CHAR(64) NULL,
  storage_key BINARY(16) NOT NULL,
  created_by VARCHAR(160) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  item_kind VARCHAR(16) NOT NULL DEFAULT 'FILE',
  PRIMARY KEY (id),
  UNIQUE KEY uq_managed_storage_key (storage_key),
  UNIQUE KEY uq_managed_volume_path (volume_id, relative_path),
  KEY idx_managed_parent (parent_id),
  KEY idx_managed_hash (sha256),
  KEY idx_managed_name (display_name),
  CONSTRAINT fk_managed_volume FOREIGN KEY (volume_id) REFERENCES backup_volume(id),
  CONSTRAINT fk_managed_parent FOREIGN KEY (parent_id) REFERENCES managed_item(id),
  CONSTRAINT chk_managed_size CHECK (size_bytes >= 0),
  CONSTRAINT chk_managed_kind CHECK (item_kind IN ('FILE', 'FOLDER'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS audit_event (
  id BINARY(16) NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  actor VARCHAR(160) NULL,
  volume_id VARCHAR(80) NULL,
  item_id BINARY(16) NULL,
  action VARCHAR(60) NOT NULL,
  result VARCHAR(30) NOT NULL,
  details JSON NULL,
  PRIMARY KEY (id),
  KEY idx_audit_date (occurred_at),
  KEY idx_audit_volume (volume_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS app_setting (
  setting_key VARCHAR(120) NOT NULL,
  setting_value JSON NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS import_run (
  id BINARY(16) NOT NULL,
  source_checksum CHAR(64) NOT NULL,
  source_name VARCHAR(255) NOT NULL,
  started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  completed_at TIMESTAMP(6) NULL,
  outcome VARCHAR(30) NOT NULL,
  imported_volumes INT NOT NULL DEFAULT 0,
  imported_items INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uq_import_run_checksum (source_checksum)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS backup_record (
  id BINARY(16) NOT NULL,
  volume_id VARCHAR(80) NOT NULL,
  usuario_setor VARCHAR(255) NOT NULL,
  created_date DATE NULL,
  test_status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
  size_value DECIMAL(18,3) NULL,
  unit VARCHAR(2) NOT NULL DEFAULT 'GB',
  blocked BOOLEAN NOT NULL DEFAULT FALSE,
  source_sheet VARCHAR(40) NULL,
  source_row INT NULL,
  source_details JSON NULL,
  created_by VARCHAR(160) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_backup_record_legacy_source (source_sheet, source_row),
  KEY idx_backup_record_volume (volume_id),
  KEY idx_backup_record_usuario_setor (usuario_setor),
  CONSTRAINT fk_backup_record_volume FOREIGN KEY (volume_id) REFERENCES backup_volume(id),
  CONSTRAINT chk_backup_record_status CHECK (test_status IN ('CONFERIDO', 'PENDENTE', 'COM ERRO')),
  CONSTRAINT chk_backup_record_unit CHECK (unit IN ('KB', 'MB', 'GB', 'TB'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS backup_record_import (
  source_checksum CHAR(64) NOT NULL,
  imported_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  imported_records INT NOT NULL,
  PRIMARY KEY (source_checksum)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS email_access_record (
  id BINARY(16) NOT NULL,
  category VARCHAR(40) NOT NULL,
  data_ciphertext TEXT NOT NULL,
  source_file VARCHAR(255) NULL,
  source_row INT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_email_access_source (source_file, source_row),
  KEY idx_email_access_category (category, created_at),
  CONSTRAINT chk_email_access_category CHECK (category IN (
    'EMAIL_MAIN', 'OFFICE365', 'SKYPE', 'REDIRECTS', 'MANAGERS',
    'EXTERNAL_ACCOUNTS', 'DEVICES', 'LAUNDRY'
  ))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS email_access_import (
  source_checksum CHAR(64) NOT NULL,
  source_name VARCHAR(255) NOT NULL,
  imported_records INT NOT NULL,
  imported_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (source_checksum)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS auth_user (
  username VARCHAR(80) NOT NULL,
  password_hash VARCHAR(60) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (username)
) ENGINE=InnoDB;

DELIMITER $$
DROP TRIGGER IF EXISTS trg_email_access_record_updated_at$$
CREATE TRIGGER trg_email_access_record_updated_at
BEFORE UPDATE ON email_access_record
FOR EACH ROW
BEGIN
  SET NEW.updated_at = CURRENT_TIMESTAMP(6);
END$$
DELIMITER ;
