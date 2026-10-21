package ru.soa.marketplace.domain;

public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAID,
    SHIPPED,
    COMPLETED,
    CANCELED
}
