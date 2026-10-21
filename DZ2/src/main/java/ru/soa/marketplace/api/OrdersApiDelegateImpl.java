package ru.soa.marketplace.api;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.soa.marketplace.generated.api.OrdersApiDelegate;
import ru.soa.marketplace.generated.model.OrderCreate;
import ru.soa.marketplace.generated.model.OrderResponse;
import ru.soa.marketplace.generated.model.OrderUpdate;
import ru.soa.marketplace.security.SecurityUtils;
import ru.soa.marketplace.service.OrderService;

import java.util.UUID;

@Service
public class OrdersApiDelegateImpl implements OrdersApiDelegate {
    private final OrderService orders;

    public OrdersApiDelegateImpl(OrderService orders) {
        this.orders = orders;
    }

    @Override
    public ResponseEntity<OrderResponse> createOrder(OrderCreate request) {
        return ResponseEntity.status(201).body(orders.create(request, SecurityUtils.currentUser()));
    }

    @Override
    public ResponseEntity<OrderResponse> getOrder(UUID id) {
        return ResponseEntity.ok(orders.get(id, SecurityUtils.currentUser()));
    }

    @Override
    public ResponseEntity<OrderResponse> updateOrder(UUID id, OrderUpdate request) {
        return ResponseEntity.ok(orders.update(id, request, SecurityUtils.currentUser()));
    }

    @Override
    public ResponseEntity<OrderResponse> cancelOrder(UUID id) {
        return ResponseEntity.ok(orders.cancel(id, SecurityUtils.currentUser()));
    }
}
