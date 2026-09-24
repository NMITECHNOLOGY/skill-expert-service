CREATE TABLE expert_bookings (
    id                BIGSERIAL PRIMARY KEY,
    profile_id        BIGINT       NOT NULL REFERENCES expert_profiles (id) ON DELETE CASCADE,
    customer_user_id  VARCHAR(64)  NOT NULL,
    customer_name     VARCHAR(200) NOT NULL,
    service_title     VARCHAR(200) NOT NULL,
    price             VARCHAR(64),
    address           VARCHAR(500),
    note              VARCHAR(1000),
    scheduled_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    status            VARCHAR(32)  NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_expert_bookings_status CHECK (
        status IN ('REQUESTED', 'CONFIRMED', 'DECLINED', 'CANCELLED', 'COMPLETED')
    )
);

CREATE INDEX idx_expert_bookings_profile ON expert_bookings (profile_id, scheduled_at);
CREATE INDEX idx_expert_bookings_customer ON expert_bookings (customer_user_id, scheduled_at DESC);
