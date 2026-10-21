package ru.soa.marketplace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.soa.marketplace.domain.Product;
import ru.soa.marketplace.domain.ProductStatus;
import ru.soa.marketplace.domain.Role;
import ru.soa.marketplace.repository.ProductRepository;
import ru.soa.marketplace.security.CurrentUser;
import ru.soa.marketplace.service.ProductService;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock
    ProductRepository repository;

    @Test
    void deleteArchivesProductInsteadOfRemovingIt() {
        UUID sellerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Product product = new Product(productId, "Keyboard", null, BigDecimal.TEN, 3,
                "electronics", ProductStatus.ACTIVE, sellerId);
        when(repository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        new ProductService(repository).archive(productId, new CurrentUser(sellerId, "seller", Role.SELLER));

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ARCHIVED);
        verify(repository).save(product);
    }
}
