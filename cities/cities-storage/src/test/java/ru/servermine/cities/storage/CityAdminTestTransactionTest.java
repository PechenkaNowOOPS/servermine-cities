package ru.servermine.cities.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.servermine.cities.api.ChunkPosition;
import ru.servermine.cities.api.CityFoundationDraft;
import ru.servermine.cities.api.CityStage;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CityAdminTestTransactionTest {
    @TempDir Path directory;

    @Test void testGrantsAndStageChangesAreAtomicAndAudited() throws Exception {
        Path database = directory.resolve("cities.db");
        SqliteCityRepository repository = new SqliteCityRepository(database, 5_000, Runnable::run);
        repository.initialize();
        UUID cityId = UUID.randomUUID();
        UUID rulerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        repository.found(new CityFoundationDraft(UUID.randomUUID(), cityId, rulerId, "Ruler", "Test City", "test city",
                Set.of(new ChunkPosition(worldId, 0, 0), new ChunkPosition(worldId, 0, 1),
                        new ChunkPosition(worldId, 1, 0), new ChunkPosition(worldId, 1, 1))))
                .toCompletableFuture().join();

        var grant = repository.adminTestGrantTreasury(UUID.randomUUID(), adminId, rulerId, 25_000)
                .toCompletableFuture().join().orElseThrow();
        assertEquals(25_000, grant.treasury());
        assertEquals(2, grant.revision());

        var staged = repository.adminTestSetStage(UUID.randomUUID(), adminId, rulerId, CityStage.CITY)
                .toCompletableFuture().join().orElseThrow();
        assertEquals(CityStage.CITY, staged.stage());
        assertEquals(3, staged.revision());
        assertTrue(repository.adminTestGrantTreasury(UUID.randomUUID(), adminId, UUID.randomUUID(), 100)
                .toCompletableFuture().join().isEmpty());

        var history = repository.treasuryHistory(cityId, 10).toCompletableFuture().join();
        assertEquals(1, history.size());
        assertEquals("ADMIN_TEST_GRANT", history.getFirst().reason());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var query = connection.createStatement().executeQuery("SELECT action, actor_uuid FROM city_admin_test_audit ORDER BY occurred_at")) {
            assertTrue(query.next());
            assertEquals("TREASURY_GRANT", query.getString(1));
            assertEquals(adminId.toString(), query.getString(2));
            assertTrue(query.next());
            assertEquals("STAGE_SET", query.getString(1));
            assertEquals(adminId.toString(), query.getString(2));
        }
    }
}
