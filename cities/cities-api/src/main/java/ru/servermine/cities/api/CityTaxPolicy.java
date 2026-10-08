package ru.servermine.cities.api;

import java.time.Instant;

/** A flat daily charge per resident; offline residents are skipped without arrears. */
public record CityTaxPolicy(boolean enabled, long dailyAmount, Instant nextCollectionAt) {
    public static final CityTaxPolicy DEFAULT = new CityTaxPolicy(false, 0, null);
    public CityTaxPolicy {
        if (dailyAmount < 0 || dailyAmount > 100_000) throw new IllegalArgumentException("Tax must be 0..100000");
        if (enabled && (dailyAmount == 0 || nextCollectionAt == null))
            throw new IllegalArgumentException("Enabled tax needs an amount and next collection time");
    }
}
