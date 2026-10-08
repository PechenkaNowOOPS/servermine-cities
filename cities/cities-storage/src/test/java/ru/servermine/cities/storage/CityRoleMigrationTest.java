package ru.servermine.cities.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CityRoleMigrationTest {
    @TempDir Path directory;

    @Test
    void legacySchemaMigratesRoleMarketAndAdminTestAuditStores() throws Exception {
        Path database = directory.resolve("legacy-cities.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE city_members(city_uuid TEXT, player_uuid TEXT, role_id TEXT, joined_at TEXT)");
            statement.execute("INSERT INTO city_members VALUES ('city', 'player', 'RESIDENT', '2026-01-01T00:00:00Z')");
            statement.execute("CREATE TABLE city_operations(operation_type TEXT, state TEXT, created_at TEXT)");
            statement.execute("PRAGMA user_version=2");
        }

        new SqliteCityRepository(database, 5_000, Runnable::run).initialize();

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var version = statement.executeQuery("PRAGMA user_version")) {
            assertTrue(version.next());
            assertEquals(12, version.getInt(1));
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var role = statement.executeQuery("SELECT role_id FROM city_members WHERE player_uuid='player'")) {
            assertTrue(role.next());
            assertEquals("MEMBER", role.getString(1));
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var audit = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='city_role_audit'")) {
            assertTrue(audit.next());
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var upgrades = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='city_upgrades'")) {
            assertTrue(upgrades.next());
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var treaties = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='city_treaties'")) {
            assertTrue(treaties.next());
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var transfers = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='city_treasury_transfers'")) {
            assertTrue(transfers.next());
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var market = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='city_market_deliveries'")) {
            assertTrue(market.next());
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var audit = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='city_admin_test_audit'")) {
            assertTrue(audit.next());
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var tax = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='city_tax_policy'")) {
            assertTrue(tax.next());
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var audit = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='city_treasury_recovery_audit'")) {
            assertTrue(audit.next());
        }
        var repository = new SqliteCityRepository(database, 5_000, Runnable::run);
        var operationId = java.util.UUID.randomUUID();
        var actorId = java.util.UUID.randomUUID();
        repository.recordTreasuryRecoveryAudit(operationId, actorId, "RECONCILED").toCompletableFuture().join();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var entry = statement.executeQuery("SELECT actor_uuid, outcome FROM city_treasury_recovery_audit WHERE operation_uuid='"
                     + operationId + "'")) {
            assertTrue(entry.next());
            assertEquals(actorId.toString(), entry.getString("actor_uuid"));
            assertEquals("RECONCILED", entry.getString("outcome"));
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var indexes = statement.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='index' AND name IN "
                     + "('idx_city_market_city_status_created','idx_city_market_seller_status_created',"
                     + "'idx_city_operations_type_state_created','idx_city_treaties_proposing_created','idx_city_treaties_target_created',"
                     + "'idx_city_tax_due','idx_city_members_city_joined')")) {
            assertTrue(indexes.next());
            assertEquals(7, indexes.getInt(1));
        }
    }
}
