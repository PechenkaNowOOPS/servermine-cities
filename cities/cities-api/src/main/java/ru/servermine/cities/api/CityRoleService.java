package ru.servermine.cities.api;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Change member roles through the authoritative transactional Cities service. */
public interface CityRoleService {
    boolean isReady();

    CompletionStage<CityRoleResult> assign(UUID actorId, UUID targetId, CityRole role, long expectedRevision);
}
