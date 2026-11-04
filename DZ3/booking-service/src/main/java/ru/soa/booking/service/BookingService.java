package ru.soa.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.soa.booking.domain.Booking;
import ru.soa.booking.domain.BookingStatus;
import ru.soa.booking.grpc.FlightGateway;
import ru.soa.booking.repository.BookingRepository;
import ru.soa.booking.web.ApiException;
import ru.soa.booking.web.dto.BookingResponse;
import ru.soa.booking.web.dto.CreateBookingRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookings;
    private final FlightGateway flights;

    public BookingService(BookingRepository bookings, FlightGateway flights) {
        this.bookings = bookings;
        this.flights = flights;
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        UUID bookingId = UUID.randomUUID();
        var flight = flights.get(request.flightId());
        BigDecimal price = new BigDecimal(flight.getPrice());
        flights.reserve(request.flightId(), request.seatCount(), bookingId);

        try {
            Booking booking = new Booking(
                    bookingId, request.userId(), request.flightId(), request.passengerName().trim(),
                    request.passengerEmail().trim().toLowerCase(), request.seatCount(),
                    price.multiply(BigDecimal.valueOf(request.seatCount())));
            return BookingResponse.from(bookings.saveAndFlush(booking));
        } catch (RuntimeException persistenceFailure) {
            compensateReservation(bookingId, persistenceFailure);
            throw persistenceFailure;
        }
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID id) {
        return bookings.findById(id).map(BookingResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Бронирование не найдено"));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> list(UUID userId) {
        return bookings.findByUserIdOrderByCreatedAtDesc(userId).stream().map(BookingResponse::from).toList();
    }

    @Transactional
    public BookingResponse cancel(UUID id) {
        Booking booking = bookings.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Бронирование не найдено"));
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_ALREADY_CANCELLED", "Бронирование уже отменено");
        }
        flights.release(booking.getId());
        booking.cancel();
        return BookingResponse.from(booking);
    }

    private void compensateReservation(UUID bookingId, RuntimeException original) {
        try {
            flights.release(bookingId);
            log.warn("Compensated remote reservation after local booking failure booking_id={}", bookingId);
        } catch (RuntimeException compensationFailure) {
            original.addSuppressed(compensationFailure);
            log.error("Failed to compensate reservation booking_id={}", bookingId, compensationFailure);
        }
    }
}

