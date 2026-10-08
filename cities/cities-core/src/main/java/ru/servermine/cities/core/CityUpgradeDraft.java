package ru.servermine.cities.core;

import ru.servermine.cities.api.CityStage;
import ru.servermine.cities.api.CityUpgradeId;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable database purchase intent; treasury debit and unlock commit together. */
public record CityUpgradeDraft(UUID operationId, UUID cityId, UUID actorId, long expectedRevision,
                               CityUpgradeId upgradeId, CityStage minimumStage, long price,
                               Set<CityUpgradeId> prerequisites) {
    public CityUpgradeDraft {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(cityId, "cityId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(upgradeId, "upgradeId");
        Objects.requireNonNull(minimumStage, "minimumStage");
        prerequisites = Set.copyOf(prerequisites);
        if (expectedRevision < 1 || price < 0 || prerequisites.contains(upgradeId)) {
            throw new IllegalArgumentException("Invalid city upgrade purchase");
        }
    }
}
