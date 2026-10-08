package ru.servermine.cities.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.servermine.cities.api.*;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CityMarketTransactionTest {
    @TempDir Path directory;

    @Test void marketPurchaseRequiresTradeAndAtomicallyMovesTreasuryAndCreatesMailboxDelivery() throws Exception {
        Path database = directory.resolve("market.db");
        SqliteCityRepository repository = new SqliteCityRepository(database, 5_000, Runnable::run);
        repository.initialize();
        UUID buyerCity = UUID.randomUUID(), buyer = UUID.randomUUID();
        UUID sellerCity = UUID.randomUUID(), seller = UUID.randomUUID();
        found(repository, buyerCity, buyer, UUID.randomUUID(), 0, "Buyers");
        found(repository, sellerCity, seller, UUID.randomUUID(), 4, "Sellers");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var update = connection.prepareStatement("UPDATE cities SET treasury=1000 WHERE city_uuid IN (?,?)")) {
            update.setString(1, buyerCity.toString()); update.setString(2, sellerCity.toString()); update.executeUpdate();
        }
        UUID listingId = UUID.randomUUID(); byte[] item = {1, 2, 3, 4};
        var listed = repository.createMarketListing(new CityMarketCreateRequest(listingId, seller, "Железный слиток", item, 200), 1000)
                .toCompletableFuture().join();
        assertEquals(CityMarketCode.PREPARED, listed.code());
        assertEquals(CityMarketState.PREPARING, pending(repository, seller, listingId).state());
        assertTrue(repository.markMarketListingRemoving(listingId, seller).toCompletableFuture().join());
        assertEquals(CityMarketState.REMOVING, pending(repository, seller, listingId).state());
        assertTrue(repository.activateMarketListing(listingId, seller).toCompletableFuture().join());
        UUID cancelledId = UUID.randomUUID();
        assertEquals(CityMarketCode.PREPARED, repository.createMarketListing(new CityMarketCreateRequest(
                cancelledId, seller, "Золотой слиток", new byte[]{9}, 50), 1000).toCompletableFuture().join().code());
        assertTrue(repository.markMarketListingRemoving(cancelledId, seller).toCompletableFuture().join());
        assertTrue(repository.activateMarketListing(cancelledId, seller).toCompletableFuture().join());
        assertEquals(2, repository.myMarketListings(seller).toCompletableFuture().join().size());
        assertEquals(CityMarketCode.CANCELLED, repository.cancelMarketListing(cancelledId, seller).toCompletableFuture().join().code());
        assertEquals(1, repository.myMarketListings(seller).toCompletableFuture().join().size());
        var refunds = repository.marketDeliveries(seller).toCompletableFuture().join();
        assertEquals(1, refunds.size()); assertArrayEquals(new byte[]{9}, refunds.getFirst().itemData());
        assertEquals(CityMarketCode.TRADE_REQUIRED, repository.purchaseMarketListing(
                new CityMarketPurchaseRequest(UUID.randomUUID(), buyer, listingId), 500).toCompletableFuture().join().code());

        UUID treatyId = UUID.randomUUID();
        assertEquals(CityDiplomacyCode.CREATED, repository.executeDiplomacy(new CityDiplomacyRequest(treatyId, sellerCity,
                seller, CityDiplomacyRequest.Action.PROPOSE, buyerCity, CityTreatyType.TRADE, 30)).toCompletableFuture().join().code());
        assertEquals(CityDiplomacyCode.ACCEPTED, repository.executeDiplomacy(new CityDiplomacyRequest(treatyId, buyerCity,
                buyer, CityDiplomacyRequest.Action.ACCEPT, null, null, 30)).toCompletableFuture().join().code());
        assertEquals(1, repository.marketListings(buyer).toCompletableFuture().join().size());

        UUID operationId = UUID.randomUUID();
        var request = new CityMarketPurchaseRequest(operationId, buyer, listingId);
        var purchased = repository.purchaseMarketListing(request, 500).toCompletableFuture().join();
        assertEquals(CityMarketCode.PURCHASED, purchased.code());
        assertEquals(CityMarketCode.REPLAYED, repository.purchaseMarketListing(request, 500).toCompletableFuture().join().code());
        assertEquals(790, repository.find(buyerCity).toCompletableFuture().join().orElseThrow().treasury());
        assertEquals(1210, repository.find(sellerCity).toCompletableFuture().join().orElseThrow().treasury());
        assertEquals(1, repository.treasuryHistory(buyerCity, 10).toCompletableFuture().join().size());
        assertEquals(1, repository.treasuryHistory(sellerCity, 10).toCompletableFuture().join().size());
        var deliveries = repository.marketDeliveries(buyer).toCompletableFuture().join();
        assertEquals(1, deliveries.size()); assertArrayEquals(item, deliveries.getFirst().itemData());
        assertTrue(repository.acknowledgeMarketDelivery(deliveries.getFirst().id(), buyer).toCompletableFuture().join());
        assertTrue(repository.marketDeliveries(buyer).toCompletableFuture().join().isEmpty());
        assertTrue(repository.marketListings(buyer).toCompletableFuture().join().isEmpty());
    }

    private static void found(SqliteCityRepository repository, UUID cityId, UUID ownerId, UUID worldId, int offset, String name) {
        repository.found(new CityFoundationDraft(UUID.randomUUID(), cityId, ownerId, name + "Owner", name, name.toLowerCase(),
                Set.of(new ChunkPosition(worldId, offset, 0), new ChunkPosition(worldId, offset, 1),
                        new ChunkPosition(worldId, offset + 1, 0), new ChunkPosition(worldId, offset + 1, 1))))
                .toCompletableFuture().join();
    }

    private static CityMarketListing pending(SqliteCityRepository repository, UUID seller, UUID listing) {
        return repository.pendingMarketListings(seller).toCompletableFuture().join().stream()
                .filter(value -> value.id().equals(listing)).findFirst().orElseThrow();
    }
}
