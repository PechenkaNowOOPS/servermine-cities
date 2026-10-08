package ru.servermine.cities.api;

import java.util.Objects;
import java.util.UUID;

public record CityLawsRequest(UUID cityId, UUID actorId, long expectedRevision, CityLaws laws) {
    public CityLawsRequest {
        Objects.requireNonNull(cityId, "cityId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(laws, "laws");
        if (expectedRevision < 0) throw new IllegalArgumentException("expectedRevision must not be negative");
    }
}
