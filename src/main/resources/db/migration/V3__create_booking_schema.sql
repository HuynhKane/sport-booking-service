CREATE TABLE court_block (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id uuid NOT NULL REFERENCES court (id) ON DELETE RESTRICT,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    reason varchar(255),
    created_by_user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT court_block_interval_check
        CHECK (ends_at > starts_at)
);

CREATE INDEX court_block_interval_idx
    ON court_block (court_id, starts_at, ends_at);

CREATE TABLE booking (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id uuid NOT NULL REFERENCES court (id) ON DELETE RESTRICT,
    player_user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    created_by_user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'CONFIRMED',
    source varchar(20) NOT NULL,
    notes text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT booking_interval_check
        CHECK (ends_at > starts_at),
    CONSTRAINT booking_status_check
        CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT booking_source_check
        CHECK (source IN ('PLAYER', 'OWNER'))
);

ALTER TABLE booking
    ADD CONSTRAINT booking_no_active_overlap
    EXCLUDE USING gist (
        court_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    )
    WHERE (status IN ('PENDING', 'CONFIRMED'));

CREATE INDEX booking_court_interval_idx
    ON booking (court_id, starts_at, ends_at);

CREATE INDEX booking_player_start_idx
    ON booking (player_user_id, starts_at DESC);

CREATE INDEX booking_creator_idx
    ON booking (created_by_user_id);

CREATE TABLE booking_cancellation (
    booking_id uuid PRIMARY KEY REFERENCES booking (id) ON DELETE RESTRICT,
    cancelled_by_user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    reason varchar(500),
    cancelled_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX booking_cancellation_user_idx
    ON booking_cancellation (cancelled_by_user_id);
