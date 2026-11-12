CREATE TABLE backup_record (
    id UUID PRIMARY KEY,
    volume_id VARCHAR(80) NOT NULL REFERENCES backup_volume(id),
    usuario_setor VARCHAR(255) NOT NULL,
    created_text VARCHAR(120),
    test_status TEXT,
    size_text VARCHAR(100),
    unit VARCHAR(32),
    source_sheet VARCHAR(40),
    source_row INTEGER,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_backup_record_volume ON backup_record(volume_id);
CREATE INDEX idx_backup_record_usuario_setor ON backup_record USING gin (to_tsvector('simple', usuario_setor));
CREATE UNIQUE INDEX idx_backup_record_legacy_source ON backup_record(source_sheet, source_row) WHERE source_sheet IS NOT NULL AND source_row IS NOT NULL;
CREATE TABLE backup_record_import (source_checksum CHAR(64) PRIMARY KEY, imported_at TIMESTAMPTZ NOT NULL DEFAULT now(), imported_records INTEGER NOT NULL);
CREATE FUNCTION set_backup_record_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END;
$$;
CREATE TRIGGER trg_backup_record_updated_at BEFORE UPDATE ON backup_record
FOR EACH ROW EXECUTE FUNCTION set_backup_record_updated_at();
