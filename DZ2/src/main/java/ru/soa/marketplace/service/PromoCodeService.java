package ru.soa.marketplace.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.soa.marketplace.domain.DiscountType;
import ru.soa.marketplace.domain.PromoCode;
import ru.soa.marketplace.generated.model.PromoCodeCreate;
import ru.soa.marketplace.generated.model.PromoCodeResponse;
import ru.soa.marketplace.repository.PromoCodeRepository;
import ru.soa.marketplace.security.CurrentUser;
import ru.soa.marketplace.web.ApiException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PromoCodeService {
    private final PromoCodeRepository promoCodes;

    public PromoCodeService(PromoCodeRepository promoCodes) {
        this.promoCodes = promoCodes;
    }

    @Transactional
    public PromoCodeResponse create(PromoCodeCreate request, CurrentUser user) {
        if (promoCodes.existsByCode(request.getCode())) {
            throw new ApiException(HttpStatus.CONFLICT, "PROMO_CODE_ALREADY_EXISTS", "Промокод уже существует");
        }
        if (!request.getValidUntil().isAfter(request.getValidFrom())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Дата окончания должна быть позже даты начала");
        }
        PromoCode promoCode = new PromoCode(
                UUID.randomUUID(), request.getCode(), DiscountType.valueOf(request.getDiscountType().getValue()),
                money(request.getDiscountValue()), money(request.getMinOrderAmount()), request.getMaxUses(),
                request.getValidFrom(), request.getValidUntil(), !Boolean.FALSE.equals(request.getActive()), user.id());
        promoCodes.saveAndFlush(promoCode);
        return toResponse(promoCode);
    }

    public PromoCodeResponse toResponse(PromoCode promoCode) {
        PromoCodeResponse response = new PromoCodeResponse();
        response.setId(promoCode.getId());
        response.setCode(promoCode.getCode());
        response.setDiscountType(ru.soa.marketplace.generated.model.DiscountType.fromValue(
                promoCode.getDiscountType().name()));
        response.setDiscountValue(promoCode.getDiscountValue().doubleValue());
        response.setMinOrderAmount(promoCode.getMinOrderAmount().doubleValue());
        response.setMaxUses(promoCode.getMaxUses());
        response.setCurrentUses(promoCode.getCurrentUses());
        response.setValidFrom(promoCode.getValidFrom());
        response.setValidUntil(promoCode.getValidUntil());
        response.setActive(promoCode.isActive());
        return response;
    }

    private BigDecimal money(Double value) {
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
