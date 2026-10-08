package ru.servermine.cities.api;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.UUID;

public interface CityTaxService {
    boolean isReady();
    CompletionStage<CityTaxResult> update(CityTaxRequest request);
    CompletionStage<List<CityTaxCollection>> collectDue(Set<UUID> onlineResidents);
}
