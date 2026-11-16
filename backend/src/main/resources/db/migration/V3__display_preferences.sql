-- =============================================================================================
-- V3: per-user display and accessibility preferences (theme, text size, reading aids).
-- Stored on the user so they follow the person to any device. Portable SQL (PostgreSQL and H2).
-- =============================================================================================
ALTER TABLE users ADD COLUMN display_theme VARCHAR(20) DEFAULT 'SYSTEM' NOT NULL;
ALTER TABLE users ADD COLUMN display_text_size VARCHAR(20) DEFAULT 'DEFAULT' NOT NULL;
ALTER TABLE users ADD COLUMN display_reduce_motion BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE users ADD COLUMN display_easier_reading BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE users ADD COLUMN display_underline_links BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE users ADD CONSTRAINT ck_users_display_theme
    CHECK (display_theme IN ('SYSTEM', 'LIGHT', 'DARK', 'DIM', 'WARM', 'HIGH_CONTRAST'));
ALTER TABLE users ADD CONSTRAINT ck_users_display_text_size
    CHECK (display_text_size IN ('SMALL', 'DEFAULT', 'LARGE', 'EXTRA_LARGE'));
