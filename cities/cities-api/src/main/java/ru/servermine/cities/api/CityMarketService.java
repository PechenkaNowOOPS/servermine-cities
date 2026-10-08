package ru.servermine.cities.api;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** City treasury-backed marketplace with durable item escrow. */
public interface CityMarketService {
    boolean isReady();
    CompletionStage<List<CityMarketListing>> listings(UUID viewerId);
    CompletionStage<List<CityMarketListing>> myListings(UUID playerId);
    CompletionStage<List<CityMarketListing>> pendingListings(UUID sellerId);
    CompletionStage<CityMarketResult> prepare(CityMarketCreateRequest request);
    CompletionStage<Boolean> markRemoving(UUID listingId, UUID sellerId);
    CompletionStage<Boolean> activate(UUID listingId, UUID sellerId);
    CompletionStage<Boolean> abort(UUID listingId, UUID sellerId);
    CompletionStage<CityMarketResult> purchase(CityMarketPurchaseRequest request);
    CompletionStage<CityMarketResult> cancel(UUID listingId, UUID actorId);
    CompletionStage<List<CityMarketDelivery>> deliveries(UUID playerId);
    CompletionStage<Boolean> acknowledgeDelivery(UUID deliveryId, UUID playerId);
}
