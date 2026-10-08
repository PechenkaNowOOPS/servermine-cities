package ru.servermine.cities.api;

import java.util.Objects;
import java.util.Optional;

public record CityDiplomacyResult(CityDiplomacyCode code, Optional<CityTreatyView> treaty) {
    public CityDiplomacyResult { Objects.requireNonNull(code); treaty = Objects.requireNonNull(treaty); }
    public static CityDiplomacyResult of(CityDiplomacyCode code) { return new CityDiplomacyResult(code, Optional.empty()); }
}
