package ru.servermine.cities.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.servermine.cities.api.ChunkPosition;
import ru.servermine.cities.api.CityFoundationDraft;
import ru.servermine.cities.api.CityStage;
import ru.servermine.cities.api.CityUpgradeCode;
import ru.servermine.cities.api.CityUpgradeId;
import ru.servermine.cities.core.CityUpgradeDraft;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CityUpgradeTransactionTest {
    @TempDir Path directory;

    @Test void purchaseDebitsTreasuryAndUnlocksUpgradeOnce() throws Exception {
        Path database = directory.resolve("cities.db");
        SqliteCityRepository repository = new SqliteCityRepository(database, 5_000, Runnable::run);
        repository.initialize();
        UUID cityId = UUID.randomUUID();
        UUID rulerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        CityFoundationDraft foundation = new CityFoundationDraft(UUID.randomUUID(), cityId, rulerId,
                "Ruler", "Test City", "test city", Set.of(
                new ChunkPosition(worldId, 0, 0), new ChunkPosition(worldId, 0, 1),
                new ChunkPosition(worldId, 1, 0), new ChunkPosition(worldId, 1, 1)));
        repository.found(foundation).toCompletableFuture().join();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var update = connection.prepareStatement("UPDATE cities SET stage='CITY', treasury=1000 WHERE city_uuid=?")) {
            update.setString(1, cityId.toString());
            assertEquals(1, update.executeUpdate());
        }

        CityUpgradeDraft unauthorized = new CityUpgradeDraft(UUID.randomUUID(), cityId, UUID.randomUUID(), 1,
                CityUpgradeId.CITY_WARDING, CityStage.CITY, 500, Set.of());
        assertEquals(CityUpgradeCode.NOT_MEMBER, repository.purchaseUpgrade(unauthorized).toCompletableFuture().join().code());
        CityUpgradeDraft missingPrerequisite = new CityUpgradeDraft(UUID.randomUUID(), cityId, rulerId, 1,
                CityUpgradeId.CITY_GUARDS, CityStage.CITY, 500, Set.of(CityUpgradeId.CITY_WARDING));
        assertEquals(CityUpgradeCode.PREREQUISITE_REQUIRED,
                repository.purchaseUpgrade(missingPrerequisite).toCompletableFuture().join().code());

        CityUpgradeDraft purchase = new CityUpgradeDraft(UUID.randomUUID(), cityId, rulerId, 1,
                CityUpgradeId.CITY_WARDING, CityStage.CITY, 500, Set.of());
        assertEquals(CityUpgradeCode.PURCHASED, repository.purchaseUpgrade(purchase).toCompletableFuture().join().code());
        assertEquals(CityUpgradeCode.REPLAYED, repository.purchaseUpgrade(purchase).toCompletableFuture().join().code());
        CityUpgradeDraft conflictingReplay = new CityUpgradeDraft(purchase.operationId(), cityId, rulerId, 1,
                CityUpgradeId.CITY_WARDING, CityStage.CITY, 400, Set.of());
        assertEquals(CityUpgradeCode.OPERATION_CONFLICT,
                repository.purchaseUpgrade(conflictingReplay).toCompletableFuture().join().code());
        CityUpgradeDraft alreadyOwned = new CityUpgradeDraft(UUID.randomUUID(), cityId, rulerId, 2,
                CityUpgradeId.CITY_WARDING, CityStage.CITY, 500, Set.of());
        assertEquals(CityUpgradeCode.ALREADY_OWNED,
                repository.purchaseUpgrade(alreadyOwned).toCompletableFuture().join().code());
        var city = repository.find(cityId).toCompletableFuture().join().orElseThrow();
        assertTrue(city.upgrades().contains(CityUpgradeId.CITY_WARDING));
        assertEquals(500, city.treasury());
        assertEquals(2, city.revision());
        var history = repository.treasuryHistory(cityId, 10).toCompletableFuture().join();
        assertEquals(1, history.size());
        assertEquals(-500, history.getFirst().amount());
        assertEquals(500, history.getFirst().balanceAfter());
    }
}
