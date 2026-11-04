CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    flight_id UUID NOT NULL,
    passenger_name VARCHAR(200) NOT NULL,
    passenger_email VARCHAR(320) NOT NULL,
    seat_count INTEGER NOT NULL CHECK (seat_count > 0),
    price_per_seat NUMERIC(12, 2) NOT NULL CHECK (price_per_seat > 0),
    total_price NUMERIC(14, 2) NOT NULL CHECK (total_price > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (total_price = price_per_seat * seat_count),
    CHECK ((status = 'CONFIRMED' AND cancelled_at IS NULL) OR status = 'CANCELLED')
);

CREATE INDEX idx_bookings_user_created ON bookings(user_id, created_at DESC);
CREATE INDEX idx_bookings_flight ON bookings(flight_id);


