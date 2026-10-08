package ru.servermine.cities.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CityTreasuryTransferResult(UUID operationId, CityTreasuryTransferCode code,
                                         Optional<CityView> sourceCity, Optional<CityView> targetCity) {
    public CityTreasuryTransferResult {
        Objects.requireNonNull(operationId); Objects.requireNonNull(code);
        sourceCity = Objects.requireNonNull(sourceCity); targetCity = Objects.requireNonNull(targetCity);
    }
}
