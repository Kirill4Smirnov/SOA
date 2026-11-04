package ru.soa.booking.web;

import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.soa.booking.grpc.FlightGateway;
import ru.soa.booking.web.dto.FlightResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Validated
@RestController
public class FlightController {
    private final FlightGateway flights;

    public FlightController(FlightGateway flights) {
        this.flights = flights;
    }

    @GetMapping("/flights")
    public List<FlightResponse> search(
            @RequestParam @Pattern(regexp = "(?i)[A-Z]{3}") String origin,
            @RequestParam @Pattern(regexp = "(?i)[A-Z]{3}") String destination,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return flights.search(origin.toUpperCase(Locale.ROOT), destination.toUpperCase(Locale.ROOT), date)
                .getFlightsList().stream().map(FlightResponse::from).toList();
    }

    @GetMapping("/flights/{id}")
    public FlightResponse get(@PathVariable UUID id) {
        return FlightResponse.from(flights.get(id));
    }
}

