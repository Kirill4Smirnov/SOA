package ru.soa.marketplace.api;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.soa.marketplace.generated.api.ProductsApiDelegate;
import ru.soa.marketplace.generated.model.ProductCreate;
import ru.soa.marketplace.generated.model.ProductPage;
import ru.soa.marketplace.generated.model.ProductResponse;
import ru.soa.marketplace.generated.model.ProductStatus;
import ru.soa.marketplace.generated.model.ProductUpdate;
import ru.soa.marketplace.security.SecurityUtils;
import ru.soa.marketplace.service.ProductService;

import java.util.UUID;

@Service
public class ProductsApiDelegateImpl implements ProductsApiDelegate {
    private final ProductService products;

    public ProductsApiDelegateImpl(ProductService products) {
        this.products = products;
    }

    @Override
    public ResponseEntity<ProductResponse> createProduct(ProductCreate request) {
        return ResponseEntity.status(201).body(products.create(request, SecurityUtils.currentUser()));
    }

    @Override
    public ResponseEntity<ProductResponse> getProduct(UUID id) {
        return ResponseEntity.ok(products.get(id));
    }

    @Override
    public ResponseEntity<ProductPage> listProducts(Integer page, Integer size, ProductStatus status, String category) {
        return ResponseEntity.ok(products.list(page, size, status, category));
    }

    @Override
    public ResponseEntity<ProductResponse> updateProduct(UUID id, ProductUpdate request) {
        return ResponseEntity.ok(products.update(id, request, SecurityUtils.currentUser()));
    }

    @Override
    public ResponseEntity<Void> archiveProduct(UUID id) {
        products.archive(id, SecurityUtils.currentUser());
        return ResponseEntity.noContent().build();
    }
}
