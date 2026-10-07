CREATE TABLE booking_reviews (
    id              BIGSERIAL PRIMARY KEY,
    booking_id      BIGINT       NOT NULL REFERENCES expert_bookings (id) ON DELETE CASCADE,
    direction       VARCHAR(32)  NOT NULL,
    stars           INTEGER      NOT NULL,
    comment         VARCHAR(500),
    author_user_id  VARCHAR(64)  NOT NULL,
    author_name     VARCHAR(200) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_booking_reviews_direction CHECK (
        direction IN ('FINDER_TO_EXPERT', 'EXPERT_TO_FINDER')
    ),
    CONSTRAINT ck_booking_reviews_stars CHECK (stars BETWEEN 1 AND 5),
    CONSTRAINT uk_booking_reviews_booking_direction UNIQUE (booking_id, direction)
);

CREATE INDEX idx_booking_reviews_booking ON booking_reviews (booking_id);
