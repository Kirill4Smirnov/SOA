CREATE TABLE airlines (
    code VARCHAR(3) PRIMARY KEY CHECK (code ~ '^[A-Z0-9]{2,3}$'),
    name VARCHAR(120) NOT NULL UNIQUE
);

CREATE TABLE airports (
    code VARCHAR(3) PRIMARY KEY CHECK (code ~ '^[A-Z]{3}$'),
    name VARCHAR(120) NOT NULL,
    city VARCHAR(120) NOT NULL
);

CREATE TABLE flights (
    id UUID PRIMARY KEY,
    flight_number VARCHAR(10) NOT NULL CHECK (flight_number ~ '^[A-Z0-9]{2,3}[0-9]{1,4}$'),
    airline_code VARCHAR(3) NOT NULL REFERENCES airlines(code),
    origin_code VARCHAR(3) NOT NULL REFERENCES airports(code),
    destination_code VARCHAR(3) NOT NULL REFERENCES airports(code),
    departure_time TIMESTAMPTZ NOT NULL,
    arrival_time TIMESTAMPTZ NOT NULL,
    total_seats INTEGER NOT NULL CHECK (total_seats > 0),
    available_seats INTEGER NOT NULL CHECK (available_seats >= 0 AND available_seats <= total_seats),
    price NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('SCHEDULED', 'DEPARTED', 'CANCELLED', 'COMPLETED')),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (origin_code <> destination_code),
    CHECK (arrival_time > departure_time)
);

CREATE UNIQUE INDEX uq_flight_number_departure_date
    ON flights (flight_number, ((departure_time AT TIME ZONE 'UTC')::date));
CREATE INDEX idx_flight_search
    ON flights (origin_code, destination_code, departure_time, status);

CREATE TABLE seat_reservations (
    id UUID PRIMARY KEY,
    flight_id UUID NOT NULL REFERENCES flights(id),
    booking_id UUID NOT NULL UNIQUE,
    seat_count INTEGER NOT NULL CHECK (seat_count > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'RELEASED', 'EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ,
    CHECK ((status = 'ACTIVE' AND released_at IS NULL) OR status <> 'ACTIVE')
);

INSERT INTO airlines(code, name) VALUES
    ('SU', 'Aeroflot'),
    ('S7', 'S7 Airlines');

INSERT INTO airports(code, name, city) VALUES
    ('SVO', 'Sheremetyevo International Airport', 'Moscow'),
    ('VKO', 'Vnukovo International Airport', 'Moscow'),
    ('LED', 'Pulkovo Airport', 'Saint Petersburg'),
    ('KZN', 'Kazan International Airport', 'Kazan');

INSERT INTO flights(id, flight_number, airline_code, origin_code, destination_code,
                    departure_time, arrival_time, total_seats, available_seats, price, status)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'SU1234', 'SU', 'SVO', 'LED',
     '2026-04-01T07:00:00Z', '2026-04-01T08:30:00Z', 180, 180, 7490.00, 'SCHEDULED'),
    ('22222222-2222-2222-2222-222222222222', 'S71001', 'S7', 'SVO', 'LED',
     '2026-04-01T12:00:00Z', '2026-04-01T13:35:00Z', 160, 160, 6890.00, 'SCHEDULED'),
    ('33333333-3333-3333-3333-333333333333', 'SU1502', 'SU', 'VKO', 'KZN',
     '2026-04-02T09:00:00Z', '2026-04-02T10:40:00Z', 140, 140, 5990.00, 'SCHEDULED');

