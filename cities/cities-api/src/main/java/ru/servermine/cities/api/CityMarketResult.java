package ru.servermine.cities.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CityMarketResult(UUID operationId, CityMarketCode code, Optional<CityMarketListing> listing,
                               Optional<CityView> buyerCity, Optional<CityView> sellerCity) {
    public CityMarketResult {
        Objects.requireNonNull(operationId); Objects.requireNonNull(code);
        listing = Objects.requireNonNull(listing); buyerCity = Objects.requireNonNull(buyerCity);
        sellerCity = Objects.requireNonNull(sellerCity);
    }
}
