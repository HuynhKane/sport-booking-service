CREATE TABLE app_user (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email varchar(320) NOT NULL,
    password_hash varchar(255),
    display_name varchar(120) NOT NULL,
    phone_number varchar(30),
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT app_user_status_check
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT app_user_email_not_blank_check
        CHECK (btrim(email) <> ''),
    CONSTRAINT app_user_display_name_not_blank_check
        CHECK (btrim(display_name) <> '')
);

CREATE UNIQUE INDEX app_user_email_unique_idx ON app_user (lower(email));

CREATE TABLE user_role (
    user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role varchar(20) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT user_role_role_check
        CHECK (role IN ('PLAYER', 'OWNER'))
);

CREATE TABLE venue (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(160) NOT NULL,
    description text,
    phone_number varchar(30),
    address_line varchar(255) NOT NULL,
    ward varchar(120),
    district varchar(120) NOT NULL,
    city varchar(120) NOT NULL DEFAULT 'Ho Chi Minh City',
    location geography(Point, 4326) NOT NULL,
    timezone varchar(50) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT venue_status_check
        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT venue_name_not_blank_check
        CHECK (btrim(name) <> ''),
    CONSTRAINT venue_address_not_blank_check
        CHECK (btrim(address_line) <> ''),
    CONSTRAINT venue_district_not_blank_check
        CHECK (btrim(district) <> '')
);

CREATE INDEX venue_location_gist_idx ON venue USING gist (location);
CREATE INDEX venue_status_idx ON venue (status);

CREATE TABLE venue_owner (
    venue_id uuid NOT NULL REFERENCES venue (id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (venue_id, user_id)
);

CREATE INDEX venue_owner_user_idx ON venue_owner (user_id);

CREATE TABLE court (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    venue_id uuid NOT NULL REFERENCES venue (id) ON DELETE RESTRICT,
    name varchar(100) NOT NULL,
    description text,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT court_status_check
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE')),
    CONSTRAINT court_name_not_blank_check
        CHECK (btrim(name) <> ''),
    CONSTRAINT court_venue_name_unique
        UNIQUE (venue_id, name)
);

CREATE INDEX court_venue_status_idx ON court (venue_id, status);

CREATE TABLE venue_operating_hour (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    venue_id uuid NOT NULL REFERENCES venue (id) ON DELETE CASCADE,
    day_of_week smallint NOT NULL,
    opens_at time NOT NULL,
    closes_at time NOT NULL,
    CONSTRAINT venue_operating_hour_day_check
        CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT venue_operating_hour_interval_check
        CHECK (closes_at > opens_at),
    CONSTRAINT venue_operating_hour_unique
        UNIQUE (venue_id, day_of_week, opens_at, closes_at)
);

CREATE INDEX venue_operating_hour_lookup_idx
    ON venue_operating_hour (venue_id, day_of_week);
