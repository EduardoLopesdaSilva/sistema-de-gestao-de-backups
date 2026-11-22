CREATE TABLE email_access_record (
    id UUID PRIMARY KEY,
    category VARCHAR(40) NOT NULL CHECK (category IN (
        'EMAIL_MAIN', 'OFFICE365', 'SKYPE', 'REDIRECTS', 'MANAGERS',
        'EXTERNAL_ACCOUNTS', 'DEVICES', 'LAUNDRY'
    )),
    data_ciphertext TEXT NOT NULL,
    source_file VARCHAR(255),
    source_row INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(source_file, source_row)
);
CREATE INDEX idx_email_access_category ON email_access_record(category, created_at DESC);

CREATE TABLE email_access_import (
    source_checksum CHAR(64) PRIMARY KEY,
    source_name VARCHAR(255) NOT NULL,
    imported_records INTEGER NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
