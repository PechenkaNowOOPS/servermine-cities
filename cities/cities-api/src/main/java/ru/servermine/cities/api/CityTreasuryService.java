package ru.servermine.cities.api;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** City balance, audited history, and physical-coin deposits. City development costs debit this treasury. */
public interface CityTreasuryService {
    boolean isReady();

    CompletionStage<CityTreasuryResult> deposit(UUID playerId, long amount);

    CompletionStage<List<CityTreasuryEntry>> history(UUID playerId, int limit);

    CompletionStage<CityTreasuryTransferResult> transfer(CityTreasuryTransferRequest request);

    /** OP-only diagnostics for interrupted physical-currency deposits. */
    default CompletionStage<List<CityTreasuryRecoveryView>> pendingRecovery() {
        return java.util.concurrent.CompletableFuture.completedFuture(List.of());
    }

    /** OP-only request to resume the existing idempotent recovery path for one pending operation. */
    default CompletionStage<Boolean> reconcile(UUID operationId, UUID actorId) {
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
}
