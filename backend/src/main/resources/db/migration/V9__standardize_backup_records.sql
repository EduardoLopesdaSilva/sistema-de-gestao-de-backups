ALTER TABLE backup_record ADD COLUMN recorded_date DATE;
UPDATE backup_record SET created_text = NULL
WHERE btrim(created_text) ~ '^0{1,2}/0{1,2}/0{2,4}$';

UPDATE backup_record
SET recorded_date = CASE
    WHEN btrim(created_text) ~ '^\d{4}-\d{2}-\d{2}$'
        THEN CASE WHEN to_char(to_date(btrim(created_text), 'YYYY-MM-DD'), 'YYYY-MM-DD') = btrim(created_text)
            THEN to_date(btrim(created_text), 'YYYY-MM-DD') END
    WHEN btrim(created_text) ~ '^\d{1,2}/\d{1,2}/\d{4}$'
        THEN CASE
            WHEN split_part(btrim(created_text), '/', 1)::int > 12
                THEN to_date(btrim(created_text), 'DD/MM/YYYY')
            WHEN split_part(btrim(created_text), '/', 2)::int > 12
                THEN to_date(btrim(created_text), 'MM/DD/YYYY')
            ELSE to_date(btrim(created_text), 'DD/MM/YYYY')
        END
    WHEN btrim(created_text) ~ '^\d{1,2}/\d{1,2}/\d{2}$'
        THEN CASE
            WHEN split_part(btrim(created_text), '/', 1)::int > 12
                THEN to_date(btrim(created_text), 'DD/MM/YY')
            WHEN split_part(btrim(created_text), '/', 2)::int > 12
                THEN to_date(btrim(created_text), 'MM/DD/YY')
            ELSE to_date(btrim(created_text), 'DD/MM/YY')
        END
    ELSE NULL
END;

ALTER TABLE backup_record DROP COLUMN created_text;
ALTER TABLE backup_record RENAME COLUMN recorded_date TO created_date;
ALTER TABLE backup_record ADD COLUMN size_value NUMERIC(18,3);
UPDATE backup_record
SET size_value = replace(regexp_replace(btrim(size_text), '[^0-9,.]', '', 'g'), ',', '.')::numeric
WHERE replace(regexp_replace(btrim(size_text), '[^0-9,.]', '', 'g'), ',', '.') ~ '^\d+(\.\d+)?$';
ALTER TABLE backup_record DROP COLUMN size_text;
ALTER TABLE backup_record ALTER COLUMN test_status TYPE VARCHAR(20) USING
    CASE
        WHEN test_status ILIKE '%erro%' THEN 'COM ERRO'
        WHEN test_status IS NULL OR btrim(test_status) = '' OR test_status ILIKE '%não feito%' OR test_status ILIKE '%pendente%' THEN 'PENDENTE'
        ELSE 'CONFERIDO'
    END;
ALTER TABLE backup_record ALTER COLUMN test_status SET DEFAULT 'PENDENTE';
UPDATE backup_record SET test_status = 'PENDENTE' WHERE test_status IS NULL;
ALTER TABLE backup_record ALTER COLUMN test_status SET NOT NULL;
ALTER TABLE backup_record ADD CONSTRAINT chk_backup_record_test_status CHECK (test_status IN ('CONFERIDO', 'PENDENTE', 'COM ERRO'));

UPDATE backup_record SET unit = upper(btrim(unit)) WHERE upper(btrim(unit)) IN ('KB', 'MB', 'GB', 'TB');
UPDATE backup_record SET unit = 'GB' WHERE unit IS NULL OR upper(btrim(unit)) NOT IN ('KB', 'MB', 'GB', 'TB');
ALTER TABLE backup_record ALTER COLUMN unit TYPE VARCHAR(2);
ALTER TABLE backup_record ALTER COLUMN unit SET NOT NULL;
ALTER TABLE backup_record ALTER COLUMN unit SET DEFAULT 'GB';
ALTER TABLE backup_record ADD CONSTRAINT chk_backup_record_unit CHECK (unit IN ('KB', 'MB', 'GB', 'TB'));

ALTER TABLE backup_record ADD COLUMN blocked BOOLEAN NOT NULL DEFAULT FALSE;
