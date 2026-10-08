package ru.servermine.cities.api;

import java.util.Objects;
import java.util.UUID;

/** Stable purchase intent validated against the current city revision. */
public record CityUpgradeRequest(UUID operationId, UUID cityId, UUID actorId, long expectedRevision,
                                 CityUpgradeId upgradeId) {
    public CityUpgradeRequest {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(cityId, "cityId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(upgradeId, "upgradeId");
        if (expectedRevision < 1) throw new IllegalArgumentException("expectedRevision must be positive");
    }
}
