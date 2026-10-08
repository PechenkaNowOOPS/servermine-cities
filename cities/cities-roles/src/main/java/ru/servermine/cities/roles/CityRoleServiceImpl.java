package ru.servermine.cities.roles;

import ru.servermine.cities.api.CityRole;
import ru.servermine.cities.api.CityRoleCode;
import ru.servermine.cities.api.CityRoleResult;
import ru.servermine.cities.api.CityRoleService;
import ru.servermine.cities.api.CityView;
import ru.servermine.cities.core.CityRepository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Role assignment is persisted atomically with city revision and an audit row. */
public final class CityRoleServiceImpl implements CityRoleService {
    private final CityRepository repository;
    private final Consumer<CityView> cityChanged;
    private volatile boolean ready = true;

    public CityRoleServiceImpl(CityRepository repository, Consumer<CityView> cityChanged) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cityChanged = Objects.requireNonNull(cityChanged, "cityChanged");
    }

    @Override public boolean isReady() { return ready; }
    public void deactivate() { ready = false; }

    @Override
    public CompletionStage<CityRoleResult> assign(UUID actorId, UUID targetId, CityRole role, long expectedRevision) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(role, "role");
        if (!ready) return result(CityRoleCode.SERVICE_UNAVAILABLE);
        if (role == CityRole.RULER) return result(CityRoleCode.CANNOT_CHANGE_RULER);
        return repository.assignMemberRole(actorId, targetId, role, expectedRevision).thenApply(result -> {
            if (result.code() == CityRoleCode.ROLE_CHANGED) result.city().ifPresent(cityChanged);
            return result;
        }).exceptionally(error -> new CityRoleResult(CityRoleCode.INTERNAL_ERROR, Optional.empty()));
    }

    private CompletionStage<CityRoleResult> result(CityRoleCode code) {
        return CompletableFuture.completedFuture(new CityRoleResult(code, Optional.empty()));
    }
}
