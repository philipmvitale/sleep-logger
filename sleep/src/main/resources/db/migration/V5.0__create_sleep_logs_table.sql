CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE TYPE mood_type AS ENUM ('BAD', 'OK', 'GOOD');
CREATE TABLE sleep_logs
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mood          mood_type   NOT NULL,
    bed_time      TIMESTAMPTZ NOT NULL,
    bed_timezone  TEXT        NOT NULL REFERENCES timezones (name),
    wake_time     TIMESTAMPTZ NOT NULL,
    wake_timezone TEXT        NOT NULL REFERENCES timezones (name),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT wake_after_bed CHECK (wake_time > bed_time),
    CONSTRAINT no_overlapping_sleep EXCLUDE USING gist(user_id WITH =, tstzrange(bed_time, wake_time) WITH &&)
);
CREATE INDEX idx_sleep_logs_user_id_wake_time ON sleep_logs (user_id, wake_time DESC);

CREATE TRIGGER trg_set_updated_at
    BEFORE UPDATE
    ON sleep_logs
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
