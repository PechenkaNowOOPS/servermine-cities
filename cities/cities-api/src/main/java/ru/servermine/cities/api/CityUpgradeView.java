package ru.servermine.cities.api;

import java.util.Objects;
import java.util.Set;

/** Immutable configured upgrade definition. */
public record CityUpgradeView(CityUpgradeId id, boolean enabled, long price, CityStage minimumStage,
                              Set<CityUpgradeId> prerequisites) {
    public CityUpgradeView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(minimumStage, "minimumStage");
        prerequisites = Set.copyOf(prerequisites);
        if (price < 0 || prerequisites.contains(id)) throw new IllegalArgumentException("Invalid upgrade definition");
    }
}
