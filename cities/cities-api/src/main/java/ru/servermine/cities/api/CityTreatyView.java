package ru.servermine.cities.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CityTreatyView(UUID id, UUID proposingCityId, String proposingCityName,
                             UUID targetCityId, String targetCityName, CityTreatyType type,
                             CityTreatyState state, Instant createdAt, Instant expiresAt) {
    public CityTreatyView {
        Objects.requireNonNull(id); Objects.requireNonNull(proposingCityId); Objects.requireNonNull(targetCityId);
        Objects.requireNonNull(proposingCityName); Objects.requireNonNull(targetCityName);
        Objects.requireNonNull(type); Objects.requireNonNull(state); Objects.requireNonNull(createdAt);
        Objects.requireNonNull(expiresAt);
    }
}
