package ru.servermine.cities.api;

import java.util.Objects;
import java.util.UUID;

public record CityTaxRequest(UUID cityId, UUID actorId, long expectedRevision, boolean enabled, long dailyAmount) {
    public CityTaxRequest {
        Objects.requireNonNull(cityId, "cityId");
        Objects.requireNonNull(actorId, "actorId");
        if (expectedRevision < 0) throw new IllegalArgumentException("expectedRevision must not be negative");
    }
}
