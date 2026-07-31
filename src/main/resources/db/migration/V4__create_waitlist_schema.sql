CREATE TABLE waitlist_entry (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id uuid NOT NULL REFERENCES court (id) ON DELETE RESTRICT,
    player_user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    desired_starts_at timestamptz NOT NULL,
    desired_ends_at timestamptz NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'WAITING',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT waitlist_entry_interval_check
        CHECK (desired_ends_at > desired_starts_at),
    CONSTRAINT waitlist_entry_status_check
        CHECK (status IN ('WAITING', 'OFFERED', 'FULFILLED', 'EXPIRED', 'CANCELLED'))
);

CREATE UNIQUE INDEX waitlist_entry_active_unique_idx
    ON waitlist_entry (
        court_id,
        player_user_id,
        desired_starts_at,
        desired_ends_at
    )
    WHERE status IN ('WAITING', 'OFFERED');

CREATE INDEX waitlist_entry_match_idx
    ON waitlist_entry (
        court_id,
        desired_starts_at,
        desired_ends_at,
        status,
        created_at
    );

CREATE INDEX waitlist_entry_player_idx
    ON waitlist_entry (player_user_id, created_at DESC);

CREATE TABLE waitlist_offer (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    waitlist_entry_id uuid NOT NULL REFERENCES waitlist_entry (id) ON DELETE RESTRICT,
    released_booking_id uuid NOT NULL REFERENCES booking (id) ON DELETE RESTRICT,
    status varchar(20) NOT NULL DEFAULT 'PENDING',
    offered_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    responded_at timestamptz,
    CONSTRAINT waitlist_offer_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')),
    CONSTRAINT waitlist_offer_expiry_check
        CHECK (expires_at > offered_at),
    CONSTRAINT waitlist_offer_response_check
        CHECK (
            (status = 'PENDING' AND responded_at IS NULL)
            OR (status <> 'PENDING' AND responded_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX waitlist_offer_pending_entry_unique_idx
    ON waitlist_offer (waitlist_entry_id)
    WHERE status = 'PENDING';

CREATE INDEX waitlist_offer_expiry_idx
    ON waitlist_offer (status, expires_at);

CREATE INDEX waitlist_offer_released_booking_idx
    ON waitlist_offer (released_booking_id);
