package ru.servermine.cities.treasury;

import ru.servermine.cities.api.CityTaxCode;
import ru.servermine.cities.api.CityTaxCollection;
import ru.servermine.cities.api.CityTaxRequest;
import ru.servermine.cities.api.CityTaxResult;
import ru.servermine.cities.api.CityTaxService;
import ru.servermine.cities.api.CityTreasuryCode;
import ru.servermine.cities.api.CityView;
import ru.servermine.cities.core.CityRepository;
import ru.servermine.cities.core.CityTaxDue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Daily online-only tax collection, using the treasury deposit saga and stable operation ids. */
public final class CityTaxServiceImpl implements CityTaxService {
    private final CityRepository repository;
    private final CityTreasuryServiceImpl treasury;
    private final Consumer<CityView> cityChanged;
    private final AtomicBoolean collecting = new AtomicBoolean();
    private volatile boolean active = true;

    public CityTaxServiceImpl(CityRepository repository, CityTreasuryServiceImpl treasury,
                              Consumer<CityView> cityChanged) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.treasury = Objects.requireNonNull(treasury, "treasury");
        this.cityChanged = Objects.requireNonNull(cityChanged, "cityChanged");
    }

    @Override public boolean isReady() { return active && treasury.isReady(); }

    @Override public CompletionStage<CityTaxResult> update(CityTaxRequest request) {
        if (!isReady()) return CompletableFuture.completedFuture(new CityTaxResult(CityTaxCode.INTERNAL_ERROR, java.util.Optional.empty()));
        if (collecting.get()) return CompletableFuture.completedFuture(
                new CityTaxResult(CityTaxCode.COLLECTION_IN_PROGRESS, java.util.Optional.empty()));
        return repository.updateCityTaxPolicy(request).thenApply(result -> {
            result.city().ifPresent(cityChanged);
            return result;
        });
    }

    @Override public CompletionStage<List<CityTaxCollection>> collectDue(Set<UUID> onlineResidents) {
        if (!isReady() || !collecting.compareAndSet(false, true)) return CompletableFuture.completedFuture(List.of());
        Set<UUID> online = Set.copyOf(onlineResidents);
        return repository.dueCityTaxes(Instant.now()).thenCompose(cities -> {
            CompletionStage<List<CityTaxCollection>> chain = CompletableFuture.completedFuture(new ArrayList<>());
            for (CityTaxDue city : cities) {
                Instant cycle = city.cycle();
                List<CityView.ResidentView> payers = city.residents().stream()
                        .filter(resident -> online.contains(resident.playerId())).toList();
                chain = chain.thenCompose(results -> collectResidents(city, cycle, payers, results)
                        .thenCompose(unresolved -> {
                            if (unresolved) return CompletableFuture.completedFuture(results);
                            return repository.completeCityTaxCycle(city.cityId(), cycle, Instant.now().plusSeconds(86_400))
                                    .thenApply(ignored -> results);
                        }));
            }
            return chain.thenApply(List::copyOf);
        }).whenComplete((ignored, error) -> collecting.set(false));
    }

    private CompletionStage<Boolean> collectResidents(CityTaxDue city, Instant cycle,
                                                       List<CityView.ResidentView> payers,
                                                       List<CityTaxCollection> results) {
        CompletionStage<Boolean> chain = CompletableFuture.completedFuture(false);
        for (CityView.ResidentView resident : payers) {
            UUID operationId = UUID.nameUUIDFromBytes(("ServerMineCities:tax:" + city.cityId() + ":" + cycle + ":"
                    + resident.playerId()).getBytes(StandardCharsets.UTF_8));
            chain = chain.thenCompose(unresolved -> {
                if (unresolved) return CompletableFuture.completedFuture(true);
                return treasury.collectTax(operationId, city.cityId(), resident.playerId(), city.dailyAmount())
                        .thenApply(result -> {
                            results.add(new CityTaxCollection(resident.playerId(), city.cityName(),
                                    city.dailyAmount(), result.code()));
                            return result.code() == CityTreasuryCode.UNKNOWN_OUTCOME
                                    || result.code() == CityTreasuryCode.OPERATION_IN_PROGRESS
                                    || result.code() == CityTreasuryCode.SERVICE_UNAVAILABLE
                                    || result.code() == CityTreasuryCode.INTERNAL_ERROR;
                        });
            });
        }
        return chain;
    }

    public void deactivate() { active = false; }
}
