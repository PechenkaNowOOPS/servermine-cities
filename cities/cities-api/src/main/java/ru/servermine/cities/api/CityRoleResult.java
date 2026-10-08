package ru.servermine.cities.api;

import java.util.Objects;
import java.util.Optional;

public record CityRoleResult(CityRoleCode code, Optional<CityView> city) {
    public CityRoleResult {
        Objects.requireNonNull(code, "code");
        city = Objects.requireNonNull(city, "city");
        if ((code == CityRoleCode.ROLE_CHANGED) != city.isPresent()) {
            throw new IllegalArgumentException("Successful role changes contain the updated city");
        }
    }
}
