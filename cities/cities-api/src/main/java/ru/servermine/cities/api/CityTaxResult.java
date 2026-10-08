package ru.servermine.cities.api;

import java.util.Objects;
import java.util.Optional;

public record CityTaxResult(CityTaxCode code, Optional<CityView> city) {
    public CityTaxResult {
        Objects.requireNonNull(code, "code");
        city = Objects.requireNonNull(city, "city");
        if ((code == CityTaxCode.UPDATED) != city.isPresent())
            throw new IllegalArgumentException("Successful tax updates contain the updated city");
    }
}
