package ru.servermine.cities.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface CityDiplomacyService {
    boolean isReady();
    CompletionStage<Optional<CityDiplomacySnapshot>> snapshot(UUID playerId);
    CompletionStage<CityDiplomacyResult> execute(CityDiplomacyRequest request);
}
