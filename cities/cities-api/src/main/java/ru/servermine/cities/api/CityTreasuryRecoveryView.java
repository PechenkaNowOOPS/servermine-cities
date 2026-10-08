package ru.servermine.cities.api;

import java.util.UUID;

/** Read-only cross-system status for one interrupted city treasury deposit. */
public record CityTreasuryRecoveryView(UUID operationId, UUID cityId, UUID actorId, long amount,
                                       String reason, String cityState, String economyState) { }
