package com.adags.modules.billing.domain;

import java.util.Optional;

public interface PriceListRepository {

    Optional<PriceList> findByPayerCategory(PayerCategory payerCategory);
}
