package ru.servermine.cities.api;

import java.util.Objects;
import java.util.UUID;

public record CityDiplomacyRequest(UUID treatyId, UUID cityId, UUID actorId, Action action,
                                   UUID targetCityId, CityTreatyType type, int durationDays) {
    public enum Action { PROPOSE, ACCEPT, DECLINE, CANCEL, TERMINATE }
    public CityDiplomacyRequest {
        Objects.requireNonNull(treatyId); Objects.requireNonNull(cityId); Objects.requireNonNull(actorId);
        Objects.requireNonNull(action);
        if (durationDays < 1 || durationDays > 90) throw new IllegalArgumentException("Duration must be 1..90 days");
        if (action == Action.PROPOSE) { Objects.requireNonNull(targetCityId); Objects.requireNonNull(type); }
    }
}
