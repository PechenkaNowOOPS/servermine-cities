package ru.servermine.cities.api;

import java.util.concurrent.CompletionStage;

public interface CityLawsService {
    boolean isReady();
    CompletionStage<CityLawsResult> update(CityLawsRequest request);
}
