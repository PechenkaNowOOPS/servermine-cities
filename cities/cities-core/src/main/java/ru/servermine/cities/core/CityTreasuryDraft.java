package ru.servermine.cities.core;

import java.util.Objects;
import java.util.UUID;

public record CityTreasuryDraft(UUID operationId, UUID cityId, UUID actorId, long amount, String reason) {
    public CityTreasuryDraft(UUID operationId, UUID cityId, UUID actorId, long amount) {
        this(operationId, cityId, actorId, amount, "DEPOSIT");
    }
    public CityTreasuryDraft {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(cityId, "cityId");
        Objects.requireNonNull(actorId, "actorId");
        if (!"DEPOSIT".equals(reason) && !"CITY_TAX".equals(reason))
            throw new IllegalArgumentException("Unsupported treasury deposit reason");
        if (amount <= 0) throw new IllegalArgumentException("Deposit amount must be positive");
    }
}
