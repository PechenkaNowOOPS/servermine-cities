package ru.servermine.cities.upgrades;

import ru.servermine.cities.api.CityStage;
import ru.servermine.cities.api.CityUpgradeId;
import ru.servermine.cities.api.CityUpgradeView;

import java.util.Set;

public record CityUpgradePolicy(boolean enabled, long price, CityStage minimumStage,
                                Set<CityUpgradeId> prerequisites) {
    public CityUpgradePolicy {
        if (price < 0 || minimumStage == null) throw new IllegalArgumentException("Invalid city upgrade policy");
        prerequisites = Set.copyOf(prerequisites);
    }

    public CityUpgradeView view(CityUpgradeId id) {
        return new CityUpgradeView(id, enabled, price, minimumStage, prerequisites);
    }
}
