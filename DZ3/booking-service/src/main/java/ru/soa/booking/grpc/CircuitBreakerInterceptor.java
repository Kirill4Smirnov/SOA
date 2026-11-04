package ru.soa.booking.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

@Component
public class CircuitBreakerInterceptor implements ClientInterceptor {
    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerInterceptor.class);

    enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long openTimeoutNanos;
    private final int windowSize;
    private final Deque<Boolean> outcomes = new ArrayDeque<>();
    private State state = State.CLOSED;
    private long openedAt;
    private boolean halfOpenRequestInFlight;

    public CircuitBreakerInterceptor(
            @Value("${flight.grpc.circuit-breaker.failure-threshold}") int failureThreshold,
            @Value("${flight.grpc.circuit-breaker.open-timeout}") Duration openTimeout,
            @Value("${flight.grpc.circuit-breaker.window-size}") int windowSize) {
        if (failureThreshold <= 0 || windowSize < failureThreshold) {
            throw new IllegalArgumentException("Circuit breaker window must contain the failure threshold");
        }
        this.failureThreshold = failureThreshold;
        this.openTimeoutNanos = openTimeout.toNanos();
        this.windowSize = windowSize;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        synchronized (this) {
            if (state == State.OPEN && System.nanoTime() - openedAt >= openTimeoutNanos) {
                transition(State.HALF_OPEN);
                halfOpenRequestInFlight = false;
            }
            if (state == State.OPEN) return rejectedCall();
            if (state == State.HALF_OPEN) {
                if (halfOpenRequestInFlight) return rejectedCall();
                halfOpenRequestInFlight = true;
            }
        }

        ClientCall<ReqT, RespT> delegate = next.newCall(method, callOptions);
        return new ForwardingClientCall.SimpleForwardingClientCall<>(delegate) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                Listener<RespT> listener = new ForwardingClientCallListener
                        .SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        record(status);
                        super.onClose(status, trailers);
                    }
                };
                super.start(listener, headers);
            }
        };
    }

    private synchronized void record(Status result) {
        boolean infrastructureFailure = switch (result.getCode()) {
            case UNAVAILABLE, DEADLINE_EXCEEDED, INTERNAL, UNKNOWN -> true;
            default -> false;
        };
        if (state == State.HALF_OPEN) {
            halfOpenRequestInFlight = false;
            if (infrastructureFailure) open();
            else {
                outcomes.clear();
                transition(State.CLOSED);
            }
            return;
        }
        if (state != State.CLOSED) return;

        outcomes.addLast(infrastructureFailure);
        while (outcomes.size() > windowSize) outcomes.removeFirst();
        long failures = outcomes.stream().filter(Boolean::booleanValue).count();
        if (failures >= failureThreshold) open();
    }

    private void open() {
        openedAt = System.nanoTime();
        transition(State.OPEN);
    }

    private void transition(State target) {
        if (state != target) {
            log.warn("gRPC circuit breaker transition {} -> {}", state, target);
            state = target;
        }
    }

    private <ReqT, RespT> ClientCall<ReqT, RespT> rejectedCall() {
        return new ClientCall<>() {
            @Override
            public void start(Listener<RespT> listener, Metadata headers) {
                listener.onClose(Status.UNAVAILABLE.withDescription("Flight Service circuit breaker is OPEN"),
                        new Metadata());
            }
            @Override public void request(int numMessages) {}
            @Override public void cancel(String message, Throwable cause) {}
            @Override public void halfClose() {}
            @Override public void sendMessage(ReqT message) {}
        };
    }
}

