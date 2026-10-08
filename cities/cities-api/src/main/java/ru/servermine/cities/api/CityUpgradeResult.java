package ru.servermine.cities.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CityUpgradeResult(UUID operationId, CityUpgradeCode code, Optional<CityView> city,
                                boolean idempotentReplay) {
    public CityUpgradeResult {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(code, "code");
        city = Objects.requireNonNull(city, "city");
        if ((code == CityUpgradeCode.PURCHASED || code == CityUpgradeCode.REPLAYED) != city.isPresent()) {
            throw new IllegalArgumentException("Successful upgrade results must contain the updated city view");
        }
    }
}
