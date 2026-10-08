package ru.servermine.cities.treasury;

import ru.servermine.cities.api.CityTreasuryCode;
import ru.servermine.cities.api.CityTreasuryEntry;
import ru.servermine.cities.api.CityTreasuryResult;
import ru.servermine.cities.api.CityTreasuryRecoveryView;
import ru.servermine.cities.api.CityTreasuryService;
import ru.servermine.cities.api.CityTreasuryTransferCode;
import ru.servermine.cities.api.CityTreasuryTransferRequest;
import ru.servermine.cities.api.CityTreasuryTransferResult;
import ru.servermine.cities.api.CityView;
import ru.servermine.cities.api.CityRole;
import ru.servermine.cities.api.CityPermission;
import ru.servermine.cities.core.CityRepository;
import ru.servermine.cities.core.CityTreasuryDraft;
import ru.servermine.cities.core.PendingTreasuryDeposit;
import ru.servermine.economy.api.EconomyResult;
import ru.servermine.economy.api.EconomyService;
import ru.servermine.economy.api.MoneyRequest;
import ru.servermine.economy.api.OperationState;
import ru.servermine.economy.api.ResultCode;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Physical-coin deposits are a durable saga; city spending is committed with its domain action. */
public final class CityTreasuryServiceImpl implements CityTreasuryService {
    private final CityRepository repository;
    private final EconomyService economy;
    private final Consumer<CityView> cityChanged;
    private final long maximumTransferAmount;
    private volatile boolean ready;
    private final java.util.Set<UUID> recoveringOperations = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public CityTreasuryServiceImpl(CityRepository repository, EconomyService economy, Consumer<CityView> cityChanged) {
        this(repository, economy, cityChanged, 100_000);
    }

