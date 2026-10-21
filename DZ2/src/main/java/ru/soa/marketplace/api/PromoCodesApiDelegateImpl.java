package ru.soa.marketplace.api;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.soa.marketplace.generated.api.PromoCodesApiDelegate;
import ru.soa.marketplace.generated.model.PromoCodeCreate;
import ru.soa.marketplace.generated.model.PromoCodeResponse;
import ru.soa.marketplace.security.SecurityUtils;
import ru.soa.marketplace.service.PromoCodeService;

@Service
public class PromoCodesApiDelegateImpl implements PromoCodesApiDelegate {
    private final PromoCodeService promoCodes;

    public PromoCodesApiDelegateImpl(PromoCodeService promoCodes) {
        this.promoCodes = promoCodes;
    }

    @Override
    public ResponseEntity<PromoCodeResponse> createPromoCode(PromoCodeCreate request) {
        return ResponseEntity.status(201).body(promoCodes.create(request, SecurityUtils.currentUser()));
    }
}
