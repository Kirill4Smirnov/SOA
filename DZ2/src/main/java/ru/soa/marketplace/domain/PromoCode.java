package ru.soa.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "promo_codes")
public class PromoCode {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 20)
    private String code;
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;
    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;
    @Column(name = "min_order_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal minOrderAmount;
    @Column(name = "max_uses", nullable = false)
    private int maxUses;
    @Column(name = "current_uses", nullable = false)
    private int currentUses;
    @Column(name = "valid_from", nullable = false)
    private OffsetDateTime validFrom;
    @Column(name = "valid_until", nullable = false)
    private OffsetDateTime validUntil;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    protected PromoCode() {}

    public PromoCode(UUID id, String code, DiscountType discountType, BigDecimal discountValue,
                     BigDecimal minOrderAmount, int maxUses, OffsetDateTime validFrom,
                     OffsetDateTime validUntil, boolean active, UUID createdBy) {
        this.id = id;
        this.code = code;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.maxUses = maxUses;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.active = active;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public DiscountType getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public int getMaxUses() { return maxUses; }
    public int getCurrentUses() { return currentUses; }
    public OffsetDateTime getValidFrom() { return validFrom; }
    public OffsetDateTime getValidUntil() { return validUntil; }
    public boolean isActive() { return active; }
    public void use() { currentUses++; }
    public void releaseUse() { if (currentUses > 0) currentUses--; }
}
