package ru.servermine.cities.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Persistent city upgrade catalog and treasury-backed purchases. */
public interface CityUpgradeService {
    boolean isReady();
    Optional<CityUpgradeView> upgrade(CityUpgradeId id);
    CompletionStage<CityUpgradeResult> purchase(CityUpgradeRequest request);
    CompletionStage<Optional<CityView>> cityForPlayer(UUID playerId);
}
