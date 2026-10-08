package ru.servermine.cities.diplomacy;

import ru.servermine.cities.api.*;
import ru.servermine.cities.core.CityRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Diplomacy facade; all authority checks and state transitions live in the repository transaction. */
public final class CityDiplomacyServiceImpl implements CityDiplomacyService {
    private final CityRepository repository;
    private final Consumer<CityTreatyView> treatyChanged;
    private final AtomicBoolean ready = new AtomicBoolean(true);

    public CityDiplomacyServiceImpl(CityRepository repository) { this(repository, ignored -> { }); }
    public CityDiplomacyServiceImpl(CityRepository repository, Consumer<CityTreatyView> treatyChanged) {
        this.repository = repository; this.treatyChanged = treatyChanged;
    }
    @Override public boolean isReady() { return ready.get(); }

    @Override public CompletionStage<Optional<CityDiplomacySnapshot>> snapshot(UUID playerId) {
        if (!isReady()) return CompletableFuture.failedFuture(new IllegalStateException("Diplomacy is unavailable"));
        return repository.findForPlayer(playerId).thenCompose(city -> {
            if (city.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
            UUID id = city.get().id();
            var treaties = repository.diplomacyTreaties(id);
            var history = repository.diplomacyHistory(id, 9);
            var cities = repository.findAllCities();
            return CompletableFuture.allOf(treaties.toCompletableFuture(), history.toCompletableFuture(),
                    cities.toCompletableFuture()).thenApply(ignored -> {
                List<CityDiplomacySnapshot.CitySummary> others = cities.toCompletableFuture().join().stream()
                        .filter(other -> !other.id().equals(id)).sorted(Comparator.comparing(CityView::name))
                        .map(other -> new CityDiplomacySnapshot.CitySummary(other.id(), other.name(), other.stage())).toList();
                List<CityTreatyView> currentTreaties = treaties.toCompletableFuture().join();
                currentTreaties.forEach(treatyChanged);
                return Optional.of(new CityDiplomacySnapshot(currentTreaties, history.toCompletableFuture().join(), others));
            });
        });
    }

    @Override public CompletionStage<CityDiplomacyResult> execute(CityDiplomacyRequest request) {
        if (!isReady()) return CompletableFuture.completedFuture(CityDiplomacyResult.of(CityDiplomacyCode.NOT_FOUND));
        return repository.executeDiplomacy(request).thenApply(result -> {
            result.treaty().ifPresent(treatyChanged);
            return result;
        });
    }

    public void deactivate() { ready.set(false); }
}