    public CityTreasuryServiceImpl(CityRepository repository, EconomyService economy, Consumer<CityView> cityChanged,
                                   long maximumTransferAmount) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.economy = economy;
        this.cityChanged = Objects.requireNonNull(cityChanged, "cityChanged");
        if (maximumTransferAmount < 1) throw new IllegalArgumentException("maximumTransferAmount must be positive");
        this.maximumTransferAmount = maximumTransferAmount;
    }

    @Override public boolean isReady() { return ready; }
    public void deactivate() { ready = false; }

    /** Reconcile deposits interrupted at any Economy/SQLite boundary before accepting new deposits. */
    public CompletionStage<Void> recoverPending() {
        ready = false;
        return repository.pendingTreasuryDeposits().thenCompose(pending -> {
            CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
            for (PendingTreasuryDeposit operation : pending) {
                chain = chain.thenCompose(ignored -> recover(operation));
            }
            return chain;
        }).thenRun(() -> ready = true);
    }

    @Override
    public CompletionStage<CityTreasuryResult> deposit(UUID playerId, long amount) {
        return deposit(UUID.randomUUID(), null, playerId, amount, "DEPOSIT");
    }

    /** Stable operation id and dedicated ledger label used by daily city taxes. */
    public CompletionStage<CityTreasuryResult> collectTax(UUID operationId, UUID cityId, UUID playerId, long amount) {
        return deposit(operationId, cityId, playerId, amount, "CITY_TAX");
    }

    private CompletionStage<CityTreasuryResult> deposit(UUID operationId, UUID expectedCityId, UUID playerId,
                                                        long amount, String reason) {
        if (!ready) return result(operationId, CityTreasuryCode.SERVICE_UNAVAILABLE);
        if (amount <= 0) return result(operationId, CityTreasuryCode.INVALID_AMOUNT);
        if (economy == null || !economy.isReady()) return result(operationId, CityTreasuryCode.ECONOMY_UNAVAILABLE);
        return repository.findForPlayer(playerId).thenCompose(found -> {
            if (found.isEmpty() || (expectedCityId != null && !found.get().id().equals(expectedCityId)))
                return result(operationId, CityTreasuryCode.NO_CITY);
            CityTreasuryDraft draft = new CityTreasuryDraft(operationId, found.get().id(), playerId, amount, reason);
            return repository.prepareTreasuryDeposit(draft).thenCompose(prepared -> {
                if (prepared.code() != CityTreasuryCode.OPERATION_IN_PROGRESS) return CompletableFuture.completedFuture(prepared);
                MoneyRequest money = new MoneyRequest(operationId, playerId, amount, "ServerMineCities",
                        reason.equals("CITY_TAX") ? "city_tax" : "city_treasury_deposit");
                return economy.reserve(money).thenCompose(reservation -> handleReservation(draft, reservation));
            });
        }).exceptionally(error -> resultNow(operationId, CityTreasuryCode.INTERNAL_ERROR));
    }

    @Override
    public CompletionStage<List<CityTreasuryEntry>> history(UUID playerId, int limit) {
        if (!ready) return CompletableFuture.failedFuture(new IllegalStateException("City treasury is recovering"));
        return repository.findForPlayer(playerId).thenCompose(found -> {
            if (found.isEmpty()) return CompletableFuture.completedFuture(List.of());
            CityRole role = found.get().residents().stream().filter(resident -> resident.playerId().equals(playerId))
                    .map(resident -> CityRole.fromStorage(resident.role())).findFirst().orElse(CityRole.MEMBER);
            if (!role.allows(CityPermission.VIEW_TREASURY)) {
                return CompletableFuture.failedFuture(new SecurityException("This city role cannot view treasury history"));
            }
            return repository.treasuryHistory(found.get().id(), limit);
        });
    }

    @Override
    public CompletionStage<CityTreasuryTransferResult> transfer(CityTreasuryTransferRequest request) {
        if (!ready) return CompletableFuture.completedFuture(transferResult(request, CityTreasuryTransferCode.SERVICE_UNAVAILABLE));
        return repository.transferTreasury(request, maximumTransferAmount).thenApply(result -> {
            result.sourceCity().ifPresent(cityChanged);
            result.targetCity().ifPresent(cityChanged);
            return result;
        }).exceptionally(error -> transferResult(request, CityTreasuryTransferCode.INTERNAL_ERROR));
    }

    @Override
    public CompletionStage<List<CityTreasuryRecoveryView>> pendingRecovery() {
        return repository.pendingTreasuryDeposits().thenCompose(pending -> {
            CompletionStage<List<CityTreasuryRecoveryView>> chain = CompletableFuture.completedFuture(new java.util.ArrayList<>());
            for (PendingTreasuryDeposit operation : pending) {
                chain = chain.thenCompose(rows -> economy == null || !economy.isReady()
                        ? CompletableFuture.completedFuture(append(rows, recoveryView(operation, "UNAVAILABLE")))
                        : economy.operation(operation.operationId()).handle((found, error) -> append(rows,
                                recoveryView(operation, error != null ? "ERROR"
                                        : found.isEmpty() ? "NOT_FOUND" : found.get().state().name()))));
            }
            return chain.thenApply(List::copyOf);
        });
    }

    private List<CityTreasuryRecoveryView> append(List<CityTreasuryRecoveryView> rows, CityTreasuryRecoveryView row) {
        rows.add(row);
        return rows;
    }

    private CityTreasuryRecoveryView recoveryView(PendingTreasuryDeposit operation, String economyState) {
        return new CityTreasuryRecoveryView(operation.operationId(), operation.cityId(), operation.actorId(),
                operation.amount(), operation.reason(), operation.state(), economyState);
    }

    @Override
    public CompletionStage<Boolean> reconcile(UUID operationId, UUID actorId) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(actorId, "actorId");
        if (!recoveringOperations.add(operationId)) return CompletableFuture.completedFuture(false);
        CompletionStage<Boolean> attempt = repository.pendingTreasuryDeposits().thenCompose(pending -> {
            PendingTreasuryDeposit operation = pending.stream()
                    .filter(row -> row.operationId().equals(operationId)).findFirst().orElse(null);
            if (operation == null) return repository.recordTreasuryRecoveryAudit(operationId, actorId, "NOT_PENDING")
                    .thenApply(ignored -> false);
            return repository.recordTreasuryRecoveryAudit(operationId, actorId, "RECONCILE_STARTED")
                    .thenCompose(ignored -> recover(operation))
                    .thenCompose(ignored -> repository.pendingTreasuryDeposits())
                    .thenCompose(remaining -> {
                        boolean resolved = remaining.stream().noneMatch(row -> row.operationId().equals(operationId));
                        return repository.recordTreasuryRecoveryAudit(operationId, actorId,
                                resolved ? "RECONCILED" : "STILL_PENDING").thenApply(done -> resolved);
                    });
        }).exceptionallyCompose(error -> repository.recordTreasuryRecoveryAudit(operationId, actorId,
                "FAILED: " + rootMessage(error)).handle((ignored, auditError) -> false));
        return attempt.whenComplete((ignored, error) -> recoveringOperations.remove(operationId));
    }

    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message;
    }

    private CityTreasuryTransferResult transferResult(CityTreasuryTransferRequest request, CityTreasuryTransferCode code) {
        return new CityTreasuryTransferResult(request.operationId(), code, Optional.empty(), Optional.empty());
    }

    private CompletionStage<CityTreasuryResult> handleReservation(CityTreasuryDraft draft, EconomyResult reservation) {
        if (reservation.code() != ResultCode.OK || reservation.state() != OperationState.RESERVED) {
            if (reservation.state() == OperationState.UNKNOWN || reservation.state() == OperationState.PREPARED
                    || reservation.code() == ResultCode.UNKNOWN_OUTCOME || reservation.code() == ResultCode.INTERNAL_ERROR) {
                return result(draft.operationId(), CityTreasuryCode.UNKNOWN_OUTCOME);
            }
            return repository.failTreasuryDeposit(draft.operationId(), false).thenApply(ignored -> resultNow(
                    draft.operationId(), reservation.code() == ResultCode.INSUFFICIENT_FUNDS
                            ? CityTreasuryCode.INSUFFICIENT_FUNDS : CityTreasuryCode.INTERNAL_ERROR));
        }
        return repository.reserveTreasuryDeposit(draft.operationId()).thenCompose(reserved -> reserved
                ? applyAndCommit(draft) : release(draft, CityTreasuryCode.OPERATION_IN_PROGRESS));
    }

    private CompletionStage<CityTreasuryResult> applyAndCommit(CityTreasuryDraft draft) {
        return repository.applyTreasuryDeposit(draft).thenCompose(applied -> {
            if (applied.code() != CityTreasuryCode.DEPOSITED) return release(draft, applied.code());
            return economy.commit(draft.operationId()).thenCompose(committed -> {
                if (committed.code() == ResultCode.OK && committed.state() == OperationState.COMMITTED) {
                    return repository.completeTreasuryDeposit(draft.operationId()).thenApply(done -> publish(done));
                }
                return repository.markTreasuryCommitPending(draft.operationId())
                        .thenApply(ignored -> resultNow(draft.operationId(), CityTreasuryCode.UNKNOWN_OUTCOME));
            });
        });
    }

    private CompletionStage<CityTreasuryResult> release(CityTreasuryDraft draft, CityTreasuryCode failure) {
        return repository.failTreasuryDeposit(draft.operationId(), true).thenCompose(ignored -> economy.release(draft.operationId()))
                .thenCompose(released -> {
                    if (released.code() == ResultCode.OK && released.state() == OperationState.RELEASED) {
                        return repository.markTreasuryDepositReleased(draft.operationId())
                                .thenApply(done -> resultNow(draft.operationId(), failure));
                    }
                    return result(draft.operationId(), CityTreasuryCode.UNKNOWN_OUTCOME);
                });
    }

    private CompletionStage<Void> recover(PendingTreasuryDeposit pending) {
        if (economy == null || !economy.isReady()) return CompletableFuture.failedFuture(
                new IllegalStateException("Economy is unavailable while recovering city deposits"));
        CityTreasuryDraft draft = new CityTreasuryDraft(pending.operationId(), pending.cityId(), pending.actorId(),
                pending.amount(), pending.reason());
        return economy.operation(pending.operationId()).thenCompose(found -> {
            if (found.isEmpty()) {
                if (pending.state().equals("PREPARED")) return repository.failTreasuryDeposit(pending.operationId(), false);
                if (pending.state().equals("RELEASE_PENDING")) return repository.markTreasuryDepositReleased(pending.operationId());
                return CompletableFuture.failedFuture(new IllegalStateException("Treasury operation is pending without an Economy record"));
            }
            OperationState economyState = found.get().state();
            if (pending.state().equals("RELEASE_PENDING")) {
                if (economyState == OperationState.RELEASED) return repository.markTreasuryDepositReleased(pending.operationId());
                if (economyState == OperationState.RESERVED || economyState == OperationState.RELEASING) {
                    return economy.release(pending.operationId()).thenCompose(result -> result.state() == OperationState.RELEASED
                            ? repository.markTreasuryDepositReleased(pending.operationId())
                            : CompletableFuture.failedFuture(new IllegalStateException("Could not recover treasury release")));
                }
                return CompletableFuture.failedFuture(new IllegalStateException("Treasury release outcome needs manual reconciliation"));
            }
            if (pending.state().equals("PREPARED")) {
                if (economyState == OperationState.RESERVED) {
                    return repository.reserveTreasuryDeposit(pending.operationId()).thenCompose(ok -> ok
                            ? applyAndCommit(draft).thenApply(ignored -> null)
                            : CompletableFuture.failedFuture(new IllegalStateException("Could not restore treasury reservation")));
                }
                if (economyState == OperationState.FAILED || economyState == OperationState.RELEASED) {
                    return repository.failTreasuryDeposit(pending.operationId(), false);
                }
                return CompletableFuture.failedFuture(new IllegalStateException("Prepared treasury operation needs reconciliation"));
            }
            if (pending.state().equals("RESERVED")) {
                if (economyState == OperationState.RESERVED) return applyAndCommit(draft).thenApply(ignored -> null);
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Reserved treasury operation does not match Economy state " + economyState));
            }
            if (pending.state().equals("DOMAIN_COMMITTED") || pending.state().equals("COMMIT_PENDING")) {
                if (economyState == OperationState.COMMITTED) return repository.completeTreasuryDeposit(pending.operationId()).thenAccept(this::publish);
                if (economyState == OperationState.RESERVED) return economy.commit(pending.operationId()).thenCompose(result ->
                        result.state() == OperationState.COMMITTED
                                ? repository.completeTreasuryDeposit(pending.operationId()).thenAccept(this::publish)
                                : CompletableFuture.failedFuture(new IllegalStateException("Could not recover treasury commit")));
            }
            return CompletableFuture.failedFuture(new IllegalStateException("Treasury operation needs manual reconciliation: " + pending.operationId()));
        });
    }

    private CityTreasuryResult publish(CityTreasuryResult result) {
        if (result.code() == CityTreasuryCode.DEPOSITED) result.city().ifPresent(cityChanged);
        return result;
    }

    private CompletionStage<CityTreasuryResult> result(UUID id, CityTreasuryCode code) {
        return CompletableFuture.completedFuture(resultNow(id, code));
    }

    private CityTreasuryResult resultNow(UUID id, CityTreasuryCode code) {
        return new CityTreasuryResult(id, code, Optional.empty());
    }
}
