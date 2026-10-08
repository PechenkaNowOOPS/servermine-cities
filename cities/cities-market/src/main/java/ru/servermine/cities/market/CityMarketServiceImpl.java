package ru.servermine.cities.market;

import ru.servermine.cities.api.CityMarketCode;
import ru.servermine.cities.api.CityMarketCreateRequest;
import ru.servermine.cities.api.CityMarketDelivery;
import ru.servermine.cities.api.CityMarketListing;
import ru.servermine.cities.api.CityMarketPurchaseRequest;
import ru.servermine.cities.api.CityMarketResult;
import ru.servermine.cities.api.CityMarketService;
import ru.servermine.cities.api.CityView;
import ru.servermine.cities.core.CityRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Coordinates the SQLite market transactions and publishes changed city balances. */
public final class CityMarketServiceImpl implements CityMarketService {
    private final CityRepository repository;
    private final Consumer<CityView> cityChanged;
    private final long maximumPrice;
    private final int feeBasisPoints;
    private volatile boolean ready = true;

    public CityMarketServiceImpl(CityRepository repository, Consumer<CityView> cityChanged,
                                 long maximumPrice, int feeBasisPoints) {
        this.repository = Objects.requireNonNull(repository);
        this.cityChanged = Objects.requireNonNull(cityChanged);
        if (maximumPrice < 1 || feeBasisPoints < 0 || feeBasisPoints > 2_000)
            throw new IllegalArgumentException("Invalid market price/fee configuration");
        this.maximumPrice = maximumPrice;
        this.feeBasisPoints = feeBasisPoints;
    }

    @Override public boolean isReady() { return ready; }
    public void deactivate() { ready = false; }

    @Override public CompletionStage<List<CityMarketListing>> listings(UUID viewerId) {
        if (!ready) return CompletableFuture.failedFuture(new IllegalStateException("City market unavailable"));
        return repository.marketListings(viewerId);
    }

    @Override public CompletionStage<List<CityMarketListing>> myListings(UUID playerId) {
        if (!ready) return CompletableFuture.failedFuture(new IllegalStateException("City market unavailable"));
        return repository.myMarketListings(playerId);
    }

    @Override public CompletionStage<List<CityMarketListing>> pendingListings(UUID sellerId) {
        if (!ready) return CompletableFuture.failedFuture(new IllegalStateException("City market unavailable"));
        return repository.pendingMarketListings(sellerId);
    }

    @Override public CompletionStage<CityMarketResult> prepare(CityMarketCreateRequest request) {
        if (!ready) return result(request.listingId(), CityMarketCode.SERVICE_UNAVAILABLE);
        return repository.createMarketListing(request, maximumPrice)
                .exceptionally(error -> resultNow(request.listingId(), CityMarketCode.INTERNAL_ERROR));
    }

    @Override public CompletionStage<Boolean> markRemoving(UUID listingId, UUID sellerId) {
        if (!ready) return CompletableFuture.completedFuture(false);
        return repository.markMarketListingRemoving(listingId, sellerId);
    }

    @Override public CompletionStage<Boolean> activate(UUID listingId, UUID sellerId) {
        if (!ready) return CompletableFuture.completedFuture(false);
        return repository.activateMarketListing(listingId, sellerId);
    }

    @Override public CompletionStage<Boolean> abort(UUID listingId, UUID sellerId) {
        if (!ready) return CompletableFuture.completedFuture(false);
        return repository.abortMarketListing(listingId, sellerId);
    }

    @Override public CompletionStage<CityMarketResult> purchase(CityMarketPurchaseRequest request) {
        if (!ready) return result(request.operationId(), CityMarketCode.SERVICE_UNAVAILABLE);
        return repository.purchaseMarketListing(request, feeBasisPoints).thenApply(result -> {
            result.buyerCity().ifPresent(cityChanged);
            result.sellerCity().ifPresent(cityChanged);
            return result;
        }).exceptionally(error -> resultNow(request.operationId(), CityMarketCode.INTERNAL_ERROR));
    }

    @Override public CompletionStage<CityMarketResult> cancel(UUID listingId, UUID actorId) {
        if (!ready) return result(listingId, CityMarketCode.SERVICE_UNAVAILABLE);
        return repository.cancelMarketListing(listingId, actorId)
                .exceptionally(error -> resultNow(listingId, CityMarketCode.INTERNAL_ERROR));
    }

    @Override public CompletionStage<List<CityMarketDelivery>> deliveries(UUID playerId) {
        if (!ready) return CompletableFuture.failedFuture(new IllegalStateException("City market unavailable"));
        return repository.marketDeliveries(playerId);
    }

    @Override public CompletionStage<Boolean> acknowledgeDelivery(UUID deliveryId, UUID playerId) {
        if (!ready) return CompletableFuture.completedFuture(false);
        return repository.acknowledgeMarketDelivery(deliveryId, playerId);
    }

    private CompletionStage<CityMarketResult> result(UUID operationId, CityMarketCode code) {
        return CompletableFuture.completedFuture(resultNow(operationId, code));
    }

    private CityMarketResult resultNow(UUID operationId, CityMarketCode code) {
        return new CityMarketResult(operationId, code, Optional.empty(), Optional.empty(), Optional.empty());
    }
}
