package ru.servermine.cities.api;

import java.util.Objects;
import java.util.UUID;

public record CityTaxCollection(UUID playerId, String cityName, long amount, CityTreasuryCode result) {
    public CityTaxCollection {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(cityName, "cityName");
        Objects.requireNonNull(result, "result");
    }
}
