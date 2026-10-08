package ru.servermine.cities.upgrades;

import ru.servermine.cities.api.CityUpgradeCode;
import ru.servermine.cities.api.CityUpgradeId;
import ru.servermine.cities.api.CityUpgradeRequest;
import ru.servermine.cities.api.CityUpgradeResult;
import ru.servermine.cities.api.CityUpgradeService;
import ru.servermine.cities.api.CityUpgradeView;
import ru.servermine.cities.api.CityView;
import ru.servermine.cities.core.CityRepository;
import ru.servermine.cities.core.CityUpgradeDraft;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Purchases persisted feature unlocks using the same atomic city-treasury transaction as the unlock. */
public final class CityUpgradeServiceImpl implements CityUpgradeService {
    private final CityRepository repository;
    private final Map<CityUpgradeId, CityUpgradePolicy> policies;
    private final Consumer<CityView> cityChanged;
    private volatile boolean ready = true;

    public CityUpgradeServiceImpl(CityRepository repository, Map<CityUpgradeId, CityUpgradePolicy> policies,
                                  Consumer<CityView> cityChanged) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.policies = Map.copyOf(policies);
        this.cityChanged = Objects.requireNonNull(cityChanged, "cityChanged");
    }

    public void deactivate() { ready = false; }
    @Override public boolean isReady() { return ready; }

    @Override public Optional<CityUpgradeView> upgrade(CityUpgradeId id) {
        CityUpgradePolicy policy = policies.get(Objects.requireNonNull(id, "id"));
        return policy == null ? Optional.empty() : Optional.of(policy.view(id));
    }

    @Override public CompletionStage<Optional<CityView>> cityForPlayer(UUID playerId) {
        if (!ready) return CompletableFuture.failedFuture(new IllegalStateException("Upgrade service unavailable"));
        return repository.findForPlayer(Objects.requireNonNull(playerId, "playerId"));
    }

    @Override public CompletionStage<CityUpgradeResult> purchase(CityUpgradeRequest request) {
        Objects.requireNonNull(request, "request");
        if (!ready) return result(request.operationId(), CityUpgradeCode.SERVICE_UNAVAILABLE);
        CityUpgradePolicy policy = policies.get(request.upgradeId());
        if (policy == null || !policy.enabled()) return result(request.operationId(), CityUpgradeCode.UPGRADE_DISABLED);
        CityUpgradeDraft draft = new CityUpgradeDraft(request.operationId(), request.cityId(), request.actorId(),
                request.expectedRevision(), request.upgradeId(), policy.minimumStage(), policy.price(), policy.prerequisites());
        return repository.purchaseUpgrade(draft).thenApply(result -> {
            if (result.code() == CityUpgradeCode.PURCHASED || result.code() == CityUpgradeCode.REPLAYED) {
                result.city().ifPresent(cityChanged);
            }
            return result;
        }).exceptionally(error -> resultNow(request.operationId(), CityUpgradeCode.INTERNAL_ERROR));
    }

    private CompletionStage<CityUpgradeResult> result(UUID id, CityUpgradeCode code) {
        return CompletableFuture.completedFuture(resultNow(id, code));
    }

    private CityUpgradeResult resultNow(UUID id, CityUpgradeCode code) {
        return new CityUpgradeResult(id, code, Optional.empty(), false);
    }
}
