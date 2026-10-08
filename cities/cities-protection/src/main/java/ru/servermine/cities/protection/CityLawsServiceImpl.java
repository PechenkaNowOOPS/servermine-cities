package ru.servermine.cities.protection;

import ru.servermine.cities.api.CityLawsRequest;
import ru.servermine.cities.api.CityLawsResult;
import ru.servermine.cities.api.CityLawsService;
import ru.servermine.cities.api.CityLawsCode;
import ru.servermine.cities.core.CityRepository;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.concurrent.CompletionStage;

/** Persists rule changes and refreshes the main-thread protection snapshot. */
public final class CityLawsServiceImpl implements CityLawsService {
    private final CityRepository repository;
    private final Consumer<ru.servermine.cities.api.CityView> cityChanged;
    private volatile boolean ready = true;

    public CityLawsServiceImpl(CityRepository repository, Consumer<ru.servermine.cities.api.CityView> cityChanged) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cityChanged = Objects.requireNonNull(cityChanged, "cityChanged");
    }

    @Override public boolean isReady() { return ready; }

    @Override public CompletionStage<CityLawsResult> update(CityLawsRequest request) {
        if (!ready) return java.util.concurrent.CompletableFuture.completedFuture(
                new CityLawsResult(CityLawsCode.INTERNAL_ERROR, java.util.Optional.empty()));
        return repository.updateCityLaws(request).thenApply(result -> {
            result.city().ifPresent(cityChanged);
            return result;
        });
    }

    public void deactivate() { ready = false; }
}
