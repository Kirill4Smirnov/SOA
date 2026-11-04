-- total_price is the immutable price snapshot required by the booking domain.
-- Keeping unit price as well would make one non-key attribute derivable from others.
ALTER TABLE bookings DROP COLUMN price_per_seat;
