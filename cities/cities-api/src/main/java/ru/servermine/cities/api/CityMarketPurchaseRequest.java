package ru.servermine.cities.api;

import java.util.Objects;
import java.util.UUID;

public record CityMarketPurchaseRequest(UUID operationId, UUID buyerId, UUID listingId) {
    public CityMarketPurchaseRequest {
        Objects.requireNonNull(operationId); Objects.requireNonNull(buyerId); Objects.requireNonNull(listingId);
    }
}
