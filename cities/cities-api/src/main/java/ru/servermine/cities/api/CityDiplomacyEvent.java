package ru.servermine.cities.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CityDiplomacyEvent(UUID id, UUID treatyId, String firstCity, String secondCity,
                                 String event, Instant occurredAt) {
    public CityDiplomacyEvent {
        Objects.requireNonNull(id); Objects.requireNonNull(treatyId); Objects.requireNonNull(firstCity);
        Objects.requireNonNull(secondCity); Objects.requireNonNull(event); Objects.requireNonNull(occurredAt);
    }
}
