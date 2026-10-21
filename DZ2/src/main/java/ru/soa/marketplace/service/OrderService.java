package ru.soa.marketplace.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.soa.marketplace.domain.DiscountType;
import ru.soa.marketplace.domain.OperationType;
import ru.soa.marketplace.domain.Order;
import ru.soa.marketplace.domain.OrderItem;
import ru.soa.marketplace.domain.OrderStatus;
import ru.soa.marketplace.domain.Product;
import ru.soa.marketplace.domain.ProductStatus;
import ru.soa.marketplace.domain.PromoCode;
import ru.soa.marketplace.domain.Role;
import ru.soa.marketplace.domain.UserOperation;
import ru.soa.marketplace.generated.model.OrderCreate;
import ru.soa.marketplace.generated.model.OrderItemRequest;
import ru.soa.marketplace.generated.model.OrderItemResponse;
import ru.soa.marketplace.generated.model.OrderResponse;
import ru.soa.marketplace.generated.model.OrderUpdate;
import ru.soa.marketplace.repository.OrderRepository;
import ru.soa.marketplace.repository.ProductRepository;
import ru.soa.marketplace.repository.PromoCodeRepository;
import ru.soa.marketplace.repository.UserOperationRepository;
import ru.soa.marketplace.repository.UserRepository;
import ru.soa.marketplace.security.CurrentUser;
import ru.soa.marketplace.web.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

@Service
public class OrderService {
    private static final List<OrderStatus> ACTIVE = List.of(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING);

    private final OrderRepository orders;
    private final ProductRepository products;
    private final PromoCodeRepository promoCodes;
    private final UserOperationRepository operations;
    private final UserRepository users;
    private final Duration cooldown;

    public OrderService(OrderRepository orders, ProductRepository products, PromoCodeRepository promoCodes,
                        UserOperationRepository operations, UserRepository users,
                        @Value("${app.order.operation-cooldown}") Duration cooldown) {
        this.orders = orders;
        this.products = products;
        this.promoCodes = promoCodes;
        this.operations = operations;
        this.users = users;
        this.cooldown = cooldown;
    }

