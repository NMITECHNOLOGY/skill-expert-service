ALTER TABLE expert_bookings ADD COLUMN payment_reference VARCHAR(64);

CREATE UNIQUE INDEX uq_expert_bookings_payment_reference
    ON expert_bookings (payment_reference);
