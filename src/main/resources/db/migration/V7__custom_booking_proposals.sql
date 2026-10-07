ALTER TABLE expert_bookings ADD COLUMN request_kind VARCHAR(16) NOT NULL DEFAULT 'CATALOG';

ALTER TABLE expert_bookings ADD COLUMN proposal_note VARCHAR(1000);

ALTER TABLE expert_bookings ADD COLUMN proposed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE expert_bookings DROP CONSTRAINT ck_expert_bookings_status;

ALTER TABLE expert_bookings ADD CONSTRAINT ck_expert_bookings_status CHECK (
    status IN ('REQUESTED', 'CONFIRMED', 'DECLINED', 'CANCELLED', 'COMPLETED', 'PROPOSED')
);

ALTER TABLE expert_bookings ADD CONSTRAINT ck_expert_bookings_request_kind CHECK (
    request_kind IN ('CATALOG', 'CUSTOM')
);
