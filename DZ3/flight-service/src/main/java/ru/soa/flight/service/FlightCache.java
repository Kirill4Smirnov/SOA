package ru.soa.flight.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class FlightCache {
    private static final Logger log = LoggerFactory.getLogger(FlightCache.class);
    private static final TypeReference<List<FlightSnapshot>> FLIGHT_LIST = new TypeReference<>() {};

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public FlightCache(StringRedisTemplate redis, ObjectMapper objectMapper,
                       @Value("${flight.cache.ttl}") Duration ttl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public Optional<FlightSnapshot> getFlight(UUID id) {
        String key = flightKey(id);
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                log.info("cache miss key={}", key);
                return Optional.empty();
            }
            log.info("cache hit key={}", key);
            return Optional.of(objectMapper.readValue(json, FlightSnapshot.class));
        } catch (Exception exception) {
            log.warn("cache read failed key={}, fallback=db", key, exception);
            return Optional.empty();
        }
    }

    public void putFlight(FlightSnapshot flight) {
        put(flightKey(flight.id()), flight);
    }

    public Optional<List<FlightSnapshot>> getSearch(String key) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                log.info("cache miss key={}", key);
                return Optional.empty();
            }
            log.info("cache hit key={}", key);
            return Optional.of(objectMapper.readValue(json, FLIGHT_LIST));
        } catch (Exception exception) {
            log.warn("cache read failed key={}, fallback=db", key, exception);
            return Optional.empty();
        }
    }

    public void putSearch(String key, List<FlightSnapshot> flights) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(flights), ttl);
            for (FlightSnapshot flight : flights) {
                String indexKey = searchIndexKey(flight.id());
                redis.opsForSet().add(indexKey, key);
                redis.expire(indexKey, ttl);
            }
        } catch (Exception exception) {
            log.warn("cache write failed key={}", key, exception);
        }
    }

    public void invalidate(UUID flightId, String origin, String destination, LocalDate departureDate) {
        String indexKey = searchIndexKey(flightId);
        try {
            Set<String> searchKeys = redis.opsForSet().members(indexKey);
            redis.delete(flightKey(flightId));
            if (searchKeys != null && !searchKeys.isEmpty()) redis.delete(searchKeys);
            redis.delete(List.of(
                    searchKey(origin, destination, "all"),
                    searchKey(origin, destination, departureDate.toString())));
            redis.delete(indexKey);
            log.info("cache invalidated flight_id={} search_keys={}", flightId,
                    searchKeys == null ? 0 : searchKeys.size());
        } catch (Exception exception) {
            log.warn("cache invalidation failed flight_id={}", flightId, exception);
        }
    }

    public String searchKey(String origin, String destination, String date) {
        return "search:" + origin + ":" + destination + ":" + date;
    }

    private void put(String key, Object value) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception exception) {
            log.warn("cache write failed key={}", key, exception);
        }
    }

    private String flightKey(UUID id) { return "flight:" + id; }
    private String searchIndexKey(UUID id) { return "flight-search-keys:" + id; }
}

