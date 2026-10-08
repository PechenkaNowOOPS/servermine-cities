package ru.servermine.cities.api;

import java.util.Objects;
import java.util.Optional;

public record CityLawsResult(CityLawsCode code, Optional<CityView> city) {
    public CityLawsResult {
        Objects.requireNonNull(code, "code");
        city = Objects.requireNonNull(city, "city");
        if ((code == CityLawsCode.UPDATED) != city.isPresent())
            throw new IllegalArgumentException("Successful law updates contain the updated city");
    }
}
