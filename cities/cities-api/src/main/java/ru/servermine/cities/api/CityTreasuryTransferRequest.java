package ru.servermine.cities.api;

import java.util.Objects;
import java.util.UUID;

public record CityTreasuryTransferRequest(UUID operationId, UUID actorId, UUID targetCityId, long amount) {
    public CityTreasuryTransferRequest {
        Objects.requireNonNull(operationId); Objects.requireNonNull(actorId); Objects.requireNonNull(targetCityId);
        if (amount <= 0) throw new IllegalArgumentException("Transfer amount must be positive");
    }
}