    @Transactional
    public OrderResponse create(OrderCreate request, CurrentUser user) {
        users.findByIdForUpdate(user.id()).orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "Пользователь не найден"));
        checkRateLimit(user.id(), OperationType.CREATE_ORDER);
        if (orders.existsByUserIdAndStatusIn(user.id(), ACTIVE)) {
            throw new ApiException(CONFLICT, "ORDER_HAS_ACTIVE", "У пользователя уже есть активный заказ");
        }

        Map<UUID, Integer> requested = normalize(request.getItems());
        Map<UUID, Product> lockedProducts = lockProducts(requested.keySet().stream().toList());
        validateAndReserve(requested, lockedProducts);

        Order order = new Order(UUID.randomUUID(), user.id());
        requested.forEach((productId, quantity) -> order.addItem(lockedProducts.get(productId), quantity));
        BigDecimal baseAmount = baseAmount(order.getItems());
        Discount discount = applyPromo(request.getPromoCode(), baseAmount, true);
        order.setAmounts(baseAmount.subtract(discount.amount()), discount.amount(), discount.promoCode());

        operations.save(new UserOperation(UUID.randomUUID(), user.id(), OperationType.CREATE_ORDER));
        return toResponse(orders.saveAndFlush(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id, CurrentUser user) {
        Order order = orders.findDetailedById(id).orElseThrow(() -> notFound(id));
        checkOwner(order, user);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse update(UUID id, OrderUpdate request, CurrentUser user) {
        Order order = orders.findDetailedByIdForUpdate(id).orElseThrow(() -> notFound(id));
        checkOwner(order, user);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw invalidState(order);
        }
        checkRateLimit(user.id(), OperationType.UPDATE_ORDER);

        Map<UUID, Integer> requested = normalize(request.getItems());
        List<UUID> allIds = new ArrayList<>(requested.keySet());
        order.getItems().stream().map(item -> item.getProduct().getId()).forEach(allIds::add);
        Map<UUID, Product> lockedProducts = lockProducts(allIds.stream().distinct().toList());

        order.getItems().forEach(item -> lockedProducts.get(item.getProduct().getId()).release(item.getQuantity()));
        validateAndReserve(requested, lockedProducts);

        List<OrderItem> newItems = requested.entrySet().stream()
                .map(entry -> new OrderItem(UUID.randomUUID(), order, lockedProducts.get(entry.getKey()),
                        entry.getValue(), lockedProducts.get(entry.getKey()).getPrice()))
                .toList();
        order.replaceItems(newItems);
        BigDecimal baseAmount = baseAmount(newItems);
        Discount discount = recalculateExistingPromo(order.getPromoCode(), baseAmount);
        order.setAmounts(baseAmount.subtract(discount.amount()), discount.amount(), discount.promoCode());

        operations.save(new UserOperation(UUID.randomUUID(), user.id(), OperationType.UPDATE_ORDER));
        return toResponse(orders.saveAndFlush(order));
    }

    @Transactional
    public OrderResponse cancel(UUID id, CurrentUser user) {
        Order order = orders.findDetailedByIdForUpdate(id).orElseThrow(() -> notFound(id));
        checkOwner(order, user);
        if (!ACTIVE.contains(order.getStatus())) {
            throw invalidState(order);
        }
        List<UUID> ids = order.getItems().stream().map(item -> item.getProduct().getId()).distinct().toList();
        Map<UUID, Product> lockedProducts = lockProducts(ids);
        order.getItems().forEach(item -> lockedProducts.get(item.getProduct().getId()).release(item.getQuantity()));
        if (order.getPromoCode() != null) {
            PromoCode promo = promoCodes.findByCodeForUpdate(order.getPromoCode().getCode()).orElseThrow();
            promo.releaseUse();
        }
        order.cancel();
        return toResponse(orders.saveAndFlush(order));
    }

    private Map<UUID, Integer> normalize(List<OrderItemRequest> items) {
        Map<UUID, Integer> result = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            if (result.putIfAbsent(item.getProductId(), item.getQuantity()) != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Один товар нельзя указать дважды", Map.of("product_id", item.getProductId()));
            }
        }
        return result;
    }

    private Map<UUID, Product> lockProducts(List<UUID> ids) {
        List<UUID> sorted = ids.stream().distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        return products.findAllByIdForUpdate(sorted).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private void validateAndReserve(Map<UUID, Integer> requested, Map<UUID, Product> locked) {
        for (UUID id : requested.keySet()) {
            Product product = locked.get(id);
            if (product == null) {
                throw new ApiException(NOT_FOUND, "PRODUCT_NOT_FOUND", "Товар не найден",
                        Map.of("product_id", id));
            }
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new ApiException(CONFLICT, "PRODUCT_INACTIVE", "Товар неактивен",
                        Map.of("product_id", id));
            }
        }
        List<Map<String, Object>> insufficient = requested.entrySet().stream()
                .filter(entry -> locked.get(entry.getKey()).getStock() < entry.getValue())
                .map(entry -> Map.<String, Object>of(
                        "product_id", entry.getKey(),
                        "requested", entry.getValue(),
                        "available", locked.get(entry.getKey()).getStock()))
                .toList();
        if (!insufficient.isEmpty()) {
            throw new ApiException(CONFLICT, "INSUFFICIENT_STOCK", "Недостаточно товара на складе",
                    Map.of("products", insufficient));
        }
        requested.forEach((id, quantity) -> locked.get(id).reserve(quantity));
    }

    private Discount applyPromo(String code, BigDecimal baseAmount, boolean incrementUse) {
        if (code == null) return new Discount(null, BigDecimal.ZERO);
        PromoCode promo = promoCodes.findByCodeForUpdate(code).orElseThrow(() -> promoInvalid(code));
        OffsetDateTime now = OffsetDateTime.now();
        if (!promo.isActive() || promo.getCurrentUses() >= promo.getMaxUses()
                || now.isBefore(promo.getValidFrom()) || now.isAfter(promo.getValidUntil())) {
            throw promoInvalid(code);
        }
        if (baseAmount.compareTo(promo.getMinOrderAmount()) < 0) {
            throw new ApiException(UNPROCESSABLE_ENTITY, "PROMO_CODE_MIN_AMOUNT",
                    "Сумма заказа меньше минимальной для промокода",
                    Map.of("minimum", promo.getMinOrderAmount(), "actual", baseAmount));
        }
        BigDecimal amount = discountAmount(promo, baseAmount);
        if (incrementUse) promo.use();
        return new Discount(promo, amount);
    }

    private Discount recalculateExistingPromo(PromoCode currentPromo, BigDecimal baseAmount) {
        if (currentPromo == null) return new Discount(null, BigDecimal.ZERO);
        PromoCode promo = promoCodes.findByCodeForUpdate(currentPromo.getCode())
                .orElseThrow(() -> promoInvalid(currentPromo.getCode()));
        OffsetDateTime now = OffsetDateTime.now();
        if (!promo.isActive() || now.isBefore(promo.getValidFrom()) || now.isAfter(promo.getValidUntil())) {
            throw promoInvalid(promo.getCode());
        }
        if (baseAmount.compareTo(promo.getMinOrderAmount()) < 0) {
            promo.releaseUse();
            return new Discount(null, BigDecimal.ZERO);
        }
        return new Discount(promo, discountAmount(promo, baseAmount));
    }

    private BigDecimal discountAmount(PromoCode promo, BigDecimal baseAmount) {
        BigDecimal discount;
        if (promo.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = baseAmount.multiply(promo.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            discount = discount.min(baseAmount.multiply(BigDecimal.valueOf(0.70)));
        } else {
            discount = promo.getDiscountValue().min(baseAmount);
        }
        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal baseAmount(List<OrderItem> items) {
        return items.stream()
                .map(item -> item.getPriceAtOrder().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void checkRateLimit(UUID userId, OperationType type) {
        operations.findFirstByUserIdAndOperationTypeOrderByCreatedAtDesc(userId, type)
                .filter(operation -> operation.getCreatedAt().plus(cooldown).isAfter(OffsetDateTime.now()))
                .ifPresent(operation -> {
                    throw new ApiException(TOO_MANY_REQUESTS, "ORDER_LIMIT_EXCEEDED",
                            "Операции с заказами выполняются слишком часто");
                });
    }

    private void checkOwner(Order order, CurrentUser user) {
        if (user.role() != Role.ADMIN && !order.getUserId().equals(user.id())) {
            throw new ApiException(FORBIDDEN, "ORDER_OWNERSHIP_VIOLATION", "Заказ принадлежит другому пользователю");
        }
    }

    private ApiException invalidState(Order order) {
        return new ApiException(CONFLICT, "INVALID_STATE_TRANSITION", "Операция недоступна в текущем состоянии",
                Map.of("status", order.getStatus()));
    }

    private ApiException notFound(UUID id) {
        return new ApiException(NOT_FOUND, "ORDER_NOT_FOUND", "Заказ не найден", Map.of("order_id", id));
    }

    private ApiException promoInvalid(String code) {
        return new ApiException(UNPROCESSABLE_ENTITY, "PROMO_CODE_INVALID", "Промокод недействителен",
                Map.of("promo_code", code));
    }

    public OrderResponse toResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setUserId(order.getUserId());
        response.setStatus(ru.soa.marketplace.generated.model.OrderStatus.fromValue(order.getStatus().name()));
        response.setPromoCode(order.getPromoCode() == null ? null : order.getPromoCode().getCode());
        response.setTotalAmount(order.getTotalAmount().doubleValue());
        response.setDiscountAmount(order.getDiscountAmount().doubleValue());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        response.setItems(order.getItems().stream().map(item -> {
            OrderItemResponse result = new OrderItemResponse();
            result.setProductId(item.getProduct().getId());
            result.setQuantity(item.getQuantity());
            result.setPriceAtOrder(item.getPriceAtOrder().doubleValue());
            return result;
        }).toList());
        return response;
    }

    private record Discount(PromoCode promoCode, BigDecimal amount) {}
}
