package ru.soa.marketplace.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.soa.marketplace.domain.Product;
import ru.soa.marketplace.domain.ProductStatus;
import ru.soa.marketplace.domain.Role;
import ru.soa.marketplace.generated.model.ProductCreate;
import ru.soa.marketplace.generated.model.ProductPage;
import ru.soa.marketplace.generated.model.ProductResponse;
import ru.soa.marketplace.generated.model.ProductUpdate;
import ru.soa.marketplace.repository.ProductRepository;
import ru.soa.marketplace.security.CurrentUser;
import ru.soa.marketplace.web.ApiException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ProductService {
    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    @Transactional
    public ProductResponse create(ProductCreate request, CurrentUser user) {
        UUID sellerId = user.role() == Role.ADMIN && request.getSellerId() != null
                ? request.getSellerId() : user.id();
        Product product = new Product(
                UUID.randomUUID(), request.getName(), request.getDescription(), money(request.getPrice()),
                request.getStock(), request.getCategory(), ProductStatus.valueOf(request.getStatus().getValue()), sellerId);
        return toResponse(products.saveAndFlush(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public ProductPage list(Integer page, Integer size,
                            ru.soa.marketplace.generated.model.ProductStatus status, String category) {
        Specification<Product> specification = Specification.where(null);
        if (status != null) {
            ProductStatus value = ProductStatus.valueOf(status.getValue());
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), value));
        }
        if (category != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("category"), category));
        }
        var result = products.findAll(specification, PageRequest.of(page, size));
        ProductPage response = new ProductPage();
        response.setItems(result.getContent().stream().map(this::toResponse).toList());
        response.setTotalElements(result.getTotalElements());
        response.setPage(result.getNumber());
        response.setSize(result.getSize());
        return response;
    }

    @Transactional
    public ProductResponse update(UUID id, ProductUpdate request, CurrentUser user) {
        Product product = products.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        checkOwner(product, user);
        UUID sellerId = user.role() == Role.ADMIN && request.getSellerId() != null
                ? request.getSellerId() : product.getSellerId();
        product.update(request.getName(), request.getDescription(), money(request.getPrice()), request.getStock(),
                request.getCategory(), ProductStatus.valueOf(request.getStatus().getValue()), sellerId);
        return toResponse(products.saveAndFlush(product));
    }

    @Transactional
    public void archive(UUID id, CurrentUser user) {
        Product product = products.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        checkOwner(product, user);
        product.archive();
        products.save(product);
    }

    private Product find(UUID id) {
        return products.findById(id).orElseThrow(() -> notFound(id));
    }

    private void checkOwner(Product product, CurrentUser user) {
        if (user.role() != Role.ADMIN && !product.getSellerId().equals(user.id())) {
            throw new ApiException(FORBIDDEN, "ACCESS_DENIED", "Продавец может менять только свои товары");
        }
    }

    private ApiException notFound(UUID id) {
        return new ApiException(NOT_FOUND, "PRODUCT_NOT_FOUND", "Товар не найден", java.util.Map.of("product_id", id));
    }

    public ProductResponse toResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setPrice(product.getPrice().doubleValue());
        response.setStock(product.getStock());
        response.setCategory(product.getCategory());
        response.setStatus(ru.soa.marketplace.generated.model.ProductStatus.fromValue(product.getStatus().name()));
        response.setSellerId(product.getSellerId());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        return response;
    }

    private BigDecimal money(Double value) {
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
