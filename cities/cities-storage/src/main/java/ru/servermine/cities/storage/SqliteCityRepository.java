package ru.servermine.cities.storage;

import ru.servermine.cities.api.ChunkPosition;
import ru.servermine.cities.api.CityStage;
import ru.servermine.cities.api.CityView;
import ru.servermine.cities.api.CityFoundationCode;
import ru.servermine.cities.api.CityFoundationDraft;
import ru.servermine.cities.api.CityFoundationResult;
import ru.servermine.cities.api.CityPromotionCode;
import ru.servermine.cities.api.CityPromotionResult;
import ru.servermine.cities.api.CityClaimCode;
import ru.servermine.cities.api.CityClaimResult;
import ru.servermine.cities.api.CityLeaveCode;
import ru.servermine.cities.api.CityLeaveResult;
import ru.servermine.cities.api.CityMembershipCode;
import ru.servermine.cities.api.CityMembershipResult;
import ru.servermine.cities.api.CityTreasuryCode;
import ru.servermine.cities.api.CityTreasuryEntry;
import ru.servermine.cities.api.CityTreasuryResult;
import ru.servermine.cities.api.CityRole;
import ru.servermine.cities.api.CityRoleCode;
import ru.servermine.cities.api.CityRoleResult;
import ru.servermine.cities.api.CityDiplomacyEvent;
import ru.servermine.cities.api.CityDiplomacyCode;
import ru.servermine.cities.api.CityDiplomacyRequest;
import ru.servermine.cities.api.CityDiplomacyResult;
import ru.servermine.cities.api.CityTreatyState;
import ru.servermine.cities.api.CityTreatyType;
import ru.servermine.cities.api.CityTreatyView;
import ru.servermine.cities.api.CityTreasuryTransferCode;
import ru.servermine.cities.api.CityTreasuryTransferRequest;
import ru.servermine.cities.api.CityTreasuryTransferResult;
import ru.servermine.cities.api.CityUpgradeCode;
import ru.servermine.cities.api.CityUpgradeId;
import ru.servermine.cities.api.CityUpgradeResult;
import ru.servermine.cities.api.CityMarketCode;
import ru.servermine.cities.api.CityMarketCreateRequest;
import ru.servermine.cities.api.CityMarketListing;
import ru.servermine.cities.api.CityMarketPurchaseRequest;
import ru.servermine.cities.api.CityMarketResult;
import ru.servermine.cities.api.CityMarketDelivery;
import ru.servermine.cities.api.CityMarketState;
import ru.servermine.cities.api.CityLaws;
import ru.servermine.cities.api.CityLawsCode;
import ru.servermine.cities.api.CityLawsRequest;
import ru.servermine.cities.api.CityLawsResult;
import ru.servermine.cities.api.CityTaxPolicy;
import ru.servermine.cities.api.CityTaxRequest;
import ru.servermine.cities.api.CityTaxCode;
import ru.servermine.cities.api.CityTaxResult;
import ru.servermine.cities.core.CityRepository;
import ru.servermine.cities.core.CityPromotionDraft;
import ru.servermine.cities.core.CityClaimDraft;
import ru.servermine.cities.core.CityTreasuryDraft;
import ru.servermine.cities.core.CityTaxDue;
import ru.servermine.cities.core.PendingTreasuryDeposit;
import ru.servermine.cities.core.CityUpgradeDraft;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Objects;

/**
 * SQLite adapter owned by Cities. Database calls run on the injected I/O executor;
 * world and inventory access are intentionally absent from this module.
 */
public final class SqliteCityRepository implements CityRepository {
    private static final int SCHEMA_VERSION = 12;

    private final String jdbcUrl;
    private final int busyTimeoutMillis;
    private final Executor ioExecutor;

    public SqliteCityRepository(Path databaseFile, int busyTimeoutMillis, Executor ioExecutor) {
        if (busyTimeoutMillis < 1 || busyTimeoutMillis > 60_000) {
            throw new IllegalArgumentException("busyTimeoutMillis must be between 1 and 60000");
        }
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        this.busyTimeoutMillis = busyTimeoutMillis;
        this.ioExecutor = ioExecutor;
    }

    /** Called once during plugin startup, before publishing the public service. */
    public void initialize() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=FULL");
            int version;
            try (ResultSet result = statement.executeQuery("PRAGMA user_version")) {
                if (!result.next()) throw new SQLException("SQLite did not return a schema version");
                version = result.getInt(1);
            }
            if (version > SCHEMA_VERSION) {
                throw new SQLException("Cities database schema " + version + " is newer than supported " + SCHEMA_VERSION);
            }
            if (version == 0) {
                createVersionOne(connection);
                version = 1;
            }
            if (version == 1) {
                createVersionTwo(connection);
                version = 2;
            }
            if (version == 2) {
                createVersionThree(connection);
                version = 3;
            }
            if (version == 3) { createVersionFour(connection); version = 4; }
            if (version == 4) { createVersionFive(connection); version = 5; }
            if (version == 5) { createVersionSix(connection); version = 6; }
            if (version == 6) { createVersionSeven(connection); version = 7; }
            if (version == 7) { createVersionEight(connection); version = 8; }
            if (version == 8) { createVersionNine(connection); version = 9; }
            if (version == 9) { createVersionTen(connection); version = 10; }
            if (version == 10) { createVersionEleven(connection); version = 11; }
            if (version == 11) createVersionTwelve(connection);
        }
    }

    private void createVersionFive(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE city_treaties (
                        treaty_uuid TEXT PRIMARY KEY,
                        proposing_city TEXT NOT NULL REFERENCES cities(city_uuid) ON DELETE CASCADE,
                        target_city TEXT NOT NULL REFERENCES cities(city_uuid) ON DELETE CASCADE,
                        treaty_type TEXT NOT NULL CHECK (treaty_type IN ('ALLIANCE','NON_AGGRESSION','TRADE')),
                        status TEXT NOT NULL CHECK (status IN ('PENDING','ACTIVE','DECLINED','CANCELLED','TERMINATED','EXPIRED')),
                        duration_days INTEGER NOT NULL CHECK (duration_days BETWEEN 1 AND 90),
                        proposed_by TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        expires_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX idx_city_treaties_cities ON city_treaties(proposing_city, target_city, status)");
            statement.execute("""
                    CREATE TABLE city_diplomacy_events (
                        event_uuid TEXT PRIMARY KEY,
                        treaty_uuid TEXT NOT NULL REFERENCES city_treaties(treaty_uuid) ON DELETE CASCADE,
                        proposing_city_name TEXT NOT NULL,
                        target_city_name TEXT NOT NULL,
                        event_code TEXT NOT NULL,
                        occurred_at TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX idx_city_diplomacy_events_treaty ON city_diplomacy_events(treaty_uuid, occurred_at DESC)");
            statement.execute("PRAGMA user_version = 5");
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        }
    }

    private void createVersionSix(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE city_treasury_transfers (
                        operation_uuid TEXT PRIMARY KEY,
                        source_city TEXT NOT NULL REFERENCES cities(city_uuid),
                        target_city TEXT NOT NULL REFERENCES cities(city_uuid),
                        actor_uuid TEXT NOT NULL,
                        amount INTEGER NOT NULL CHECK (amount > 0),
                        source_balance_after INTEGER NOT NULL CHECK (source_balance_after >= 0),
                        target_balance_after INTEGER NOT NULL CHECK (target_balance_after >= 0),
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX idx_city_treasury_transfers_city ON city_treasury_transfers(source_city, target_city, created_at)");
            statement.execute("PRAGMA user_version = 6");
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            connection.rollback(); throw error;
        }
    }

    private void createVersionSeven(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE city_market_listings (
                        listing_uuid TEXT PRIMARY KEY,
                        city_uuid TEXT NOT NULL REFERENCES cities(city_uuid) ON DELETE CASCADE,
                        seller_uuid TEXT NOT NULL,
                        item_name TEXT NOT NULL,
                        item_data BLOB NOT NULL,
                        price INTEGER NOT NULL CHECK (price > 0),
                        status TEXT NOT NULL CHECK (status IN ('PREPARING','REMOVING','ACTIVE','SOLD','CANCELLED')),
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX idx_city_market_active ON city_market_listings(status, created_at DESC)");
            statement.execute("""
                    CREATE TABLE city_market_purchases (
                        operation_uuid TEXT PRIMARY KEY,
                        listing_uuid TEXT NOT NULL UNIQUE REFERENCES city_market_listings(listing_uuid),
                        buyer_city TEXT NOT NULL REFERENCES cities(city_uuid),
                        buyer_uuid TEXT NOT NULL,
                        price INTEGER NOT NULL CHECK (price > 0),
                        fee INTEGER NOT NULL CHECK (fee >= 0),
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE city_market_deliveries (
                        delivery_uuid TEXT PRIMARY KEY,
                        operation_uuid TEXT NOT NULL UNIQUE,
                        buyer_uuid TEXT NOT NULL,
                        item_data BLOB NOT NULL,
                        status TEXT NOT NULL CHECK (status IN ('PENDING','DELIVERED')),
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX idx_city_market_deliveries_buyer ON city_market_deliveries(buyer_uuid, status)");
            statement.execute("PRAGMA user_version = 7");
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            connection.rollback(); throw error;
        }
    }

    private void createVersionEight(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE city_admin_test_audit (
                        operation_uuid TEXT PRIMARY KEY,
                        city_uuid TEXT NOT NULL REFERENCES cities(city_uuid) ON DELETE CASCADE,
                        actor_uuid TEXT NOT NULL,
                        target_uuid TEXT NOT NULL,
                        action TEXT NOT NULL CHECK (action IN ('TREASURY_GRANT','STAGE_SET')),
                        amount INTEGER,
                        previous_value TEXT NOT NULL,
                        new_value TEXT NOT NULL,
                        occurred_at TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX idx_city_admin_test_audit_city ON city_admin_test_audit(city_uuid, occurred_at DESC)");
            statement.execute("PRAGMA user_version = 8");
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            connection.rollback(); throw error;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void createVersionNine(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE city_laws (
                        city_uuid TEXT PRIMARY KEY REFERENCES cities(city_uuid) ON DELETE CASCADE,
                        pvp_allowed INTEGER NOT NULL DEFAULT 1 CHECK (pvp_allowed IN (0,1)),
                        outsider_interactions_allowed INTEGER NOT NULL DEFAULT 0 CHECK (outsider_interactions_allowed IN (0,1)),
                        fire_allowed INTEGER NOT NULL DEFAULT 0 CHECK (fire_allowed IN (0,1)),
                        explosions_allowed INTEGER NOT NULL DEFAULT 0 CHECK (explosions_allowed IN (0,1)),
                        updated_by TEXT,
                        updated_at TEXT NOT NULL
                    )
                    """);
            if (tableExists(connection, "cities")) {
                statement.execute("INSERT INTO city_laws(city_uuid, updated_at) SELECT city_uuid, created_at FROM cities");
            }
            statement.execute("""
                    CREATE TABLE city_law_audit (
                        operation_uuid TEXT PRIMARY KEY,
                        city_uuid TEXT NOT NULL REFERENCES cities(city_uuid) ON DELETE CASCADE,
                        actor_uuid TEXT NOT NULL,
                        old_values TEXT NOT NULL,
                        new_values TEXT NOT NULL,
                        occurred_at TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX idx_city_law_audit_city ON city_law_audit(city_uuid, occurred_at DESC)");
            statement.execute("PRAGMA user_version = 9");
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            connection.rollback(); throw error;
        } finally { connection.setAutoCommit(true); }
    }

    private boolean tableExists(Connection connection, String name) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            query.setString(1, name);
            try (ResultSet result = query.executeQuery()) { return result.next(); }
        }
    }

    private void createVersionTen(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE city_tax_policy (
                        city_uuid TEXT PRIMARY KEY REFERENCES cities(city_uuid) ON DELETE CASCADE,
                        enabled INTEGER NOT NULL DEFAULT 0 CHECK (enabled IN (0,1)),
                        daily_amount INTEGER NOT NULL DEFAULT 0 CHECK (daily_amount BETWEEN 0 AND 100000),
                        next_collection_at TEXT,
                        updated_by TEXT,
                        updated_at TEXT NOT NULL
                    )
                    """);
            if (tableExists(connection, "cities")) {
                statement.execute("INSERT INTO city_tax_policy(city_uuid, updated_at) SELECT city_uuid, created_at FROM cities");
            }
            statement.execute("""
                    CREATE TABLE city_tax_policy_audit (
                        operation_uuid TEXT PRIMARY KEY,
                        city_uuid TEXT NOT NULL REFERENCES cities(city_uuid) ON DELETE CASCADE,
                        actor_uuid TEXT NOT NULL,
                        old_enabled INTEGER NOT NULL,
                        old_amount INTEGER NOT NULL,
                        new_enabled INTEGER NOT NULL,
                        new_amount INTEGER NOT NULL,
                        occurred_at TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX idx_city_tax_policy_audit_city ON city_tax_policy_audit(city_uuid, occurred_at DESC)");
            statement.execute("PRAGMA user_version = 10");
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            connection.rollback(); throw error;
        } finally { connection.setAutoCommit(true); }
    }

    private void createVersionEleven(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE city_treasury_recovery_audit (
                        audit_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        operation_uuid TEXT NOT NULL,
                        actor_uuid TEXT NOT NULL,
                        outcome TEXT NOT NULL,
                        occurred_at TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX idx_city_treasury_recovery_audit_operation ON city_treasury_recovery_audit(operation_uuid, audit_id DESC)");
            statement.execute("PRAGMA user_version = 11");
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            connection.rollback(); throw error;
        } finally { connection.setAutoCommit(true); }
    }

    private void createVersionTwelve(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX idx_city_market_city_status_created ON city_market_listings(city_uuid, status, created_at DESC)");
            statement.execute("CREATE INDEX idx_city_market_seller_status_created ON city_market_listings(seller_uuid, status, created_at)");
            statement.execute("CREATE INDEX idx_city_operations_type_state_created ON city_operations(operation_type, state, created_at)");
            statement.execute("CREATE INDEX idx_city_treaties_proposing_created ON city_treaties(proposing_city, created_at DESC)");
            statement.execute("CREATE INDEX idx_city_treaties_target_created ON city_treaties(target_city, created_at DESC)");
            statement.execute("CREATE INDEX idx_city_tax_due ON city_tax_policy(enabled, next_collection_at)");
            statement.execute("CREATE INDEX idx_city_members_city_joined ON city_members(city_uuid, joined_at, player_uuid)");
            statement.execute("PRAGMA user_version = 12");
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            connection.rollback(); throw error;
        } finally { connection.setAutoCommit(true); }
    }

    private void createVersionOne(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE cities (
                        city_uuid TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        name_key TEXT NOT NULL UNIQUE,
                        stage TEXT NOT NULL CHECK (stage IN ('SETTLEMENT','VILLAGE','CITY','CAPITAL','KINGDOM')),
                        treasury INTEGER NOT NULL DEFAULT 0 CHECK (treasury >= 0),
                        revision INTEGER NOT NULL DEFAULT 1 CHECK (revision >= 1),
                        founder_uuid TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE city_members (
                        city_uuid TEXT NOT NULL REFERENCES cities(city_uuid) ON DELETE CASCADE,
                        player_uuid TEXT NOT NULL UNIQUE,
                        last_known_name TEXT NOT NULL,
                        role_id TEXT NOT NULL,
                        joined_at TEXT NOT NULL,
                        PRIMARY KEY (city_uuid, player_uuid)
                    )
                    """);
            statement.execute("CREATE INDEX idx_city_members_player ON city_members(player_uuid)");
            statement.execute("""
                    CREATE TABLE city_chunks (
                        world_uuid TEXT NOT NULL,
                        chunk_x INTEGER NOT NULL,
                        chunk_z INTEGER NOT NULL,
                        city_uuid TEXT NOT NULL REFERENCES cities(city_uuid) ON DELETE CASCADE,
                        claimed_at TEXT NOT NULL,
                        PRIMARY KEY (world_uuid, chunk_x, chunk_z)
                    )
                    """);
            statement.execute("CREATE INDEX idx_city_chunks_owner ON city_chunks(city_uuid)");
            statement.execute("""
                    CREATE TABLE city_operations (
                        operation_uuid TEXT PRIMARY KEY,
                        operation_type TEXT NOT NULL,
                        request_hash TEXT NOT NULL,
                        request_payload TEXT NOT NULL,
                        state TEXT NOT NULL CHECK (state IN ('PREPARED','RESERVED','DOMAIN_COMMITTED',
                            'COMMIT_PENDING','RELEASE_PENDING','COMPLETED','RELEASED','FAILED','UNKNOWN')),
                        result_code TEXT NOT NULL,
                        city_uuid TEXT,
                        actor_uuid TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX idx_city_operations_state ON city_operations(state, updated_at)");
            statement.execute("""
                    CREATE TABLE city_treasury_entries (
                        operation_uuid TEXT PRIMARY KEY,
                        city_uuid TEXT NOT NULL REFERENCES cities(city_uuid),
                        amount INTEGER NOT NULL CHECK (amount <> 0),
                        balance_after INTEGER NOT NULL CHECK (balance_after >= 0),
                        reason TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX idx_city_treasury_history ON city_treasury_entries(city_uuid, created_at)");
            statement.execute("PRAGMA user_version=1");
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void createVersionTwo(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE city_invites (
                        player_uuid TEXT PRIMARY KEY,
                        city_uuid TEXT NOT NULL REFERENCES cities(city_uuid) ON DELETE CASCADE,
                        inviter_uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        expires_at TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX idx_city_invites_city ON city_invites(city_uuid)");
            statement.execute("PRAGMA user_version=2");
            connection.commit();
        } catch (SQLException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void createVersionThree(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("UPDATE city_members SET role_id='MEMBER' WHERE role_id='RESIDENT'");
            statement.execute("""
                    CREATE TABLE city_role_audit (
                        audit_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        city_uuid TEXT NOT NULL REFERENCES cities(city_uuid) ON DELETE CASCADE,
                        actor_uuid TEXT NOT NULL,
                        target_uuid TEXT NOT NULL,
                        previous_role TEXT NOT NULL,
                        new_role TEXT NOT NULL,
                        occurred_at TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX idx_city_role_audit_city ON city_role_audit(city_uuid, audit_id DESC)");
            statement.execute("PRAGMA user_version=3");
            connection.commit();
        } catch (SQLException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void createVersionFour(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE city_upgrades (
                        city_uuid TEXT NOT NULL REFERENCES cities(city_uuid) ON DELETE CASCADE,
                        upgrade_id TEXT NOT NULL CHECK (upgrade_id IN ('CITY_WARDING','CITY_GUARDS')),
                        purchased_by TEXT NOT NULL,
                        purchased_at TEXT NOT NULL,
                        PRIMARY KEY (city_uuid, upgrade_id)
                    )
                    """);
            statement.execute("PRAGMA user_version=4");
            connection.commit();
        } catch (SQLException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public CompletionStage<Optional<CityView>> find(UUID cityId) {
        return async(() -> readCity(cityId));
    }

    @Override
    public CompletionStage<Optional<CityView>> findForPlayer(UUID playerId) {
        return async(() -> {
            try (Connection connection = connection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT city_uuid FROM city_members WHERE player_uuid = ?")) {
                    query.setString(1, playerId.toString());
                    UUID cityId;
                    try (ResultSet result = query.executeQuery()) {
                        cityId = result.next() ? UUID.fromString(result.getString("city_uuid")) : null;
                    }
                    Optional<CityView> city = cityId == null ? Optional.empty() : readCity(connection, cityId);
                    connection.commit();
                    return city;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Unable to load a player's city", e);
            }
        });
    }

    @Override
    public CompletionStage<Optional<CityView>> adminTestGrantTreasury(UUID operationId, UUID actorId,
                                                                       UUID targetPlayerId, long amount) {
        if (amount <= 0 || amount > 1_000_000_000L) throw new IllegalArgumentException("Test grant must be between 1 and 1000000000");
        return async(() -> adminTestTransaction(connection -> {
            CityView city = readCityForPlayer(connection, targetPlayerId);
            if (city == null) { connection.commit(); return Optional.empty(); }
            long balance;
            try { balance = Math.addExact(city.treasury(), amount); }
            catch (ArithmeticException overflow) { throw new SQLException("City treasury overflow", overflow); }
            try (PreparedStatement update = connection.prepareStatement("UPDATE cities SET treasury=?, revision=revision+1 WHERE city_uuid=? AND revision=?")) {
                update.setLong(1, balance); update.setString(2, city.id().toString()); update.setLong(3, city.revision());
                if (update.executeUpdate() != 1) throw new SQLException("City changed during test grant");
            }
            insertTreasuryEntry(connection, operationId, city.id(), amount, "ADMIN_TEST_GRANT");
            insertAdminTestAudit(connection, operationId, city.id(), actorId, targetPlayerId,
                    "TREASURY_GRANT", amount, Long.toString(city.treasury()), Long.toString(balance));
            CityView updated = readCity(connection, city.id()).orElseThrow();
            connection.commit();
            return Optional.of(updated);
        }));
    }

    @Override
    public CompletionStage<Optional<CityView>> adminTestSetStage(UUID operationId, UUID actorId,
                                                                  UUID targetPlayerId, CityStage stage) {
        Objects.requireNonNull(stage, "stage");
        return async(() -> adminTestTransaction(connection -> {
            CityView city = readCityForPlayer(connection, targetPlayerId);
            if (city == null) { connection.commit(); return Optional.empty(); }
            try (PreparedStatement update = connection.prepareStatement("UPDATE cities SET stage=?, revision=revision+1 WHERE city_uuid=? AND revision=?")) {
                update.setString(1, stage.name()); update.setString(2, city.id().toString()); update.setLong(3, city.revision());
                if (update.executeUpdate() != 1) throw new SQLException("City changed during test stage update");
            }
            insertAdminTestAudit(connection, operationId, city.id(), actorId, targetPlayerId,
                    "STAGE_SET", null, city.stage().name(), stage.name());
            CityView updated = readCity(connection, city.id()).orElseThrow();
            connection.commit();
            return Optional.of(updated);
        }));
    }

    private CityView readCityForPlayer(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT city_uuid FROM city_members WHERE player_uuid=?")) {
            query.setString(1, playerId.toString());
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? readCity(connection, UUID.fromString(result.getString(1))).orElse(null) : null;
            }
        }
    }

    private void insertAdminTestAudit(Connection connection, UUID operationId, UUID cityId, UUID actorId,
                                      UUID targetPlayerId, String action, Long amount,
                                      String previousValue, String newValue) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO city_admin_test_audit(operation_uuid,city_uuid,actor_uuid,target_uuid,action,amount,previous_value,new_value,occurred_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """)) {
            insert.setString(1, operationId.toString()); insert.setString(2, cityId.toString());
            insert.setString(3, actorId.toString()); insert.setString(4, targetPlayerId.toString());
            insert.setString(5, action);
            if (amount == null) insert.setNull(6, java.sql.Types.INTEGER); else insert.setLong(6, amount);
            insert.setString(7, previousValue); insert.setString(8, newValue); insert.setString(9, Instant.now().toString());
            insert.executeUpdate();
        }
    }

    private <T> T adminTestTransaction(SqlTransaction<T> action) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try { return action.run(connection); }
            catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
        }
    }

    @Override
    public CompletionStage<List<CityView>> findAllCities() {
        return async(() -> {
            try (Connection connection = connection(); Statement query = connection.createStatement();
                 ResultSet rows = query.executeQuery("SELECT city_uuid FROM cities ORDER BY name_key")) {
                List<CityView> cities = new ArrayList<>();
                while (rows.next()) {
                    UUID cityId = UUID.fromString(rows.getString("city_uuid"));
                    readCity(connection, cityId).ifPresent(cities::add);
                }
                return List.copyOf(cities);
            }
        });
    }

    @Override
    public CompletionStage<List<CityTreatyView>> diplomacyTreaties(UUID cityId) {
        return async(() -> diplomacyTransaction(connection -> {
            expireTreaties(connection, cityId);
            List<CityTreatyView> treaties = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT treaty_uuid FROM city_treaties
                    WHERE proposing_city=? OR target_city=? ORDER BY created_at DESC LIMIT 30
                    """)) {
                query.setString(1, cityId.toString()); query.setString(2, cityId.toString());
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) readTreaty(connection, UUID.fromString(rows.getString(1))).ifPresent(treaties::add);
                }
            }
            return List.copyOf(treaties);
        }));
    }

    @Override
    public CompletionStage<List<CityDiplomacyEvent>> diplomacyHistory(UUID cityId, int limit) {
        int capped = Math.max(1, Math.min(limit, 50));
        return async(() -> diplomacyTransaction(connection -> {
            expireTreaties(connection, cityId);
            List<CityDiplomacyEvent> events = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT e.event_uuid,e.treaty_uuid,e.proposing_city_name,e.target_city_name,e.event_code,e.occurred_at
                    FROM city_diplomacy_events e JOIN city_treaties t ON t.treaty_uuid=e.treaty_uuid
                    WHERE t.proposing_city=? OR t.target_city=? ORDER BY e.occurred_at DESC LIMIT ?
                    """)) {
                query.setString(1, cityId.toString()); query.setString(2, cityId.toString()); query.setInt(3, capped);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) events.add(new CityDiplomacyEvent(UUID.fromString(rows.getString(1)),
                            UUID.fromString(rows.getString(2)), rows.getString(3), rows.getString(4),
                            rows.getString(5), Instant.parse(rows.getString(6))));
                }
            }
            return List.copyOf(events);
        }));
    }

    @Override
    public CompletionStage<CityDiplomacyResult> executeDiplomacy(CityDiplomacyRequest request) {
        return async(() -> diplomacyTransaction(connection -> executeDiplomacy(connection, request)));
    }

    private CityDiplomacyResult executeDiplomacy(Connection connection, CityDiplomacyRequest request) throws SQLException {
        expireTreaties(connection, request.cityId());
        String role;
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT role_id FROM city_members WHERE city_uuid=? AND player_uuid=?")) {
            query.setString(1, request.cityId().toString()); query.setString(2, request.actorId().toString());
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return CityDiplomacyResult.of(CityDiplomacyCode.NOT_MEMBER);
                role = row.getString(1);
            }
        }
        if (!CityRole.fromStorage(role).allows(ru.servermine.cities.api.CityPermission.MANAGE_DIPLOMACY))
            return CityDiplomacyResult.of(CityDiplomacyCode.PERMISSION_DENIED);
        String cityName = cityName(connection, request.cityId());
        if (cityName == null) return CityDiplomacyResult.of(CityDiplomacyCode.NOT_FOUND);
        String now = Instant.now().toString();
        CityDiplomacyCode result;
        switch (request.action()) {
            case PROPOSE -> {
                if (request.cityId().equals(request.targetCityId())) return CityDiplomacyResult.of(CityDiplomacyCode.SELF_TARGET);
                String targetName = cityName(connection, request.targetCityId());
                if (targetName == null) return CityDiplomacyResult.of(CityDiplomacyCode.INVALID_TARGET);
                try (PreparedStatement exists = connection.prepareStatement("""
                        SELECT 1 FROM city_treaties WHERE treaty_type=? AND status IN ('PENDING','ACTIVE')
                        AND ((proposing_city=? AND target_city=?) OR (proposing_city=? AND target_city=?)) LIMIT 1
                        """)) {
                    exists.setString(1, request.type().name()); exists.setString(2, request.cityId().toString());
                    exists.setString(3, request.targetCityId().toString()); exists.setString(4, request.targetCityId().toString());
                    exists.setString(5, request.cityId().toString());
                    try (ResultSet row = exists.executeQuery()) { if (row.next()) return CityDiplomacyResult.of(CityDiplomacyCode.ALREADY_EXISTS); }
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO city_treaties(treaty_uuid,proposing_city,target_city,treaty_type,status,duration_days,
                        proposed_by,created_at,expires_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)
                        """)) {
                    String expiry = Instant.now().plus(7, ChronoUnit.DAYS).toString();
                    insert.setString(1, request.treatyId().toString()); insert.setString(2, request.cityId().toString());
                    insert.setString(3, request.targetCityId().toString()); insert.setString(4, request.type().name());
                    insert.setString(5, CityTreatyState.PENDING.name()); insert.setInt(6, request.durationDays());
                    insert.setString(7, request.actorId().toString()); insert.setString(8, now); insert.setString(9, expiry); insert.setString(10, now);
                    insert.executeUpdate();
                } catch (java.sql.SQLIntegrityConstraintViolationException duplicate) {
                    return CityDiplomacyResult.of(CityDiplomacyCode.ALREADY_EXISTS);
                }
                insertDiplomacyEvent(connection, request.treatyId(), cityName, targetName, "PROPOSED", now);
                result = CityDiplomacyCode.CREATED;
            }
            case ACCEPT, DECLINE, CANCEL, TERMINATE -> {
                CityTreatyView treaty = readTreaty(connection, request.treatyId()).orElse(null);
                if (treaty == null) return CityDiplomacyResult.of(CityDiplomacyCode.NOT_FOUND);
                if (!request.cityId().equals(treaty.proposingCityId()) && !request.cityId().equals(treaty.targetCityId()))
                    return CityDiplomacyResult.of(CityDiplomacyCode.WRONG_SIDE);
                String next;
                String event;
                switch (request.action()) {
                    case ACCEPT -> {
                        if (!request.cityId().equals(treaty.targetCityId())) return CityDiplomacyResult.of(CityDiplomacyCode.WRONG_SIDE);
                        if (treaty.state() != CityTreatyState.PENDING) return CityDiplomacyResult.of(CityDiplomacyCode.INVALID_STATE);
                        next = CityTreatyState.ACTIVE.name(); event = "ACCEPTED";
                    }
                    case DECLINE -> {
                        if (!request.cityId().equals(treaty.targetCityId())) return CityDiplomacyResult.of(CityDiplomacyCode.WRONG_SIDE);
                        if (treaty.state() != CityTreatyState.PENDING) return CityDiplomacyResult.of(CityDiplomacyCode.INVALID_STATE);
                        next = CityTreatyState.DECLINED.name(); event = "DECLINED";
                    }
                    case CANCEL -> {
                        if (!request.cityId().equals(treaty.proposingCityId())) return CityDiplomacyResult.of(CityDiplomacyCode.WRONG_SIDE);
                        if (treaty.state() != CityTreatyState.PENDING) return CityDiplomacyResult.of(CityDiplomacyCode.INVALID_STATE);
                        next = CityTreatyState.CANCELLED.name(); event = "WITHDRAWN";
                    }
                    default -> {
                        if (treaty.state() != CityTreatyState.ACTIVE) return CityDiplomacyResult.of(CityDiplomacyCode.INVALID_STATE);
                        next = CityTreatyState.TERMINATED.name(); event = "TERMINATED";
                    }
                }
                // Active term starts when both parties accept; other transitions retain the original deadline.
                String expiry = request.action() == CityDiplomacyRequest.Action.ACCEPT
                        ? Instant.now().plus(treatyDuration(connection, request.treatyId()), ChronoUnit.DAYS).toString()
                        : treaty.expiresAt().toString();
                try (PreparedStatement update = connection.prepareStatement("UPDATE city_treaties SET status=?,expires_at=?,updated_at=? WHERE treaty_uuid=? AND status=?")) {
                    update.setString(1, next); update.setString(2, expiry); update.setString(3, now);
                    update.setString(4, request.treatyId().toString()); update.setString(5, treaty.state().name());
                    if (update.executeUpdate() != 1) return CityDiplomacyResult.of(CityDiplomacyCode.INVALID_STATE);
                }
                insertDiplomacyEvent(connection, request.treatyId(), treaty.proposingCityName(), treaty.targetCityName(), event, now);
                result = switch (request.action()) {
                    case ACCEPT -> CityDiplomacyCode.ACCEPTED; case DECLINE -> CityDiplomacyCode.DECLINED;
                    case CANCEL -> CityDiplomacyCode.CANCELLED; default -> CityDiplomacyCode.TERMINATED;
                };
            }
            default -> throw new IllegalStateException("Unsupported diplomacy action");
        }
        CityTreatyView saved = readTreaty(connection, request.treatyId()).orElse(null);
        return new CityDiplomacyResult(result, Optional.ofNullable(saved));
    }

    private int treatyDuration(Connection connection, UUID treatyId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT duration_days FROM city_treaties WHERE treaty_uuid=?")) {
            query.setString(1, treatyId.toString()); try (ResultSet row = query.executeQuery()) { return row.next() ? row.getInt(1) : 30; }
        }
    }

    private String cityName(Connection connection, UUID cityId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT name FROM cities WHERE city_uuid=?")) {
            query.setString(1, cityId.toString()); try (ResultSet row = query.executeQuery()) { return row.next() ? row.getString(1) : null; }
        }
    }

    private void expireTreaties(Connection connection, UUID cityId) throws SQLException {
        String now = Instant.now().toString();
        List<UUID> expired = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT treaty_uuid FROM city_treaties WHERE (proposing_city=? OR target_city=?)
                AND status IN ('PENDING','ACTIVE') AND expires_at<=?
                """)) {
            query.setString(1, cityId.toString()); query.setString(2, cityId.toString()); query.setString(3, now);
            try (ResultSet rows = query.executeQuery()) { while (rows.next()) expired.add(UUID.fromString(rows.getString(1))); }
        }
        for (UUID id : expired) {
            CityTreatyView treaty = readTreaty(connection, id).orElse(null); if (treaty == null) continue;
            try (PreparedStatement update = connection.prepareStatement("UPDATE city_treaties SET status='EXPIRED',updated_at=? WHERE treaty_uuid=? AND status IN ('PENDING','ACTIVE')")) {
                update.setString(1, now); update.setString(2, id.toString()); update.executeUpdate();
            }
            insertDiplomacyEvent(connection, id, treaty.proposingCityName(), treaty.targetCityName(), "EXPIRED", now);
        }
    }

    private void insertDiplomacyEvent(Connection connection, UUID treatyId, String proposing, String target,
                                      String code, String occurredAt) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO city_diplomacy_events VALUES(?,?,?,?,?,?)")) {
            insert.setString(1, UUID.randomUUID().toString()); insert.setString(2, treatyId.toString());
            insert.setString(3, proposing); insert.setString(4, target); insert.setString(5, code); insert.setString(6, occurredAt);
            insert.executeUpdate();
        }
    }

    private Optional<CityTreatyView> readTreaty(Connection connection, UUID treatyId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT t.*,p.name proposing_name,c.name target_name FROM city_treaties t
                JOIN cities p ON p.city_uuid=t.proposing_city JOIN cities c ON c.city_uuid=t.target_city WHERE t.treaty_uuid=?
                """)) {
            query.setString(1, treatyId.toString()); try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return Optional.empty();
                return Optional.of(new CityTreatyView(UUID.fromString(row.getString("treaty_uuid")),
                        UUID.fromString(row.getString("proposing_city")), row.getString("proposing_name"),
                        UUID.fromString(row.getString("target_city")), row.getString("target_name"),
                        CityTreatyType.valueOf(row.getString("treaty_type")), CityTreatyState.valueOf(row.getString("status")),
                        Instant.parse(row.getString("created_at")), Instant.parse(row.getString("expires_at"))));
            }
        }
    }

    private <T> T diplomacyTransaction(SqlTransaction<T> action) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try { T result = action.run(connection); connection.commit(); return result; }
            catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
        }
    }

    @Override
    public CompletionStage<CityLawsResult> updateCityLaws(CityLawsRequest request) {
        return async(() -> {
            try (Connection connection = connection()) {
                connection.setAutoCommit(false);
                try {
                    CityView current = readCity(connection, request.cityId()).orElse(null);
                    if (current == null) return lawsResult(connection, CityLawsCode.NO_CITY);
                    if (current.revision() != request.expectedRevision()) return lawsResult(connection, CityLawsCode.STALE_CITY);
                    CityRole role = null;
                    try (PreparedStatement query = connection.prepareStatement(
                            "SELECT role_id FROM city_members WHERE city_uuid=? AND player_uuid=?")) {
                        query.setString(1, request.cityId().toString());
                        query.setString(2, request.actorId().toString());
                        try (ResultSet result = query.executeQuery()) {
                            if (result.next()) role = CityRole.fromStorage(result.getString(1));
                        }
                    }
                    if (role == null || !role.allows(ru.servermine.cities.api.CityPermission.MANAGE_LAWS))
                        return lawsResult(connection, CityLawsCode.PERMISSION_DENIED);
                    CityLaws old = current.laws();
                    CityLaws next = request.laws();
                    String now = Instant.now().toString();
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE city_laws SET pvp_allowed=?, outsider_interactions_allowed=?, fire_allowed=?,
                                explosions_allowed=?, updated_by=?, updated_at=? WHERE city_uuid=?
                            """)) {
                        update.setInt(1, next.pvpAllowed() ? 1 : 0);
                        update.setInt(2, next.outsiderInteractionsAllowed() ? 1 : 0);
                        update.setInt(3, next.fireAllowed() ? 1 : 0);
                        update.setInt(4, next.explosionsAllowed() ? 1 : 0);
                        update.setString(5, request.actorId().toString());
                        update.setString(6, now);
                        update.setString(7, request.cityId().toString());
                        if (update.executeUpdate() != 1) throw new SQLException("City laws row missing");
                    }
                    try (PreparedStatement revision = connection.prepareStatement(
                            "UPDATE cities SET revision=revision+1 WHERE city_uuid=? AND revision=?")) {
                        revision.setString(1, request.cityId().toString());
                        revision.setLong(2, request.expectedRevision());
                        if (revision.executeUpdate() != 1) throw new SQLException("City revision changed during laws update");
                    }
                    try (PreparedStatement audit = connection.prepareStatement("""
                            INSERT INTO city_law_audit(operation_uuid, city_uuid, actor_uuid, old_values, new_values, occurred_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """)) {
                        audit.setString(1, UUID.randomUUID().toString());
                        audit.setString(2, request.cityId().toString());
                        audit.setString(3, request.actorId().toString());
                        audit.setString(4, lawsPayload(old));
                        audit.setString(5, lawsPayload(next));
                        audit.setString(6, now);
                        audit.executeUpdate();
                    }
                    CityView updated = readCity(connection, request.cityId()).orElseThrow();
                    connection.commit();
                    return new CityLawsResult(CityLawsCode.UPDATED, Optional.of(updated));
                } catch (SQLException | RuntimeException error) {
                    connection.rollback(); throw error;
                }
            }
        });
    }

    private CityLawsResult lawsResult(Connection connection, CityLawsCode code) throws SQLException {
        connection.commit();
        return new CityLawsResult(code, Optional.empty());
    }

    private String lawsPayload(CityLaws laws) {
        return "pvp=" + laws.pvpAllowed() + ";outsider-interactions=" + laws.outsiderInteractionsAllowed()
                + ";fire=" + laws.fireAllowed() + ";explosions=" + laws.explosionsAllowed();
    }

    @Override
    public CompletionStage<CityTaxResult> updateCityTaxPolicy(CityTaxRequest request) {
        return async(() -> {
            if (request.dailyAmount() < 0 || request.dailyAmount() > 100_000
                    || (request.enabled() && request.dailyAmount() == 0)) {
                return new CityTaxResult(CityTaxCode.INVALID_AMOUNT, Optional.empty());
            }
            try (Connection connection = connection()) {
                connection.setAutoCommit(false);
                try {
                    CityView current = readCity(connection, request.cityId()).orElse(null);
                    if (current == null) return taxResult(connection, CityTaxCode.NO_CITY);
                    if (current.revision() != request.expectedRevision()) return taxResult(connection, CityTaxCode.STALE_CITY);
                    CityRole role = null;
                    try (PreparedStatement query = connection.prepareStatement(
                            "SELECT role_id FROM city_members WHERE city_uuid=? AND player_uuid=?")) {
                        query.setString(1, request.cityId().toString()); query.setString(2, request.actorId().toString());
                        try (ResultSet result = query.executeQuery()) {
                            if (result.next()) role = CityRole.fromStorage(result.getString(1));
                        }
                    }
                    if (role == null || !role.allows(ru.servermine.cities.api.CityPermission.MANAGE_TAXES))
                        return taxResult(connection, CityTaxCode.PERMISSION_DENIED);

                    CityTaxPolicy old = current.taxPolicy();
                    String now = Instant.now().toString();
                    String nextCollection = request.enabled() ? Instant.now().plus(1, ChronoUnit.DAYS).toString() : null;
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE city_tax_policy SET enabled=?, daily_amount=?, next_collection_at=?, updated_by=?, updated_at=?
                            WHERE city_uuid=?
                            """)) {
                        update.setInt(1, request.enabled() ? 1 : 0); update.setLong(2, request.dailyAmount());
                        update.setString(3, nextCollection); update.setString(4, request.actorId().toString());
                        update.setString(5, now); update.setString(6, request.cityId().toString());
                        if (update.executeUpdate() != 1) throw new SQLException("City tax policy row missing");
                    }
                    try (PreparedStatement audit = connection.prepareStatement("""
                            INSERT INTO city_tax_policy_audit(operation_uuid, city_uuid, actor_uuid, old_enabled,
                                old_amount, new_enabled, new_amount, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """)) {
                        audit.setString(1, UUID.randomUUID().toString()); audit.setString(2, request.cityId().toString());
                        audit.setString(3, request.actorId().toString()); audit.setInt(4, old.enabled() ? 1 : 0);
                        audit.setLong(5, old.dailyAmount()); audit.setInt(6, request.enabled() ? 1 : 0);
                        audit.setLong(7, request.dailyAmount()); audit.setString(8, now); audit.executeUpdate();
                    }
                    try (PreparedStatement revision = connection.prepareStatement(
                            "UPDATE cities SET revision=revision+1 WHERE city_uuid=? AND revision=?")) {
                        revision.setString(1, request.cityId().toString()); revision.setLong(2, request.expectedRevision());
                        if (revision.executeUpdate() != 1) throw new SQLException("City revision changed during tax update");
                    }
                    CityView updated = readCity(connection, request.cityId()).orElseThrow();
                    connection.commit();
                    return new CityTaxResult(CityTaxCode.UPDATED, Optional.of(updated));
                } catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
            }
        });
    }

    private CityTaxResult taxResult(Connection connection, CityTaxCode code) throws SQLException {
        connection.commit(); return new CityTaxResult(code, Optional.empty());
    }

    @Override
    public CompletionStage<Boolean> completeCityTaxCycle(UUID cityId, Instant expectedCollection, Instant nextCollection) {
        return async(() -> {
            try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement("""
                    UPDATE city_tax_policy SET next_collection_at=?
                    WHERE city_uuid=? AND enabled=1 AND next_collection_at=?
                    """)) {
                update.setString(1, nextCollection.toString()); update.setString(2, cityId.toString());
                update.setString(3, expectedCollection.toString());
                return update.executeUpdate() == 1;
            }
        });
    }

    @Override
    public CompletionStage<List<CityTaxDue>> dueCityTaxes(Instant now) {
        return async(() -> {
            List<CityTaxDue> due = new ArrayList<>();
            try (Connection connection = connection(); PreparedStatement query = connection.prepareStatement("""
                    SELECT c.city_uuid, c.name, p.daily_amount, p.next_collection_at
                    FROM city_tax_policy p JOIN cities c ON c.city_uuid=p.city_uuid
                    WHERE p.enabled=1 AND p.next_collection_at IS NOT NULL AND p.next_collection_at<=?
                    ORDER BY p.next_collection_at, c.city_uuid
                    """)) {
                query.setString(1, now.toString());
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        UUID cityId = UUID.fromString(rows.getString("city_uuid"));
                        List<CityView.ResidentView> residents = new ArrayList<>();
                        try (PreparedStatement members = connection.prepareStatement("""
                                SELECT player_uuid, last_known_name, role_id FROM city_members
                                WHERE city_uuid=? ORDER BY joined_at, player_uuid
                                """)) {
                            members.setString(1, cityId.toString());
                            try (ResultSet memberRows = members.executeQuery()) {
                                while (memberRows.next()) residents.add(new CityView.ResidentView(
                                        UUID.fromString(memberRows.getString("player_uuid")),
                                        memberRows.getString("last_known_name"), memberRows.getString("role_id")));
                            }
                        }
                        due.add(new CityTaxDue(cityId, rows.getString("name"), rows.getLong("daily_amount"),
                                Instant.parse(rows.getString("next_collection_at")), residents));
                    }
                }
            }
            return List.copyOf(due);
        });
    }

    @Override
    public CompletionStage<CityLeaveResult> leaveCity(UUID playerId) {
        return async(() -> leaveCityTransaction(playerId));
    }

    private CityLeaveResult leaveCityTransaction(UUID playerId) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                UUID cityId = null;
                String role = null;
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT city_uuid, role_id FROM city_members WHERE player_uuid = ?")) {
                    query.setString(1, playerId.toString());
                    try (ResultSet result = query.executeQuery()) {
                        if (result.next()) {
                            cityId = UUID.fromString(result.getString("city_uuid"));
                            role = result.getString("role_id");
                        }
                    }
                }
                if (cityId == null) {
                    connection.commit();
                    return new CityLeaveResult(CityLeaveCode.NOT_IN_CITY, Optional.empty(), Optional.empty());
                }

                UUID newRulerId = null;
                String successorPreviousRole = null;
                if ("RULER".equals(role)) {
                    try (PreparedStatement query = connection.prepareStatement("""
                            SELECT player_uuid, role_id FROM city_members
                            WHERE city_uuid = ? AND player_uuid <> ?
                            ORDER BY joined_at, player_uuid LIMIT 1
                            """)) {
                        query.setString(1, cityId.toString());
                        query.setString(2, playerId.toString());
                        try (ResultSet result = query.executeQuery()) {
                            if (result.next()) {
                                newRulerId = UUID.fromString(result.getString("player_uuid"));
                                successorPreviousRole = result.getString("role_id");
                            }
                        }
                    }
                    if (newRulerId == null) {
                        connection.rollback();
                        return new CityLeaveResult(CityLeaveCode.LAST_RULER, Optional.empty(), Optional.empty());
                    }
                    try (PreparedStatement audit = connection.prepareStatement("""
                            INSERT INTO city_role_audit(city_uuid, actor_uuid, target_uuid, previous_role, new_role, occurred_at)
                            VALUES (?, ?, ?, ?, 'RULER', ?)
                            """)) {
                        audit.setString(1, cityId.toString());
                        audit.setString(2, playerId.toString());
                        audit.setString(3, newRulerId.toString());
                        audit.setString(4, CityRole.fromStorage(successorPreviousRole).name());
                        audit.setString(5, Instant.now().toString());
                        audit.executeUpdate();
                    }
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE city_members SET role_id = 'RULER' WHERE city_uuid = ? AND player_uuid = ?")) {
                        update.setString(1, cityId.toString());
                        update.setString(2, newRulerId.toString());
                        if (update.executeUpdate() != 1) throw new SQLException("Successor membership changed during departure");
                    }
                }

                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM city_members WHERE city_uuid = ? AND player_uuid = ?")) {
                    delete.setString(1, cityId.toString());
                    delete.setString(2, playerId.toString());
                    if (delete.executeUpdate() != 1) throw new SQLException("Membership changed during departure");
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE cities SET revision = revision + 1 WHERE city_uuid = ?")) {
                    update.setString(1, cityId.toString());
                    if (update.executeUpdate() != 1) throw new SQLException("City disappeared during departure");
                }
                CityView city = readCity(connection, cityId).orElseThrow(
                        () -> new SQLException("City disappeared after member departure"));
                connection.commit();
                return new CityLeaveResult(CityLeaveCode.LEFT, Optional.of(city), Optional.ofNullable(newRulerId));
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    @Override
    public CompletionStage<CityMembershipResult> inviteToCity(UUID inviterId, UUID inviteeId, String inviteeName) {
        return async(() -> inviteToCityTransaction(inviterId, inviteeId, inviteeName));
    }

    private CityMembershipResult inviteToCityTransaction(UUID inviterId, UUID inviteeId, String inviteeName)
            throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                UUID cityId = null;
                String role = null;
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT city_uuid, role_id FROM city_members WHERE player_uuid = ?")) {
                    query.setString(1, inviterId.toString());
                    try (ResultSet result = query.executeQuery()) {
                        if (result.next()) {
                            cityId = UUID.fromString(result.getString("city_uuid"));
                            role = result.getString("role_id");
                        }
                    }
                }
                if (cityId == null) {
                    connection.commit();
                    return membershipResult(CityMembershipCode.NO_CITY);
                }
                if (!CityRole.fromStorage(role).allows(ru.servermine.cities.api.CityPermission.INVITE_MEMBERS)) {
                    connection.commit();
                    return membershipResult(CityMembershipCode.PERMISSION_DENIED);
                }
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT 1 FROM city_members WHERE player_uuid = ?")) {
                    query.setString(1, inviteeId.toString());
                    try (ResultSet result = query.executeQuery()) {
                        if (result.next()) {
                            connection.commit();
                            return membershipResult(CityMembershipCode.TARGET_ALREADY_IN_CITY);
                        }
                    }
                }
                Instant now = Instant.now();
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO city_invites(player_uuid, city_uuid, inviter_uuid, player_name, created_at, expires_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT(player_uuid) DO UPDATE SET city_uuid=excluded.city_uuid,
                            inviter_uuid=excluded.inviter_uuid, player_name=excluded.player_name,
                            created_at=excluded.created_at, expires_at=excluded.expires_at
                        """)) {
                    insert.setString(1, inviteeId.toString());
                    insert.setString(2, cityId.toString());
                    insert.setString(3, inviterId.toString());
                    insert.setString(4, inviteeName);
                    insert.setString(5, now.toString());
                    insert.setString(6, now.plus(48, ChronoUnit.HOURS).toString());
                    insert.executeUpdate();
                }
                CityView city = readCity(connection, cityId).orElseThrow(
                        () -> new SQLException("City disappeared while inviting a member"));
                connection.commit();
                return new CityMembershipResult(CityMembershipCode.INVITED, Optional.of(city));
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    @Override
    public CompletionStage<CityMembershipResult> acceptCityInvite(UUID playerId, String playerName) {
        return async(() -> acceptCityInviteTransaction(playerId, playerName));
    }

    private CityMembershipResult acceptCityInviteTransaction(UUID playerId, String playerName) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT 1 FROM city_members WHERE player_uuid = ?")) {
                    query.setString(1, playerId.toString());
                    try (ResultSet result = query.executeQuery()) {
                        if (result.next()) {
                            connection.commit();
                            return membershipResult(CityMembershipCode.ALREADY_IN_CITY);
                        }
                    }
                }
                UUID cityId = null;
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT city_uuid, expires_at FROM city_invites WHERE player_uuid = ?")) {
                    query.setString(1, playerId.toString());
                    try (ResultSet result = query.executeQuery()) {
                        if (result.next() && Instant.parse(result.getString("expires_at")).isAfter(Instant.now())) {
                            cityId = UUID.fromString(result.getString("city_uuid"));
                        }
                    }
                }
                if (cityId == null) {
                    try (PreparedStatement delete = connection.prepareStatement("DELETE FROM city_invites WHERE player_uuid = ?")) {
                        delete.setString(1, playerId.toString());
                        delete.executeUpdate();
                    }
                    connection.commit();
                    return membershipResult(CityMembershipCode.NOT_INVITED);
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO city_members(city_uuid, player_uuid, last_known_name, role_id, joined_at)
                        VALUES (?, ?, ?, 'MEMBER', ?)
                        """)) {
                    insert.setString(1, cityId.toString());
                    insert.setString(2, playerId.toString());
                    insert.setString(3, playerName);
                    insert.setString(4, Instant.now().toString());
                    insert.executeUpdate();
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM city_invites WHERE player_uuid = ?")) {
                    delete.setString(1, playerId.toString());
                    delete.executeUpdate();
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE cities SET revision = revision + 1 WHERE city_uuid = ?")) {
                    update.setString(1, cityId.toString());
                    if (update.executeUpdate() != 1) throw new SQLException("City disappeared while accepting invite");
                }
                CityView city = readCity(connection, cityId).orElseThrow(
                        () -> new SQLException("City disappeared after invitation acceptance"));
                connection.commit();
                return new CityMembershipResult(CityMembershipCode.ACCEPTED, Optional.of(city));
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    @Override
    public CompletionStage<CityMembershipResult> kickCityMember(UUID actorId, UUID targetId, long expectedRevision) {
        return async(() -> kickCityMemberTransaction(actorId, targetId, expectedRevision));
    }

    private CityMembershipResult kickCityMemberTransaction(UUID actorId, UUID targetId, long expectedRevision)
            throws SQLException {
        if (actorId.equals(targetId)) return membershipResult(CityMembershipCode.SELF_TARGET);
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                UUID cityId = null;
                String actorRole = null;
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT city_uuid, role_id FROM city_members WHERE player_uuid = ?")) {
                    query.setString(1, actorId.toString());
                    try (ResultSet result = query.executeQuery()) {
                        if (result.next()) {
                            cityId = UUID.fromString(result.getString("city_uuid"));
                            actorRole = result.getString("role_id");
                        }
                    }
                }
                if (cityId == null) {
                    connection.commit();
                    return membershipResult(CityMembershipCode.NO_CITY);
                }
                CityRole actorCityRole = CityRole.fromStorage(actorRole);
                if (!actorCityRole.allows(ru.servermine.cities.api.CityPermission.KICK_MEMBERS)) {
                    connection.commit();
                    return membershipResult(CityMembershipCode.PERMISSION_DENIED);
                }
                String targetRole = null;
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT role_id FROM city_members WHERE city_uuid = ? AND player_uuid = ?")) {
                    query.setString(1, cityId.toString());
                    query.setString(2, targetId.toString());
                    try (ResultSet result = query.executeQuery()) {
                        if (result.next()) targetRole = result.getString("role_id");
                    }
                }
                if (targetRole == null) {
                    connection.commit();
                    return membershipResult(CityMembershipCode.TARGET_NOT_IN_CITY);
                }
                CityRole targetCityRole = CityRole.fromStorage(targetRole);
                if (targetCityRole == CityRole.RULER) {
                    connection.commit();
                    return membershipResult(CityMembershipCode.CANNOT_KICK_RULER);
                }
                if (targetCityRole.rank() >= actorCityRole.rank()) {
                    connection.commit();
                    return membershipResult(CityMembershipCode.PERMISSION_DENIED);
                }
                CityView current = readCity(connection, cityId).orElseThrow(
                        () -> new SQLException("City disappeared while removing a member"));
                if (current.revision() != expectedRevision) {
                    connection.commit();
                    return membershipResult(CityMembershipCode.STALE_CITY);
                }
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM city_members WHERE city_uuid = ? AND player_uuid = ?")) {
                    delete.setString(1, cityId.toString());
                    delete.setString(2, targetId.toString());
                    if (delete.executeUpdate() != 1) throw new SQLException("Membership changed during removal");
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE cities SET revision = revision + 1 WHERE city_uuid = ?")) {
                    update.setString(1, cityId.toString());
                    if (update.executeUpdate() != 1) throw new SQLException("City disappeared during member removal");
                }
                CityView updated = readCity(connection, cityId).orElseThrow(
                        () -> new SQLException("City disappeared after member removal"));
                connection.commit();
                return new CityMembershipResult(CityMembershipCode.KICKED, Optional.of(updated));
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    private CityMembershipResult membershipResult(CityMembershipCode code) {
        return new CityMembershipResult(code, Optional.empty());
    }

    @Override
    public CompletionStage<CityRoleResult> assignMemberRole(UUID actorId, UUID targetId, CityRole role,
                                                              long expectedRevision) {
        return async(() -> assignMemberRoleTransaction(actorId, targetId, role, expectedRevision));
    }

    private CityRoleResult assignMemberRoleTransaction(UUID actorId, UUID targetId, CityRole role,
                                                        long expectedRevision) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                UUID cityId = null;
                String actorRoleId = null;
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT city_uuid, role_id FROM city_members WHERE player_uuid=?")) {
                    query.setString(1, actorId.toString());
                    try (ResultSet result = query.executeQuery()) {
                        if (result.next()) {
                            cityId = UUID.fromString(result.getString("city_uuid"));
                            actorRoleId = result.getString("role_id");
                        }
                    }
                }
                if (cityId == null) return roleResult(connection, CityRoleCode.NO_CITY);
                CityRole actorRole = CityRole.fromStorage(actorRoleId);
                if (!actorRole.allows(ru.servermine.cities.api.CityPermission.MANAGE_ROLES)
                        || !CityRole.assignableBy(actorRole).contains(role)) {
                    return roleResult(connection, CityRoleCode.PERMISSION_DENIED);
                }
                String previousRoleId = null;
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT role_id FROM city_members WHERE city_uuid=? AND player_uuid=?")) {
                    query.setString(1, cityId.toString());
                    query.setString(2, targetId.toString());
                    try (ResultSet result = query.executeQuery()) {
                        if (result.next()) previousRoleId = result.getString("role_id");
                    }
                }
                if (previousRoleId == null) return roleResult(connection, CityRoleCode.TARGET_NOT_IN_CITY);
                CityRole previousRole = CityRole.fromStorage(previousRoleId);
                if (previousRole == CityRole.RULER) return roleResult(connection, CityRoleCode.CANNOT_CHANGE_RULER);
                if (previousRole.rank() >= actorRole.rank()) return roleResult(connection, CityRoleCode.PERMISSION_DENIED);

                CityView current = readCity(connection, cityId).orElseThrow(
                        () -> new SQLException("City disappeared while assigning a role"));
                if (current.revision() != expectedRevision) return roleResult(connection, CityRoleCode.STALE_CITY);
                if (previousRole == role) {
                    return roleResult(connection, CityRoleCode.ALREADY_ASSIGNED);
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE city_members SET role_id=? WHERE city_uuid=? AND player_uuid=?")) {
                    update.setString(1, role.name());
                    update.setString(2, cityId.toString());
                    update.setString(3, targetId.toString());
                    if (update.executeUpdate() != 1) throw new SQLException("Member changed during role assignment");
                }
                try (PreparedStatement audit = connection.prepareStatement("""
                        INSERT INTO city_role_audit(city_uuid, actor_uuid, target_uuid, previous_role, new_role, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
                    audit.setString(1, cityId.toString());
                    audit.setString(2, actorId.toString());
                    audit.setString(3, targetId.toString());
                    audit.setString(4, previousRole.name());
                    audit.setString(5, role.name());
                    audit.setString(6, Instant.now().toString());
                    audit.executeUpdate();
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE cities SET revision=revision+1 WHERE city_uuid=? AND revision=?")) {
                    update.setString(1, cityId.toString());
                    update.setLong(2, expectedRevision);
                    if (update.executeUpdate() != 1) throw new SQLException("City revision changed during role assignment");
                }
                CityView updated = readCity(connection, cityId).orElseThrow();
                connection.commit();
                return new CityRoleResult(CityRoleCode.ROLE_CHANGED, Optional.of(updated));
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    private CityRoleResult roleResult(Connection connection, CityRoleCode code) throws SQLException {
        connection.commit();
        return new CityRoleResult(code, Optional.empty());
    }

    @Override
    public CompletionStage<CityTreasuryResult> prepareTreasuryDeposit(CityTreasuryDraft draft) {
        return async(() -> treasuryTransaction(connection -> {
            String payload = treasuryPayload(draft);
            String hash = sha256(payload);
            ExistingOperation existing = findOperation(connection, draft.operationId());
            if (existing != null) {
                if (!existing.type.equals("TREASURY_DEPOSIT") || !existing.requestHash.equals(hash)) {
                    connection.commit();
                    return treasuryResult(draft.operationId(), CityTreasuryCode.INTERNAL_ERROR, null);
                }
                if (existing.state.equals("COMPLETED")) {
                    CityView city = readCity(connection, draft.cityId()).orElse(null);
                    connection.commit();
                    return treasuryResult(draft.operationId(), city == null ? CityTreasuryCode.INTERNAL_ERROR
                            : CityTreasuryCode.DEPOSITED, city);
                }
                connection.commit();
                return treasuryResult(draft.operationId(), CityTreasuryCode.OPERATION_IN_PROGRESS, null);
            }
            CityView city = readCity(connection, draft.cityId()).orElse(null);
            if (city == null) {
                connection.commit();
                return treasuryResult(draft.operationId(), CityTreasuryCode.NO_CITY, null);
            }
            try (PreparedStatement member = connection.prepareStatement(
                    "SELECT 1 FROM city_members WHERE city_uuid=? AND player_uuid=?")) {
                member.setString(1, draft.cityId().toString());
                member.setString(2, draft.actorId().toString());
                try (ResultSet result = member.executeQuery()) {
                    if (!result.next()) {
                        connection.commit();
                        return treasuryResult(draft.operationId(), CityTreasuryCode.NO_CITY, null);
                    }
                }
            }
            String now = Instant.now().toString();
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO city_operations(operation_uuid, operation_type, request_hash, request_payload, state,
                        result_code, city_uuid, actor_uuid, created_at, updated_at)
                    VALUES (?, 'TREASURY_DEPOSIT', ?, ?, 'PREPARED', 'OPERATION_IN_PROGRESS', ?, ?, ?, ?)
                    """)) {
                insert.setString(1, draft.operationId().toString());
                insert.setString(2, hash);
                insert.setString(3, payload);
                insert.setString(4, draft.cityId().toString());
                insert.setString(5, draft.actorId().toString());
                insert.setString(6, now);
                insert.setString(7, now);
                insert.executeUpdate();
            }
            connection.commit();
            return treasuryResult(draft.operationId(), CityTreasuryCode.OPERATION_IN_PROGRESS, null);
        }));
    }

    @Override
    public CompletionStage<Boolean> reserveTreasuryDeposit(UUID operationId) {
        return async(() -> {
            try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement("""
                    UPDATE city_operations SET state='RESERVED', updated_at=?
                    WHERE operation_uuid=? AND operation_type='TREASURY_DEPOSIT' AND state='PREPARED'
                    """)) {
                update.setString(1, Instant.now().toString());
                update.setString(2, operationId.toString());
                if (update.executeUpdate() == 1) return true;
                ExistingOperation operation = findOperation(connection, operationId);
                return operation != null && operation.type.equals("TREASURY_DEPOSIT") && operation.state.equals("RESERVED");
            }
        });
    }

    @Override
    public CompletionStage<CityTreasuryResult> applyTreasuryDeposit(CityTreasuryDraft draft) {
        return async(() -> treasuryTransaction(connection -> {
            String hash = sha256(treasuryPayload(draft));
            ExistingOperation operation = findOperation(connection, draft.operationId());
            if (operation == null || !operation.type.equals("TREASURY_DEPOSIT") || !operation.requestHash.equals(hash)) {
                connection.commit();
                return treasuryResult(draft.operationId(), CityTreasuryCode.INTERNAL_ERROR, null);
            }
            if (operation.state.equals("DOMAIN_COMMITTED") || operation.state.equals("COMMIT_PENDING")
                    || operation.state.equals("COMPLETED")) {
                CityView city = readCity(connection, draft.cityId()).orElse(null);
                connection.commit();
                return treasuryResult(draft.operationId(), city == null ? CityTreasuryCode.INTERNAL_ERROR
                        : CityTreasuryCode.DEPOSITED, city);
            }
            if (!operation.state.equals("RESERVED")) {
                connection.commit();
                return treasuryResult(draft.operationId(), CityTreasuryCode.OPERATION_IN_PROGRESS, null);
            }
            CityView city = readCity(connection, draft.cityId()).orElse(null);
            if (city == null) {
                updateTreasuryState(connection, draft.operationId(), "RESERVED", "RELEASE_PENDING", "NO_CITY");
                connection.commit();
                return treasuryResult(draft.operationId(), CityTreasuryCode.NO_CITY, null);
            }
            final long balanceAfter;
            try { balanceAfter = Math.addExact(city.treasury(), draft.amount()); }
            catch (ArithmeticException overflow) {
                updateTreasuryState(connection, draft.operationId(), "RESERVED", "RELEASE_PENDING", "INTERNAL_ERROR");
                connection.commit();
                return treasuryResult(draft.operationId(), CityTreasuryCode.INTERNAL_ERROR, null);
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE cities SET treasury=?, revision=revision+1 WHERE city_uuid=? AND treasury=? AND revision=?
                    """)) {
                update.setLong(1, balanceAfter);
                update.setString(2, draft.cityId().toString());
                update.setLong(3, city.treasury());
                update.setLong(4, city.revision());
                if (update.executeUpdate() != 1) throw new SQLException("City changed during treasury deposit");
            }
            insertTreasuryEntry(connection, draft.operationId(), draft.cityId(), draft.amount(), draft.reason());
            updateTreasuryState(connection, draft.operationId(), "RESERVED", "DOMAIN_COMMITTED", "DEPOSITED");
            CityView updated = readCity(connection, draft.cityId()).orElseThrow();
            connection.commit();
            return treasuryResult(draft.operationId(), CityTreasuryCode.DEPOSITED, updated);
        }));
    }

    @Override
    public CompletionStage<Void> markTreasuryCommitPending(UUID operationId) {
        return async(() -> {
            try (Connection connection = connection()) {
                updateTreasuryState(connection, operationId, "DOMAIN_COMMITTED", "COMMIT_PENDING", "UNKNOWN_OUTCOME");
                return null;
            }
        });
    }

    @Override
    public CompletionStage<CityTreasuryResult> completeTreasuryDeposit(UUID operationId) {
        return async(() -> treasuryTransaction(connection -> {
            ExistingOperation operation = findOperation(connection, operationId);
            if (operation == null || !operation.type.equals("TREASURY_DEPOSIT")) {
                connection.commit();
                return treasuryResult(operationId, CityTreasuryCode.INTERNAL_ERROR, null);
            }
            if (operation.state.equals("DOMAIN_COMMITTED") || operation.state.equals("COMMIT_PENDING")) {
                updateTreasuryState(connection, operationId, operation.state, "COMPLETED", "DEPOSITED");
                operation = findOperation(connection, operationId);
            }
            if (!operation.state.equals("COMPLETED")) {
                connection.commit();
                return treasuryResult(operationId, CityTreasuryCode.OPERATION_IN_PROGRESS, null);
            }
            CityView city = operation.cityId == null ? null : readCity(connection, UUID.fromString(operation.cityId)).orElse(null);
            connection.commit();
            return treasuryResult(operationId, city == null ? CityTreasuryCode.INTERNAL_ERROR : CityTreasuryCode.DEPOSITED, city);
        }));
    }

    @Override
    public CompletionStage<Void> failTreasuryDeposit(UUID operationId, boolean releasePending) {
        return async(() -> {
            try (Connection connection = connection()) {
                updateTreasuryState(connection, operationId, releasePending ? "RESERVED" : "PREPARED",
                        releasePending ? "RELEASE_PENDING" : "FAILED", "INTERNAL_ERROR");
                return null;
            }
        });
    }

    @Override
    public CompletionStage<Void> markTreasuryDepositReleased(UUID operationId) {
        return async(() -> {
            try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement("""
                    UPDATE city_operations SET state='RELEASED', updated_at=?
                    WHERE operation_uuid=? AND operation_type='TREASURY_DEPOSIT' AND state='RELEASE_PENDING'
                    """)) {
                update.setString(1, Instant.now().toString());
                update.setString(2, operationId.toString());
                update.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public CompletionStage<List<PendingTreasuryDeposit>> pendingTreasuryDeposits() {
        return async(() -> {
            List<PendingTreasuryDeposit> pending = new ArrayList<>();
            try (Connection connection = connection(); PreparedStatement query = connection.prepareStatement("""
                    SELECT operation_uuid, city_uuid, actor_uuid, state, request_payload
                    FROM city_operations WHERE operation_type='TREASURY_DEPOSIT'
                      AND state IN ('PREPARED','RESERVED','DOMAIN_COMMITTED','COMMIT_PENDING','RELEASE_PENDING')
                    ORDER BY created_at
                    """); ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    String[] payload = result.getString("request_payload").split("\\n", -1);
                    if (payload.length != 3 && payload.length != 4) throw new SQLException("Invalid treasury deposit payload");
                    pending.add(new PendingTreasuryDeposit(UUID.fromString(result.getString("operation_uuid")),
                            UUID.fromString(result.getString("city_uuid")), UUID.fromString(result.getString("actor_uuid")),
                            Long.parseLong(payload[2]), result.getString("state"), payload.length == 4 ? payload[3] : "DEPOSIT"));
                }
            }
            return List.copyOf(pending);
        });
    }

    @Override
    public CompletionStage<Void> recordTreasuryRecoveryAudit(UUID operationId, UUID actorId, String outcome) {
        String safeOutcome = outcome == null ? "UNKNOWN" : outcome.substring(0, Math.min(500, outcome.length()));
        return async(() -> {
            try (Connection connection = connection(); PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO city_treasury_recovery_audit(operation_uuid, actor_uuid, outcome, occurred_at)
                    VALUES(?,?,?,?)
                    """)) {
                insert.setString(1, operationId.toString());
                insert.setString(2, actorId.toString());
                insert.setString(3, safeOutcome);
                insert.setString(4, Instant.now().toString());
                insert.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public CompletionStage<List<CityTreasuryEntry>> treasuryHistory(UUID cityId, int limit) {
        int boundedLimit = Math.max(1, Math.min(50, limit));
        return async(() -> {
            List<CityTreasuryEntry> entries = new ArrayList<>();
            try (Connection connection = connection(); PreparedStatement query = connection.prepareStatement("""
                    SELECT operation_uuid, amount, balance_after, reason, created_at
                    FROM city_treasury_entries WHERE city_uuid=? ORDER BY created_at DESC LIMIT ?
                    """)) {
                query.setString(1, cityId.toString());
                query.setInt(2, boundedLimit);
                try (ResultSet result = query.executeQuery()) {
                    while (result.next()) entries.add(new CityTreasuryEntry(UUID.fromString(result.getString("operation_uuid")),
                            result.getLong("amount"), result.getLong("balance_after"), result.getString("reason"),
                            Instant.parse(result.getString("created_at"))));
                }
            }
            return List.copyOf(entries);
        });
    }

    @Override
    public CompletionStage<CityTreasuryTransferResult> transferTreasury(CityTreasuryTransferRequest request, long maximumAmount) {
        return async(() -> transferTransaction(connection -> transferTreasury(connection, request, maximumAmount)));
    }

    @Override public CompletionStage<List<CityMarketListing>> marketListings(UUID viewerId) {
        return async(() -> {
            try (Connection connection = connection()) {
                UUID cityId = cityIdForPlayer(connection, viewerId);
                if (cityId == null) return List.of();
                List<CityMarketListing> result = new ArrayList<>();
                try (PreparedStatement query = connection.prepareStatement("""
                    SELECT l.listing_uuid,l.city_uuid,c.name AS city_name,l.seller_uuid,l.item_name,l.item_data,l.price,l.created_at,l.status
                    FROM city_market_listings l JOIN cities c ON c.city_uuid=l.city_uuid
                    WHERE l.status='ACTIVE' AND l.city_uuid<>? AND EXISTS (
                        SELECT 1 FROM city_treaties t WHERE t.treaty_type='TRADE' AND t.status='ACTIVE' AND t.expires_at>?
                        AND ((t.proposing_city=l.city_uuid AND t.target_city=?) OR (t.proposing_city=? AND t.target_city=l.city_uuid)))
                    ORDER BY l.created_at DESC LIMIT 45
                    """)) {
                query.setString(1, cityId.toString()); query.setString(2, Instant.now().toString());
                query.setString(3, cityId.toString()); query.setString(4, cityId.toString());
                try (ResultSet rows = query.executeQuery()) { while (rows.next()) result.add(readMarketListing(rows)); }
                }
                return List.copyOf(result);
            }
        });
    }

    @Override public CompletionStage<List<CityMarketListing>> myMarketListings(UUID playerId) {
        return async(() -> {
            try (Connection connection = connection()) {
                UUID cityId = cityIdForPlayer(connection, playerId);
                if (cityId == null) return List.of();
                List<CityMarketListing> result = new ArrayList<>();
                try (PreparedStatement query = connection.prepareStatement("""
                        SELECT l.listing_uuid,l.city_uuid,c.name AS city_name,l.seller_uuid,l.item_name,l.item_data,l.price,l.created_at,l.status
                        FROM city_market_listings l JOIN cities c ON c.city_uuid=l.city_uuid
                        WHERE l.city_uuid=? AND l.status='ACTIVE' ORDER BY l.created_at DESC LIMIT 45
                        """)) {
                    query.setString(1, cityId.toString());
                    try (ResultSet rows = query.executeQuery()) { while (rows.next()) result.add(readMarketListing(rows)); }
                }
                return List.copyOf(result);
            }
        });
    }

    @Override public CompletionStage<List<CityMarketListing>> pendingMarketListings(UUID sellerId) {
        return async(() -> {
            List<CityMarketListing> result = new ArrayList<>();
            try (Connection connection = connection(); PreparedStatement query = connection.prepareStatement("""
                    SELECT l.listing_uuid,l.city_uuid,c.name AS city_name,l.seller_uuid,l.item_name,l.item_data,l.price,l.created_at,l.status
                    FROM city_market_listings l JOIN cities c ON c.city_uuid=l.city_uuid
                    WHERE l.seller_uuid=? AND l.status IN ('PREPARING','REMOVING') ORDER BY l.created_at
                    """)) {
                query.setString(1, sellerId.toString());
                try (ResultSet rows = query.executeQuery()) { while (rows.next()) result.add(readMarketListing(rows)); }
            }
            return List.copyOf(result);
        });
    }

    @Override public CompletionStage<CityMarketResult> createMarketListing(CityMarketCreateRequest request, long maximumPrice) {
        return async(() -> marketTransaction(connection -> {
            if (request.itemData().length > 65_536 || request.itemName().length() > 100)
                return marketResult(request.listingId(), CityMarketCode.INVALID_ITEM, null, null, null);
            if (request.price() <= 0) return marketResult(request.listingId(), CityMarketCode.INVALID_PRICE, null, null, null);
            if (maximumPrice < 1 || request.price() > maximumPrice)
                return marketResult(request.listingId(), CityMarketCode.LIMIT_EXCEEDED, null, null, null);
            try (PreparedStatement existing = connection.prepareStatement("SELECT 1 FROM city_market_listings WHERE listing_uuid=?")) {
                existing.setString(1, request.listingId().toString());
                try (ResultSet row = existing.executeQuery()) {
                    if (row.next()) return marketResult(request.listingId(), CityMarketCode.OPERATION_CONFLICT, null, null, null);
                }
            }
            UUID cityId; String role;
            try (PreparedStatement member = connection.prepareStatement("SELECT city_uuid,role_id FROM city_members WHERE player_uuid=?")) {
                member.setString(1, request.sellerId().toString());
                try (ResultSet row = member.executeQuery()) {
                    if (!row.next()) return marketResult(request.listingId(), CityMarketCode.NO_CITY, null, null, null);
                    cityId = UUID.fromString(row.getString("city_uuid")); role = row.getString("role_id");
                }
            }
            if (!CityRole.fromStorage(role).allows(ru.servermine.cities.api.CityPermission.MANAGE_MARKET))
                return marketResult(request.listingId(), CityMarketCode.PERMISSION_DENIED, null, null, null);
            String now = Instant.now().toString();
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO city_market_listings(listing_uuid,city_uuid,seller_uuid,item_name,item_data,price,status,created_at)
                    VALUES(?,?,?,?,?,?,'PREPARING',?)
                    """)) {
                insert.setString(1, request.listingId().toString()); insert.setString(2, cityId.toString());
                insert.setString(3, request.sellerId().toString()); insert.setString(4, request.itemName());
                insert.setBytes(5, request.itemData()); insert.setLong(6, request.price()); insert.setString(7, now);
                insert.executeUpdate();
            }
            CityMarketListing listing = readMarketListing(connection, request.listingId());
            return marketResult(request.listingId(), CityMarketCode.PREPARED, listing, null, readCity(connection, cityId).orElse(null));
        }));
    }

    @Override public CompletionStage<Boolean> markMarketListingRemoving(UUID listingId, UUID sellerId) {
        return async(() -> listingTransition(listingId, sellerId, "PREPARING", "REMOVING"));
    }

    @Override public CompletionStage<Boolean> activateMarketListing(UUID listingId, UUID sellerId) {
        return async(() -> listingTransition(listingId, sellerId, "REMOVING", "ACTIVE"));
    }

    @Override public CompletionStage<Boolean> abortMarketListing(UUID listingId, UUID sellerId) {
        return async(() -> {
            try (Connection connection = connection(); PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM city_market_listings WHERE listing_uuid=? AND seller_uuid=? AND status='PREPARING'")) {
                delete.setString(1, listingId.toString()); delete.setString(2, sellerId.toString());
                return delete.executeUpdate() == 1;
            }
        });
    }

    private boolean listingTransition(UUID listingId, UUID sellerId, String expected, String next) throws SQLException {
        try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement(
                "UPDATE city_market_listings SET status=? WHERE listing_uuid=? AND seller_uuid=? AND status=?")) {
            update.setString(1, next); update.setString(2, listingId.toString()); update.setString(3, sellerId.toString()); update.setString(4, expected);
            if (update.executeUpdate() == 1) return true;
            try (PreparedStatement query = connection.prepareStatement("SELECT status FROM city_market_listings WHERE listing_uuid=? AND seller_uuid=?")) {
                query.setString(1, listingId.toString()); query.setString(2, sellerId.toString());
                try (ResultSet row = query.executeQuery()) { return row.next() && row.getString(1).equals(next); }
            }
        }
    }

    @Override public CompletionStage<CityMarketResult> purchaseMarketListing(CityMarketPurchaseRequest request, int feeBasisPoints) {
        return async(() -> marketTransaction(connection -> purchaseMarketListing(connection, request, feeBasisPoints)));
    }

    @Override public CompletionStage<CityMarketResult> cancelMarketListing(UUID listingId, UUID actorId) {
        return async(() -> marketTransaction(connection -> {
            CityMarketListing listing = readMarketListing(connection, listingId);
            if (listing == null) return marketResult(listingId, CityMarketCode.LISTING_NOT_FOUND, null, null, null);
            UUID actorCity = cityIdForPlayer(connection, actorId);
            if (actorCity == null) return marketResult(listingId, CityMarketCode.NO_CITY, listing, null, null);
            String role = roleForPlayer(connection, actorId);
            if (!actorCity.equals(listing.cityId())) return marketResult(listingId, CityMarketCode.PERMISSION_DENIED, listing, null, null);
            if (!CityRole.fromStorage(role).allows(ru.servermine.cities.api.CityPermission.MANAGE_MARKET))
                return marketResult(listingId, CityMarketCode.PERMISSION_DENIED, listing, null, null);
            try (PreparedStatement update = connection.prepareStatement("UPDATE city_market_listings SET status='CANCELLED' WHERE listing_uuid=? AND status='ACTIVE'")) {
                update.setString(1, listingId.toString());
                if (update.executeUpdate() != 1) return marketResult(listingId, CityMarketCode.LISTING_NOT_FOUND, listing, null, null);
            }
            UUID deliveryId = UUID.nameUUIDFromBytes((listingId + ":market-refund").getBytes(StandardCharsets.UTF_8));
            try (PreparedStatement delivery = connection.prepareStatement("INSERT INTO city_market_deliveries(delivery_uuid,operation_uuid,buyer_uuid,item_data,status,created_at) VALUES(?,?,?,?,'PENDING',?)")) {
                delivery.setString(1, deliveryId.toString()); delivery.setString(2, deliveryId.toString());
                delivery.setString(3, listing.sellerId().toString()); delivery.setBytes(4, listing.itemData());
                delivery.setString(5, Instant.now().toString()); delivery.executeUpdate();
            }
            return marketResult(listingId, CityMarketCode.CANCELLED, listing, null, readCity(connection, listing.cityId()).orElse(null));
        }));
    }

    private String roleForPlayer(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT role_id FROM city_members WHERE player_uuid=?")) {
            query.setString(1, playerId.toString()); try (ResultSet row = query.executeQuery()) { return row.next() ? row.getString(1) : null; }
        }
    }

    private CityMarketResult purchaseMarketListing(Connection connection, CityMarketPurchaseRequest request, int feeBasisPoints) throws SQLException {
        if (feeBasisPoints < 0 || feeBasisPoints > 2_000)
            return marketResult(request.operationId(), CityMarketCode.INTERNAL_ERROR, null, null, null);
        try (PreparedStatement prior = connection.prepareStatement("SELECT listing_uuid,buyer_uuid,buyer_city FROM city_market_purchases WHERE operation_uuid=?")) {
            prior.setString(1, request.operationId().toString());
            try (ResultSet row = prior.executeQuery()) {
                if (row.next()) {
                    if (!row.getString("listing_uuid").equals(request.listingId().toString())
                            || !row.getString("buyer_uuid").equals(request.buyerId().toString()))
                        return marketResult(request.operationId(), CityMarketCode.OPERATION_CONFLICT, null, null, null);
                    CityMarketListing listing = readMarketListing(connection, request.listingId());
                    return marketResult(request.operationId(), CityMarketCode.REPLAYED, listing,
                            readCity(connection, UUID.fromString(row.getString("buyer_city"))).orElse(null),
                            listing == null ? null : readCity(connection, listing.cityId()).orElse(null));
                }
            }
        }
        UUID buyerCity; String role;
        try (PreparedStatement member = connection.prepareStatement("SELECT city_uuid,role_id FROM city_members WHERE player_uuid=?")) {
            member.setString(1, request.buyerId().toString());
            try (ResultSet row = member.executeQuery()) {
                if (!row.next()) return marketResult(request.operationId(), CityMarketCode.NO_CITY, null, null, null);
                buyerCity = UUID.fromString(row.getString("city_uuid")); role = row.getString("role_id");
            }
        }
        if (!CityRole.fromStorage(role).allows(ru.servermine.cities.api.CityPermission.TRANSFER_TREASURY))
            return marketResult(request.operationId(), CityMarketCode.PERMISSION_DENIED, null, null, null);
        CityMarketListing listing = readMarketListing(connection, request.listingId());
        if (listing == null) return marketResult(request.operationId(), CityMarketCode.LISTING_NOT_FOUND, null, null, null);
        if (listing.state() != CityMarketState.ACTIVE)
            return marketResult(request.operationId(), CityMarketCode.LISTING_NOT_FOUND, listing, null, null);
        if (listing.cityId().equals(buyerCity)) return marketResult(request.operationId(), CityMarketCode.SELF_PURCHASE, listing, null, null);
        if (!marketTradeActive(connection, buyerCity, listing.cityId()))
            return marketResult(request.operationId(), CityMarketCode.TRADE_REQUIRED, listing, null, readCity(connection, listing.cityId()).orElse(null));
        CityView buyer = readCity(connection, buyerCity).orElse(null);
        CityView seller = readCity(connection, listing.cityId()).orElse(null);
        if (buyer == null || seller == null) return marketResult(request.operationId(), CityMarketCode.LISTING_NOT_FOUND, listing, null, null);
        long fee;
        try { fee = Math.floorDiv(Math.addExact(Math.multiplyExact(listing.price(), feeBasisPoints), 9_999), 10_000); }
        catch (ArithmeticException overflow) { return marketResult(request.operationId(), CityMarketCode.LIMIT_EXCEEDED, listing, buyer, seller); }
        long total;
        try { total = Math.addExact(listing.price(), fee); }
        catch (ArithmeticException overflow) { return marketResult(request.operationId(), CityMarketCode.LIMIT_EXCEEDED, listing, buyer, seller); }
        if (buyer.treasury() < total) return marketResult(request.operationId(), CityMarketCode.INSUFFICIENT_TREASURY, listing, buyer, seller);
        if (seller.treasury() > Long.MAX_VALUE - total) return marketResult(request.operationId(), CityMarketCode.LIMIT_EXCEEDED, listing, buyer, seller);
        long buyerAfter = buyer.treasury() - total, sellerAfter = seller.treasury() + total;
        try (PreparedStatement debit = connection.prepareStatement("UPDATE cities SET treasury=?,revision=revision+1 WHERE city_uuid=? AND treasury=?");
             PreparedStatement credit = connection.prepareStatement("UPDATE cities SET treasury=?,revision=revision+1 WHERE city_uuid=? AND treasury=?")) {
            debit.setLong(1, buyerAfter); debit.setString(2, buyerCity.toString()); debit.setLong(3, buyer.treasury());
            if (debit.executeUpdate() != 1) throw new SQLException("Buyer treasury changed during market purchase");
            credit.setLong(1, sellerAfter); credit.setString(2, seller.id().toString()); credit.setLong(3, seller.treasury());
            if (credit.executeUpdate() != 1) throw new SQLException("Seller treasury changed during market purchase");
        }
        String now = Instant.now().toString();
        try (PreparedStatement update = connection.prepareStatement("UPDATE city_market_listings SET status='SOLD' WHERE listing_uuid=? AND status='ACTIVE'")) {
            update.setString(1, listing.id().toString());
            if (update.executeUpdate() != 1) throw new SQLException("Market listing was sold concurrently");
        }
        try (PreparedStatement purchase = connection.prepareStatement("INSERT INTO city_market_purchases(operation_uuid,listing_uuid,buyer_city,buyer_uuid,price,fee,created_at) VALUES(?,?,?,?,?,?,?)")) {
            purchase.setString(1, request.operationId().toString()); purchase.setString(2, listing.id().toString());
            purchase.setString(3, buyerCity.toString()); purchase.setString(4, request.buyerId().toString());
            purchase.setLong(5, listing.price()); purchase.setLong(6, fee); purchase.setString(7, now); purchase.executeUpdate();
        }
        UUID deliveryId = UUID.nameUUIDFromBytes((request.operationId() + ":market-delivery").getBytes(StandardCharsets.UTF_8));
        try (PreparedStatement delivery = connection.prepareStatement("INSERT INTO city_market_deliveries(delivery_uuid,operation_uuid,buyer_uuid,item_data,status,created_at) VALUES(?,?,?,?,'PENDING',?)")) {
            delivery.setString(1, deliveryId.toString()); delivery.setString(2, request.operationId().toString());
            delivery.setString(3, request.buyerId().toString()); delivery.setBytes(4, listing.itemData()); delivery.setString(5, now); delivery.executeUpdate();
        }
        insertTreasuryEntry(connection, marketEntryId(request.operationId(), "debit"), buyerCity, -total,
                "Покупка на рынке " + listing.itemName() + " · " + request.operationId());
        insertTreasuryEntry(connection, marketEntryId(request.operationId(), "credit"), seller.id(), total,
                "Продажа на рынке " + listing.itemName() + " (цена " + listing.price() + ", сбор " + fee + ") · " + request.operationId());
        return marketResult(request.operationId(), CityMarketCode.PURCHASED, listing,
                readCity(connection, buyerCity).orElse(null), readCity(connection, seller.id()).orElse(null));
    }

    @Override public CompletionStage<List<CityMarketDelivery>> marketDeliveries(UUID playerId) {
        return async(() -> {
            List<CityMarketDelivery> result = new ArrayList<>();
            try (Connection connection = connection(); PreparedStatement query = connection.prepareStatement("SELECT delivery_uuid,buyer_uuid,item_data FROM city_market_deliveries WHERE buyer_uuid=? AND status='PENDING' ORDER BY created_at")) {
                query.setString(1, playerId.toString());
                try (ResultSet rows = query.executeQuery()) { while (rows.next()) result.add(new CityMarketDelivery(
                        UUID.fromString(rows.getString("delivery_uuid")), UUID.fromString(rows.getString("buyer_uuid")), rows.getBytes("item_data"))); }
            }
            return List.copyOf(result);
        });
    }

    @Override public CompletionStage<Boolean> acknowledgeMarketDelivery(UUID deliveryId, UUID playerId) {
        return async(() -> {
            try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement("UPDATE city_market_deliveries SET status='DELIVERED' WHERE delivery_uuid=? AND buyer_uuid=? AND status='PENDING'")) {
                update.setString(1, deliveryId.toString()); update.setString(2, playerId.toString());
                return update.executeUpdate() == 1;
            }
        });
    }

    private UUID cityIdForPlayer(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT city_uuid FROM city_members WHERE player_uuid=?")) {
            query.setString(1, playerId.toString()); try (ResultSet row = query.executeQuery()) {
                return row.next() ? UUID.fromString(row.getString(1)) : null;
            }
        }
    }

    private boolean marketTradeActive(Connection connection, UUID first, UUID second) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM city_treaties WHERE treaty_type='TRADE' AND status='ACTIVE' AND expires_at>? AND ((proposing_city=? AND target_city=?) OR (proposing_city=? AND target_city=?)) LIMIT 1")) {
            query.setString(1, Instant.now().toString()); query.setString(2, first.toString()); query.setString(3, second.toString());
            query.setString(4, second.toString()); query.setString(5, first.toString());
            try (ResultSet row = query.executeQuery()) { return row.next(); }
        }
    }

    private CityMarketListing readMarketListing(Connection connection, UUID listingId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT l.listing_uuid,l.city_uuid,c.name AS city_name,l.seller_uuid,l.item_name,l.item_data,l.price,l.created_at,l.status FROM city_market_listings l JOIN cities c ON c.city_uuid=l.city_uuid WHERE listing_uuid=?")) {
            query.setString(1, listingId.toString()); try (ResultSet row = query.executeQuery()) { return row.next() ? readMarketListing(row) : null; }
        }
    }

    private CityMarketListing readMarketListing(ResultSet row) throws SQLException {
        return new CityMarketListing(UUID.fromString(row.getString("listing_uuid")), UUID.fromString(row.getString("city_uuid")),
                row.getString("city_name"), UUID.fromString(row.getString("seller_uuid")), row.getString("item_name"),
                row.getBytes("item_data"), row.getLong("price"), Instant.parse(row.getString("created_at")),
                CityMarketState.valueOf(row.getString("status")));
    }

    private CityMarketResult marketResult(UUID id, CityMarketCode code, CityMarketListing listing, CityView buyer, CityView seller) {
        return new CityMarketResult(id, code, Optional.ofNullable(listing), Optional.ofNullable(buyer), Optional.ofNullable(seller));
    }

    private UUID marketEntryId(UUID operationId, String side) {
        return UUID.nameUUIDFromBytes((operationId + ":market:" + side).getBytes(StandardCharsets.UTF_8));
    }

    private <T> T marketTransaction(SqlTransaction<T> action) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try { T result = action.run(connection); connection.commit(); return result; }
            catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
        }
    }

    private CityTreasuryTransferResult transferTreasury(Connection connection, CityTreasuryTransferRequest request,
                                                        long maximumAmount) throws SQLException {
        if (request.amount() <= 0) return transferResult(request.operationId(), CityTreasuryTransferCode.INVALID_AMOUNT, null, null);
        if (maximumAmount < 1 || request.amount() > maximumAmount)
            return transferResult(request.operationId(), CityTreasuryTransferCode.LIMIT_EXCEEDED, null, null);
        try (PreparedStatement prior = connection.prepareStatement("""
                SELECT source_city,target_city,actor_uuid,amount FROM city_treasury_transfers WHERE operation_uuid=?
                """)) {
            prior.setString(1, request.operationId().toString());
            try (ResultSet row = prior.executeQuery()) {
                if (row.next()) {
                    boolean same = row.getString("target_city").equals(request.targetCityId().toString())
                            && row.getString("actor_uuid").equals(request.actorId().toString())
                            && row.getLong("amount") == request.amount();
                    if (!same) return transferResult(request.operationId(), CityTreasuryTransferCode.OPERATION_CONFLICT, null, null);
                    UUID sourceId = UUID.fromString(row.getString("source_city"));
                    CityView source = readCity(connection, sourceId).orElse(null);
                    CityView target = readCity(connection, request.targetCityId()).orElse(null);
                    return transferResult(request.operationId(), CityTreasuryTransferCode.REPLAYED, source, target);
                }
            }
        }
        UUID sourceId;
        String role;
        try (PreparedStatement membership = connection.prepareStatement("SELECT city_uuid,role_id FROM city_members WHERE player_uuid=?")) {
            membership.setString(1, request.actorId().toString());
            try (ResultSet row = membership.executeQuery()) {
                if (!row.next()) return transferResult(request.operationId(), CityTreasuryTransferCode.NO_CITY, null, null);
                sourceId = UUID.fromString(row.getString("city_uuid")); role = row.getString("role_id");
            }
        }
        CityView source = readCity(connection, sourceId).orElse(null);
        CityView target = readCity(connection, request.targetCityId()).orElse(null);
        if (source == null) return transferResult(request.operationId(), CityTreasuryTransferCode.NO_CITY, null, null);
        if (target == null) return transferResult(request.operationId(), CityTreasuryTransferCode.INVALID_TARGET, source, null);
        if (sourceId.equals(request.targetCityId())) return transferResult(request.operationId(), CityTreasuryTransferCode.INVALID_TARGET, source, target);
        if (!CityRole.fromStorage(role).allows(ru.servermine.cities.api.CityPermission.TRANSFER_TREASURY))
            return transferResult(request.operationId(), CityTreasuryTransferCode.PERMISSION_DENIED, source, target);
        String now = Instant.now().toString();
        try (PreparedStatement treaty = connection.prepareStatement("""
                SELECT 1 FROM city_treaties WHERE treaty_type='TRADE' AND status='ACTIVE' AND expires_at>?
                  AND ((proposing_city=? AND target_city=?) OR (proposing_city=? AND target_city=?)) LIMIT 1
                """)) {
            treaty.setString(1, now); treaty.setString(2, sourceId.toString()); treaty.setString(3, request.targetCityId().toString());
            treaty.setString(4, request.targetCityId().toString()); treaty.setString(5, sourceId.toString());
            try (ResultSet row = treaty.executeQuery()) {
                if (!row.next()) return transferResult(request.operationId(), CityTreasuryTransferCode.TRADE_REQUIRED, source, target);
            }
        }
        if (source.treasury() < request.amount())
            return transferResult(request.operationId(), CityTreasuryTransferCode.INSUFFICIENT_TREASURY, source, target);
        if (target.treasury() > Long.MAX_VALUE - request.amount())
            return transferResult(request.operationId(), CityTreasuryTransferCode.INVALID_AMOUNT, source, target);
        long sourceAfter = source.treasury() - request.amount();
        long targetAfter = target.treasury() + request.amount();
        try (PreparedStatement debit = connection.prepareStatement("UPDATE cities SET treasury=?,revision=revision+1 WHERE city_uuid=? AND treasury=?");
             PreparedStatement credit = connection.prepareStatement("UPDATE cities SET treasury=?,revision=revision+1 WHERE city_uuid=? AND treasury=?")) {
            debit.setLong(1, sourceAfter); debit.setString(2, sourceId.toString()); debit.setLong(3, source.treasury());
            if (debit.executeUpdate() != 1) throw new SQLException("Source treasury changed during transfer");
            credit.setLong(1, targetAfter); credit.setString(2, request.targetCityId().toString()); credit.setLong(3, target.treasury());
            if (credit.executeUpdate() != 1) throw new SQLException("Target treasury changed during transfer");
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO city_treasury_transfers(operation_uuid,source_city,target_city,actor_uuid,amount,
                    source_balance_after,target_balance_after,created_at) VALUES(?,?,?,?,?,?,?,?)
                """)) {
            insert.setString(1, request.operationId().toString()); insert.setString(2, sourceId.toString());
            insert.setString(3, request.targetCityId().toString()); insert.setString(4, request.actorId().toString());
            insert.setLong(5, request.amount()); insert.setLong(6, sourceAfter); insert.setLong(7, targetAfter); insert.setString(8, now);
            insert.executeUpdate();
        }
        String targetName = cityName(connection, request.targetCityId());
        String sourceName = cityName(connection, sourceId);
        insertTreasuryEntry(connection, transferEntryId(request.operationId(), "debit"), sourceId, -request.amount(),
                "Перевод в город " + targetName + " · " + request.operationId());
        insertTreasuryEntry(connection, transferEntryId(request.operationId(), "credit"), request.targetCityId(), request.amount(),
                "Перевод из города " + sourceName + " · " + request.operationId());
        return transferResult(request.operationId(), CityTreasuryTransferCode.SENT,
                readCity(connection, sourceId).orElse(null), readCity(connection, request.targetCityId()).orElse(null));
    }

    private UUID transferEntryId(UUID operationId, String side) {
        return UUID.nameUUIDFromBytes((operationId + ":" + side).getBytes(StandardCharsets.UTF_8));
    }

    private CityTreasuryTransferResult transferResult(UUID id, CityTreasuryTransferCode code, CityView source, CityView target) {
        return new CityTreasuryTransferResult(id, code, Optional.ofNullable(source), Optional.ofNullable(target));
    }

    private <T> T transferTransaction(SqlTransaction<T> action) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try { T result = action.run(connection); connection.commit(); return result; }
            catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
        }
    }

    private String treasuryPayload(CityTreasuryDraft draft) {
        String base = draft.cityId() + "\n" + draft.actorId() + "\n" + draft.amount();
        return draft.reason().equals("DEPOSIT") ? base : base + "\n" + draft.reason();
    }

    private CityTreasuryResult treasuryResult(UUID operationId, CityTreasuryCode code, CityView city) {
        return new CityTreasuryResult(operationId, code,
                code == CityTreasuryCode.DEPOSITED ? Optional.ofNullable(city) : Optional.empty());
    }

    private void updateTreasuryState(Connection connection, UUID operationId, String expected, String state, String code)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE city_operations SET state=?, result_code=?, updated_at=?
                WHERE operation_uuid=? AND operation_type='TREASURY_DEPOSIT' AND state=?
                """)) {
            update.setString(1, state);
            update.setString(2, code);
            update.setString(3, Instant.now().toString());
            update.setString(4, operationId.toString());
            update.setString(5, expected);
            if (update.executeUpdate() != 1) {
                ExistingOperation operation = findOperation(connection, operationId);
                if (operation == null || !operation.type.equals("TREASURY_DEPOSIT") || !operation.state.equals(state)) {
                    throw new SQLException("Treasury deposit journal changed unexpectedly");
                }
            }
        }
    }

    private <T> T treasuryTransaction(SqlTransaction<T> action) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try { return action.run(connection); }
            catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
        }
    }

    private void insertTreasuryEntry(Connection connection, UUID operationId, UUID cityId, long amount, String reason)
            throws SQLException {
        if (amount == 0) return;
        long balanceAfter = readCity(connection, cityId).orElseThrow().treasury();
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO city_treasury_entries(operation_uuid, city_uuid, amount, balance_after, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, operationId.toString());
            insert.setString(2, cityId.toString());
            insert.setLong(3, amount);
            insert.setLong(4, balanceAfter);
            insert.setString(5, reason);
            insert.setString(6, Instant.now().toString());
            insert.executeUpdate();
        }
    }

    @Override
    public CompletionStage<Optional<CityFoundationResult>> findFoundationReplay(CityFoundationDraft draft) {
        return async(() -> {
            String payload = foundationPayload(draft);
            String hash = sha256(payload);
            try (Connection connection = connection()) {
                ExistingOperation existing = findOperation(connection, draft.operationId());
                if (existing == null) return Optional.empty();
                if (!existing.type.equals("FOUND_CITY") || !existing.requestHash.equals(hash)) {
                    return Optional.of(foundationResult(draft.operationId(), CityFoundationCode.OPERATION_CONFLICT, null, true));
                }
                if (existing.state.equals("COMPLETED")) {
                    CityView city = readCity(connection, UUID.fromString(existing.cityId)).orElse(null);
                    return Optional.of(foundationResult(draft.operationId(), city == null
                            ? CityFoundationCode.INTERNAL_ERROR : CityFoundationCode.REPLAYED, city, true));
                }
                return Optional.of(foundationResult(draft.operationId(), parseFoundationCode(existing.resultCode), null, true));
            }
        });
    }

    @Override
    public CompletionStage<CityFoundationResult> found(CityFoundationDraft draft) {
        return async(() -> foundTransaction(draft));
    }

    private CityFoundationResult foundTransaction(CityFoundationDraft draft) throws SQLException {
        validateFootprint(draft);
        String payload = foundationPayload(draft);
        String hash = sha256(payload);
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                ExistingOperation existing = findOperation(connection, draft.operationId());
                if (existing != null) {
                    connection.commit();
                    if (!existing.type.equals("FOUND_CITY") || !existing.requestHash.equals(hash)) {
                        return foundationResult(draft.operationId(), CityFoundationCode.OPERATION_CONFLICT, null, true);
                    }
                    if (existing.state.equals("COMPLETED")) {
                        CityView city = readCity(connection, UUID.fromString(existing.cityId)).orElse(null);
                        return foundationResult(draft.operationId(), city == null
                                ? CityFoundationCode.INTERNAL_ERROR : CityFoundationCode.REPLAYED, city, true);
                    }
                    return foundationResult(draft.operationId(), parseFoundationCode(existing.resultCode), null, true);
                }

                CityFoundationCode failure = foundingConflict(connection, draft);
                if (failure != null) {
                    recordFoundationFailure(connection, draft, hash, payload, failure);
                    connection.commit();
                    return foundationResult(draft.operationId(), failure, null, false);
                }

                String now = Instant.now().toString();
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO cities(city_uuid, name, name_key, stage, treasury, revision, founder_uuid, created_at)
                        VALUES (?, ?, ?, 'SETTLEMENT', 0, 1, ?, ?)
                        """)) {
                    insert.setString(1, draft.cityId().toString());
                    insert.setString(2, draft.name());
                    insert.setString(3, draft.nameKey());
                    insert.setString(4, draft.founderId().toString());
                    insert.setString(5, now);
                    insert.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO city_members(city_uuid, player_uuid, last_known_name, role_id, joined_at)
                        VALUES (?, ?, ?, 'RULER', ?)
                        """)) {
                    insert.setString(1, draft.cityId().toString());
                    insert.setString(2, draft.founderId().toString());
                    insert.setString(3, draft.founderName());
                    insert.setString(4, now);
                    insert.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO city_laws(city_uuid, updated_at) VALUES (?, ?)")) {
                    insert.setString(1, draft.cityId().toString());
                    insert.setString(2, now);
                    insert.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO city_tax_policy(city_uuid, updated_at) VALUES (?, ?)")) {
                    insert.setString(1, draft.cityId().toString());
                    insert.setString(2, now);
                    insert.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO city_chunks(world_uuid, chunk_x, chunk_z, city_uuid, claimed_at)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
                    for (ChunkPosition chunk : sortedChunks(draft)) {
                        insert.setString(1, chunk.worldId().toString());
                        insert.setInt(2, chunk.x());
                        insert.setInt(3, chunk.z());
                        insert.setString(4, draft.cityId().toString());
                        insert.setString(5, now);
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                recordFoundationOperation(connection, draft, hash, payload, "COMPLETED", CityFoundationCode.CREATED, now);
                CityView city = readCity(connection, draft.cityId()).orElseThrow(
                        () -> new SQLException("New city could not be read inside its transaction"));
                connection.commit();
                return foundationResult(draft.operationId(), CityFoundationCode.CREATED, city, false);
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    private CityFoundationCode foundingConflict(Connection connection, CityFoundationDraft draft) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM city_members WHERE player_uuid = ?")) {
            query.setString(1, draft.founderId().toString());
            try (ResultSet result = query.executeQuery()) { if (result.next()) return CityFoundationCode.ALREADY_IN_CITY; }
        }
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM cities WHERE name_key = ?")) {
            query.setString(1, draft.nameKey());
            try (ResultSet result = query.executeQuery()) { if (result.next()) return CityFoundationCode.NAME_ALREADY_USED; }
        }
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT 1 FROM city_chunks WHERE world_uuid = ? AND chunk_x = ? AND chunk_z = ?
                """)) {
            for (ChunkPosition chunk : sortedChunks(draft)) {
                query.setString(1, chunk.worldId().toString());
                query.setInt(2, chunk.x());
                query.setInt(3, chunk.z());
                try (ResultSet result = query.executeQuery()) { if (result.next()) return CityFoundationCode.CHUNK_ALREADY_CLAIMED; }
            }
        }
        return null;
    }

    private void validateFootprint(CityFoundationDraft draft) throws SQLException {
        if (draft.initialChunks().size() != 4) throw new SQLException("A city must start with four chunks");
        UUID world = draft.initialChunks().iterator().next().worldId();
        int minX = draft.initialChunks().stream().mapToInt(ChunkPosition::x).min().orElseThrow();
        int maxX = draft.initialChunks().stream().mapToInt(ChunkPosition::x).max().orElseThrow();
        int minZ = draft.initialChunks().stream().mapToInt(ChunkPosition::z).min().orElseThrow();
        int maxZ = draft.initialChunks().stream().mapToInt(ChunkPosition::z).max().orElseThrow();
        if (draft.initialChunks().stream().anyMatch(chunk -> !chunk.worldId().equals(world))
                || (long) maxX - minX != 1 || (long) maxZ - minZ != 1) {
            throw new SQLException("Initial city chunks must form a 2x2 footprint in one world");
        }
    }

    private ExistingOperation findOperation(Connection connection, UUID operationId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT operation_type, request_hash, state, result_code, city_uuid
                FROM city_operations WHERE operation_uuid = ?
                """)) {
            query.setString(1, operationId.toString());
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? new ExistingOperation(result.getString("operation_type"),
                        result.getString("request_hash"), result.getString("state"),
                        result.getString("result_code"), result.getString("city_uuid")) : null;
            }
        }
    }

    private void recordFoundationFailure(Connection connection, CityFoundationDraft draft, String hash,
                                         String payload, CityFoundationCode code) throws SQLException {
        recordFoundationOperation(connection, draft, hash, payload, "FAILED", code, Instant.now().toString());
    }

    private void recordFoundationOperation(Connection connection, CityFoundationDraft draft, String hash,
                                           String payload, String state, CityFoundationCode code, String now)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO city_operations(operation_uuid, operation_type, request_hash, request_payload, state,
                    result_code, city_uuid, actor_uuid, created_at, updated_at)
                VALUES (?, 'FOUND_CITY', ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, draft.operationId().toString());
            insert.setString(2, hash);
            insert.setString(3, payload);
            insert.setString(4, state);
            insert.setString(5, code.name());
            insert.setString(6, state.equals("COMPLETED") ? draft.cityId().toString() : null);
            insert.setString(7, draft.founderId().toString());
            insert.setString(8, now);
            insert.setString(9, now);
            insert.executeUpdate();
        }
    }

    private List<ChunkPosition> sortedChunks(CityFoundationDraft draft) {
        return draft.initialChunks().stream().sorted(Comparator.comparing((ChunkPosition c) -> c.worldId().toString())
                .thenComparingInt(ChunkPosition::x).thenComparingInt(ChunkPosition::z)).toList();
    }

    private String foundationPayload(CityFoundationDraft draft) {
        StringBuilder value = new StringBuilder().append(draft.founderId()).append('\n').append(draft.founderName())
                .append('\n').append(draft.cityId()).append('\n').append(draft.name()).append('\n').append(draft.nameKey());
        for (ChunkPosition chunk : sortedChunks(draft)) value.append('\n').append(chunk.worldId()).append(':').append(chunk.x()).append(':').append(chunk.z());
        return value.toString();
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable", impossible); }
    }

    private CityFoundationCode parseFoundationCode(String value) {
        try { return CityFoundationCode.valueOf(value); }
        catch (IllegalArgumentException invalid) { return CityFoundationCode.INTERNAL_ERROR; }
    }

    private CityFoundationResult foundationResult(UUID operationId, CityFoundationCode code, CityView city, boolean replay) {
        Optional<CityView> view = (code == CityFoundationCode.CREATED || code == CityFoundationCode.REPLAYED)
                ? Optional.ofNullable(city) : Optional.empty();
        if (view.isEmpty() && (code == CityFoundationCode.CREATED || code == CityFoundationCode.REPLAYED)) {
            code = CityFoundationCode.INTERNAL_ERROR;
        }
        return new CityFoundationResult(operationId, code, view, replay);
    }

    private record ExistingOperation(String type, String requestHash, String state, String resultCode, String cityId) { }

    @Override
    public CompletionStage<CityPromotionResult> preparePromotion(CityPromotionDraft draft) {
        return async(() -> promotionTransaction(connection -> {
            String payload = promotionPayload(draft);
            String hash = sha256(payload);
            ExistingOperation operation = findOperation(connection, draft.operationId());
            if (operation != null) return promotionReplay(connection, draft, operation, hash);

            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT operation_uuid FROM city_operations WHERE city_uuid=? AND operation_type='PROMOTE_CITY'
                      AND state IN ('PREPARED','RESERVED','DOMAIN_COMMITTED','COMMIT_PENDING','RELEASE_PENDING')
                    LIMIT 1
                    """)) {
                query.setString(1, draft.cityId().toString());
                try (ResultSet pending = query.executeQuery()) {
                    if (pending.next()) {
                        connection.commit();
                        return promotionResult(draft.operationId(), CityPromotionCode.OPERATION_IN_PROGRESS, null, true);
                    }
                }
            }

            CityPromotionCode failure = promotionFailure(connection, draft);
            String now = Instant.now().toString();
            if (failure != null) {
                writePromotionOperation(connection, draft, hash, payload, "FAILED", failure, now);
                connection.commit();
                return promotionResult(draft.operationId(), failure, null, false);
            }
            writePromotionOperation(connection, draft, hash, payload, "PREPARED", CityPromotionCode.OPERATION_IN_PROGRESS, now);
            connection.commit();
            return promotionResult(draft.operationId(), CityPromotionCode.OPERATION_IN_PROGRESS, null, false);
        }));
    }

    @Override
    public CompletionStage<Boolean> reservePromotion(UUID operationId) {
        return async(() -> {
            try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement("""
                    UPDATE city_operations SET state='RESERVED', updated_at=?
                    WHERE operation_uuid=? AND operation_type='PROMOTE_CITY' AND state='PREPARED'
                    """)) {
                update.setString(1, Instant.now().toString());
                update.setString(2, operationId.toString());
                int changed = update.executeUpdate();
                if (changed == 1) return true;
                ExistingOperation operation = findOperation(connection, operationId);
                return operation != null && operation.type.equals("PROMOTE_CITY") && operation.state.equals("RESERVED");
            }
        });
    }

    @Override
    public CompletionStage<CityPromotionResult> applyPromotion(CityPromotionDraft draft) {
        return async(() -> promotionTransaction(connection -> {
            String payload = promotionPayload(draft);
            String hash = sha256(payload);
            ExistingOperation operation = findOperation(connection, draft.operationId());
            if (operation == null || !operation.type.equals("PROMOTE_CITY") || !operation.requestHash.equals(hash)) {
                connection.rollback();
                return promotionResult(draft.operationId(), CityPromotionCode.OPERATION_CONFLICT, null, true);
            }
            if (operation.state.equals("DOMAIN_COMMITTED") || operation.state.equals("COMMIT_PENDING") || operation.state.equals("COMPLETED")) {
                CityView current = readCity(connection, draft.cityId()).orElse(null);
                connection.commit();
                return promotionResult(draft.operationId(), current == null ? CityPromotionCode.INTERNAL_ERROR
                        : CityPromotionCode.REPLAYED, current, true);
            }
            if (!operation.state.equals("RESERVED")) {
                connection.commit();
                return promotionResult(draft.operationId(), operation.state.equals("FAILED") || operation.state.equals("RELEASED")
                        ? parsePromotionCode(operation.resultCode) : CityPromotionCode.OPERATION_IN_PROGRESS, null, true);
            }

            CityPromotionCode failure = promotionFailure(connection, draft);
            if (failure != null) {
                setOperationState(connection, draft.operationId(), "RELEASE_PENDING", failure);
                connection.commit();
                return promotionResult(draft.operationId(), failure, null, false);
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE cities SET stage=?, treasury=treasury-?, revision=revision+1
                    WHERE city_uuid=? AND stage=? AND revision=? AND treasury>=?
                    """)) {
                update.setString(1, draft.toStage().name());
                update.setLong(2, draft.price());
                update.setString(3, draft.cityId().toString());
                update.setString(4, draft.fromStage().name());
                update.setLong(5, draft.expectedRevision());
                update.setLong(6, draft.price());
                if (update.executeUpdate() != 1) {
                    CityPromotionCode failureAfterCheck = promotionFailure(connection, draft);
                    if (failureAfterCheck == null) failureAfterCheck = CityPromotionCode.STALE_REVISION;
                    setOperationState(connection, draft.operationId(), "RELEASE_PENDING", failureAfterCheck);
                    connection.commit();
                    return promotionResult(draft.operationId(), failureAfterCheck, null, false);
                }
            }
            if (draft.price() > 0) insertTreasuryEntry(connection, draft.operationId(), draft.cityId(), -draft.price(),
                    "STAGE_PROMOTION:" + draft.fromStage().name() + ":" + draft.toStage().name());
            setOperationState(connection, draft.operationId(), "DOMAIN_COMMITTED", CityPromotionCode.PROMOTED);
            CityView updated = readCity(connection, draft.cityId()).orElseThrow(() -> new SQLException("Promoted city disappeared"));
            connection.commit();
            return promotionResult(draft.operationId(), CityPromotionCode.PROMOTED, updated, false);
        }));
    }

    @Override
    public CompletionStage<Void> markPromotionCommitPending(UUID operationId) {
        return async(() -> { updatePromotionState(operationId, "DOMAIN_COMMITTED", "COMMIT_PENDING", CityPromotionCode.UNKNOWN_OUTCOME); return null; });
    }

    @Override
    public CompletionStage<CityPromotionResult> completePromotion(UUID operationId) {
        return async(() -> {
            try (Connection connection = connection()) {
                connection.setAutoCommit(false);
                ExistingOperation operation = findOperation(connection, operationId);
                if (operation == null || !operation.type.equals("PROMOTE_CITY")) {
                    connection.commit();
                    return promotionResult(operationId, CityPromotionCode.OPERATION_CONFLICT, null, true);
                }
                  if (operation.state.equals("DOMAIN_COMMITTED") || operation.state.equals("COMMIT_PENDING")) {
                      setOperationState(connection, operationId, "COMPLETED", CityPromotionCode.PROMOTED);
                      operation = findOperation(connection, operationId);
                  }
                  if (!operation.state.equals("COMPLETED")) {
                      connection.commit();
                      return promotionResult(operationId, CityPromotionCode.OPERATION_IN_PROGRESS, null, true);
                  }
                  CityView city = operation.cityId == null ? null : readCity(connection, UUID.fromString(operation.cityId)).orElse(null);
                  connection.commit();
                  return promotionResult(operationId, city == null ? CityPromotionCode.INTERNAL_ERROR
                          : CityPromotionCode.REPLAYED, city, true);
            }
        });
    }

    @Override
    public CompletionStage<Void> failPromotion(UUID operationId, CityPromotionCode code, boolean releasePending) {
        return async(() -> {
            String expected = releasePending ? "RESERVED" : "PREPARED";
            String target = releasePending ? "RELEASE_PENDING" : "FAILED";
            updatePromotionState(operationId, expected, target, code);
            return null;
        });
    }

    @Override
    public CompletionStage<Void> markPromotionReleased(UUID operationId) {
        return async(() -> {
            try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement("""
                    UPDATE city_operations SET state='RELEASED', updated_at=?
                    WHERE operation_uuid=? AND operation_type='PROMOTE_CITY' AND state='RELEASE_PENDING'
                    """)) {
                update.setString(1, Instant.now().toString());
                update.setString(2, operationId.toString());
                update.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public CompletionStage<Set<ChunkPosition>> claimedChunks(Set<ChunkPosition> candidates) {
        Set<ChunkPosition> copy = Set.copyOf(candidates);
        return async(() -> {
            Set<ChunkPosition> claimed = new HashSet<>();
            try (Connection connection = connection(); PreparedStatement query = connection.prepareStatement(
                    "SELECT 1 FROM city_chunks WHERE world_uuid=? AND chunk_x=? AND chunk_z=?")) {
                for (ChunkPosition chunk : copy) {
                    query.setString(1, chunk.worldId().toString());
                    query.setInt(2, chunk.x());
                    query.setInt(3, chunk.z());
                    try (ResultSet result = query.executeQuery()) { if (result.next()) claimed.add(chunk); }
                }
            }
            return Set.copyOf(claimed);
        });
    }

    @Override
    public CompletionStage<CityUpgradeResult> purchaseUpgrade(CityUpgradeDraft draft) {
        return async(() -> upgradeTransaction(connection -> purchaseUpgrade(connection, draft)));
    }

    private CityUpgradeResult purchaseUpgrade(Connection connection, CityUpgradeDraft draft) throws SQLException {
        String payload = upgradePayload(draft);
        String hash = sha256(payload);
        ExistingOperation existing = findOperation(connection, draft.operationId());
        if (existing != null) {
            if (!existing.type.equals("BUY_CITY_UPGRADE") || !existing.requestHash.equals(hash)) {
                return upgradeResult(draft.operationId(), CityUpgradeCode.OPERATION_CONFLICT, null, true);
            }
            if (existing.state.equals("COMPLETED")) {
                CityView city = readCity(connection, draft.cityId()).orElse(null);
                return upgradeResult(draft.operationId(), city == null ? CityUpgradeCode.NOT_FOUND
                        : CityUpgradeCode.REPLAYED, city, true);
            }
            if (existing.state.equals("FAILED")) {
                CityUpgradeCode prior;
                try { prior = CityUpgradeCode.valueOf(existing.resultCode); }
                catch (IllegalArgumentException invalid) { prior = CityUpgradeCode.INTERNAL_ERROR; }
                return upgradeResult(draft.operationId(), prior, null, true);
            }
            return upgradeResult(draft.operationId(), CityUpgradeCode.OPERATION_IN_PROGRESS, null, true);
        }

        CityView city = readCity(connection, draft.cityId()).orElse(null);
        CityUpgradeCode failure = city == null ? CityUpgradeCode.NOT_FOUND : null;
        if (failure == null) {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT role_id FROM city_members WHERE city_uuid=? AND player_uuid=?")) {
                query.setString(1, draft.cityId().toString());
                query.setString(2, draft.actorId().toString());
                try (ResultSet result = query.executeQuery()) {
                    if (!result.next()) failure = CityUpgradeCode.NOT_MEMBER;
                    else if (!CityRole.fromStorage(result.getString("role_id"))
                            .allows(ru.servermine.cities.api.CityPermission.PROMOTE_STAGE)) {
                        failure = CityUpgradeCode.PERMISSION_DENIED;
                    }
                }
            }
        }
        if (failure == null && city.revision() != draft.expectedRevision()) failure = CityUpgradeCode.STALE_REVISION;
        if (failure == null && city.stage().ordinal() < draft.minimumStage().ordinal()) failure = CityUpgradeCode.STAGE_REQUIRED;
        if (failure == null && city.upgrades().contains(draft.upgradeId())) failure = CityUpgradeCode.ALREADY_OWNED;
        if (failure == null && !city.upgrades().containsAll(draft.prerequisites())) failure = CityUpgradeCode.PREREQUISITE_REQUIRED;
        if (failure == null && city.treasury() < draft.price()) failure = CityUpgradeCode.INSUFFICIENT_TREASURY;
        if (failure != null) {
            recordUpgradeOperation(connection, draft, hash, "FAILED", failure);
            return upgradeResult(draft.operationId(), failure, null, false);
        }

        String now = Instant.now().toString();
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO city_upgrades(city_uuid, upgrade_id, purchased_by, purchased_at) VALUES (?, ?, ?, ?)
                """)) {
            insert.setString(1, draft.cityId().toString());
            insert.setString(2, draft.upgradeId().name());
            insert.setString(3, draft.actorId().toString());
            insert.setString(4, now);
            insert.executeUpdate();
        }
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE cities SET treasury=treasury-?, revision=revision+1
                WHERE city_uuid=? AND revision=? AND treasury>=?
                """)) {
            update.setLong(1, draft.price());
            update.setString(2, draft.cityId().toString());
            update.setLong(3, draft.expectedRevision());
            update.setLong(4, draft.price());
            if (update.executeUpdate() != 1) throw new SQLException("City changed during upgrade purchase");
        }
        recordUpgradeOperation(connection, draft, hash, "COMPLETED", CityUpgradeCode.PURCHASED);
        insertTreasuryEntry(connection, draft.operationId(), draft.cityId(), -draft.price(), "CITY_UPGRADE_" + draft.upgradeId().name());
        CityView updated = readCity(connection, draft.cityId()).orElseThrow(() -> new SQLException("Purchased city disappeared"));
        return upgradeResult(draft.operationId(), CityUpgradeCode.PURCHASED, updated, false);
    }

    private void recordUpgradeOperation(Connection connection, CityUpgradeDraft draft, String hash,
                                        String state, CityUpgradeCode code) throws SQLException {
        String now = Instant.now().toString();
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO city_operations(operation_uuid, operation_type, request_hash, request_payload, state,
                    result_code, city_uuid, actor_uuid, created_at, updated_at)
                VALUES (?, 'BUY_CITY_UPGRADE', ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, draft.operationId().toString());
            insert.setString(2, hash);
            insert.setString(3, upgradePayload(draft));
            insert.setString(4, state);
            insert.setString(5, code.name());
            insert.setString(6, draft.cityId().toString());
            insert.setString(7, draft.actorId().toString());
            insert.setString(8, now);
            insert.setString(9, now);
            insert.executeUpdate();
        }
    }

    private String upgradePayload(CityUpgradeDraft draft) {
        return draft.cityId() + "|" + draft.actorId() + "|" + draft.expectedRevision() + "|"
                + draft.upgradeId() + "|" + draft.minimumStage() + "|" + draft.price() + "|"
                + draft.prerequisites().stream().sorted().toList();
    }

    private CityUpgradeResult upgradeResult(UUID operationId, CityUpgradeCode code, CityView city, boolean replay) {
        Optional<CityView> view = code == CityUpgradeCode.PURCHASED || code == CityUpgradeCode.REPLAYED
                ? Optional.ofNullable(city) : Optional.empty();
        if (view.isEmpty() && (code == CityUpgradeCode.PURCHASED || code == CityUpgradeCode.REPLAYED)) {
            code = CityUpgradeCode.INTERNAL_ERROR;
        }
        return new CityUpgradeResult(operationId, code, view, replay);
    }

    private <T> T upgradeTransaction(SqlTransaction<T> action) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                T result = action.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    @Override
    public CompletionStage<CityClaimResult> prepareClaim(CityClaimDraft draft) {
        return async(() -> promotionTransaction(connection -> {
            String payload = claimPayload(draft);
            String hash = sha256(payload);
            ExistingOperation operation = findOperation(connection, draft.operationId());
            if (operation != null) return claimReplay(connection, draft, operation, hash);
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT operation_uuid FROM city_operations WHERE city_uuid=? AND operation_type='CLAIM_CHUNK'
                      AND state IN ('PREPARED','RESERVED','DOMAIN_COMMITTED','COMMIT_PENDING','RELEASE_PENDING')
                    LIMIT 1
                    """)) {
                query.setString(1, draft.cityId().toString());
                try (ResultSet pending = query.executeQuery()) {
                    if (pending.next()) {
                        connection.commit();
                        return claimResult(draft.operationId(), CityClaimCode.OPERATION_IN_PROGRESS, null, true);
                    }
                }
            }
            CityClaimCode failure = claimFailure(connection, draft);
            String now = Instant.now().toString();
            if (failure != null) {
                writeClaimOperation(connection, draft, hash, payload, "FAILED", failure, now);
                connection.commit();
                return claimResult(draft.operationId(), failure, null, false);
            }
            writeClaimOperation(connection, draft, hash, payload, "PREPARED", CityClaimCode.OPERATION_IN_PROGRESS, now);
            connection.commit();
            return claimResult(draft.operationId(), CityClaimCode.OPERATION_IN_PROGRESS, null, false);
        }));
    }

    @Override
    public CompletionStage<Boolean> reserveClaim(UUID operationId) {
        return async(() -> {
            try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement("""
                    UPDATE city_operations SET state='RESERVED', updated_at=?
                    WHERE operation_uuid=? AND operation_type='CLAIM_CHUNK' AND state='PREPARED'
                    """)) {
                update.setString(1, Instant.now().toString());
                update.setString(2, operationId.toString());
                if (update.executeUpdate() == 1) return true;
                ExistingOperation operation = findOperation(connection, operationId);
                return operation != null && operation.type.equals("CLAIM_CHUNK") && operation.state.equals("RESERVED");
            }
        });
    }

    @Override
    public CompletionStage<CityClaimResult> applyClaim(CityClaimDraft draft) {
        return async(() -> promotionTransaction(connection -> {
            String hash = sha256(claimPayload(draft));
            ExistingOperation operation = findOperation(connection, draft.operationId());
            if (operation == null || !operation.type.equals("CLAIM_CHUNK") || !operation.requestHash.equals(hash)) {
                connection.rollback();
                return claimResult(draft.operationId(), CityClaimCode.OPERATION_CONFLICT, null, true);
            }
            if (operation.state.equals("DOMAIN_COMMITTED") || operation.state.equals("COMMIT_PENDING")
                    || operation.state.equals("COMPLETED")) {
                CityView current = readCity(connection, draft.cityId()).orElse(null);
                connection.commit();
                return claimResult(draft.operationId(), current == null ? CityClaimCode.INTERNAL_ERROR
                        : CityClaimCode.REPLAYED, current, true);
            }
            if (!operation.state.equals("RESERVED")) {
                connection.commit();
                CityClaimCode code = operation.state.equals("FAILED") || operation.state.equals("RELEASED")
                        ? parseClaimCode(operation.resultCode) : CityClaimCode.OPERATION_IN_PROGRESS;
                return claimResult(draft.operationId(), code, null, true);
            }
            CityClaimCode failure = claimFailure(connection, draft);
            if (failure != null) {
                setClaimState(connection, draft.operationId(), "RELEASE_PENDING", failure);
                connection.commit();
                return claimResult(draft.operationId(), failure, null, false);
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO city_chunks(world_uuid, chunk_x, chunk_z, city_uuid, claimed_at)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                insert.setString(1, draft.target().worldId().toString());
                insert.setInt(2, draft.target().x());
                insert.setInt(3, draft.target().z());
                insert.setString(4, draft.cityId().toString());
                insert.setString(5, Instant.now().toString());
                insert.executeUpdate();
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE cities SET treasury=treasury-?, revision=revision+1
                    WHERE city_uuid=? AND revision=? AND stage<>'SETTLEMENT' AND treasury>=?
                    """)) {
                update.setLong(1, draft.price());
                update.setString(2, draft.cityId().toString());
                update.setLong(3, draft.expectedRevision());
                update.setLong(4, draft.price());
                if (update.executeUpdate() != 1) throw new SQLException("City changed or treasury balance changed while claiming chunk");
            }
            if (draft.price() > 0) insertTreasuryEntry(connection, draft.operationId(), draft.cityId(), -draft.price(),
                    "CHUNK_CLAIM:" + draft.target().worldId() + ":" + draft.target().x() + ":" + draft.target().z());
            setClaimState(connection, draft.operationId(), "DOMAIN_COMMITTED", CityClaimCode.CLAIMED);
            CityView updated = readCity(connection, draft.cityId()).orElseThrow(() -> new SQLException("Claimed city disappeared"));
            connection.commit();
            return claimResult(draft.operationId(), CityClaimCode.CLAIMED, updated, false);
        }));
    }

    @Override
    public CompletionStage<Void> markClaimCommitPending(UUID operationId) {
        return async(() -> { updateClaimState(operationId, "DOMAIN_COMMITTED", "COMMIT_PENDING", CityClaimCode.UNKNOWN_OUTCOME); return null; });
    }

    @Override
    public CompletionStage<CityClaimResult> completeClaim(UUID operationId) {
        return async(() -> {
            try (Connection connection = connection()) {
                connection.setAutoCommit(false);
                ExistingOperation operation = findOperation(connection, operationId);
                if (operation == null || !operation.type.equals("CLAIM_CHUNK")) {
                    connection.commit();
                    return claimResult(operationId, CityClaimCode.OPERATION_CONFLICT, null, true);
                }
                if (operation.state.equals("DOMAIN_COMMITTED") || operation.state.equals("COMMIT_PENDING")) {
                    setClaimState(connection, operationId, "COMPLETED", CityClaimCode.CLAIMED);
                    operation = findOperation(connection, operationId);
                }
                if (!operation.state.equals("COMPLETED")) {
                    connection.commit();
                    return claimResult(operationId, CityClaimCode.OPERATION_IN_PROGRESS, null, true);
                }
                CityView city = operation.cityId == null ? null : readCity(connection, UUID.fromString(operation.cityId)).orElse(null);
                connection.commit();
                return claimResult(operationId, city == null ? CityClaimCode.INTERNAL_ERROR : CityClaimCode.REPLAYED, city, true);
            }
        });
    }

    @Override
    public CompletionStage<Void> failClaim(UUID operationId, CityClaimCode code) {
        return async(() -> { updateClaimState(operationId, "PREPARED", "FAILED", code); return null; });
    }

    @Override
    public CompletionStage<Void> markClaimReleasePending(UUID operationId, CityClaimCode code) {
        return async(() -> {
            try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement("""
                    UPDATE city_operations SET state='RELEASE_PENDING', result_code=?, updated_at=?
                    WHERE operation_uuid=? AND operation_type='CLAIM_CHUNK' AND state IN ('PREPARED','RESERVED')
                    """)) {
                update.setString(1, code.name());
                update.setString(2, Instant.now().toString());
                update.setString(3, operationId.toString());
                update.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public CompletionStage<Void> markClaimReleased(UUID operationId) {
        return async(() -> {
            try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement("""
                    UPDATE city_operations SET state='RELEASED', updated_at=?
                    WHERE operation_uuid=? AND operation_type='CLAIM_CHUNK' AND state='RELEASE_PENDING'
                    """)) {
                update.setString(1, Instant.now().toString());
                update.setString(2, operationId.toString());
                update.executeUpdate();
                return null;
            }
        });
    }

    private CityClaimCode claimFailure(Connection connection, CityClaimDraft draft) throws SQLException {
        CityView city = readCity(connection, draft.cityId()).orElse(null);
        if (city == null) return CityClaimCode.NOT_FOUND;
        try (PreparedStatement query = connection.prepareStatement("SELECT role_id FROM city_members WHERE city_uuid=? AND player_uuid=?")) {
            query.setString(1, draft.cityId().toString());
            query.setString(2, draft.actorId().toString());
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) return CityClaimCode.NOT_MEMBER;
                if (!CityRole.fromStorage(result.getString("role_id"))
                        .allows(ru.servermine.cities.api.CityPermission.CLAIM_TERRITORY)) return CityClaimCode.PERMISSION_DENIED;
            }
        }
        if (city.revision() != draft.expectedRevision()) return CityClaimCode.STALE_REVISION;
        if (city.stage() == CityStage.SETTLEMENT) return CityClaimCode.PROMOTION_REQUIRED;
        if (city.chunks().size() >= city.stage().chunkLimit()) return CityClaimCode.CAP_REACHED;
        if (city.chunks().contains(draft.target())) return CityClaimCode.ALREADY_OWNED;
        if (city.chunks().stream().noneMatch(draft.target()::adjacentTo)) return CityClaimCode.DISCONNECTED;
        if (city.treasury() < draft.price()) return CityClaimCode.INSUFFICIENT_TREASURY;
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT 1 FROM city_chunks WHERE world_uuid=? AND chunk_x=? AND chunk_z=?
                """)) {
            query.setString(1, draft.target().worldId().toString());
            query.setInt(2, draft.target().x());
            query.setInt(3, draft.target().z());
            try (ResultSet result = query.executeQuery()) { if (result.next()) return CityClaimCode.TARGET_UNAVAILABLE; }
        }
        return null;
    }

    private CityClaimResult claimReplay(Connection connection, CityClaimDraft draft, ExistingOperation operation,
                                        String hash) throws SQLException {
        if (!operation.type.equals("CLAIM_CHUNK") || !operation.requestHash.equals(hash)) {
            connection.commit();
            return claimResult(draft.operationId(), CityClaimCode.OPERATION_CONFLICT, null, true);
        }
        if (operation.state.equals("COMPLETED")) {
            CityView city = operation.cityId == null ? null : readCity(connection, UUID.fromString(operation.cityId)).orElse(null);
            connection.commit();
            return claimResult(draft.operationId(), city == null ? CityClaimCode.INTERNAL_ERROR : CityClaimCode.REPLAYED, city, true);
        }
        if (operation.state.equals("FAILED") || operation.state.equals("RELEASED")) {
            connection.commit();
            return claimResult(draft.operationId(), parseClaimCode(operation.resultCode), null, true);
        }
        connection.commit();
        return claimResult(draft.operationId(), CityClaimCode.OPERATION_IN_PROGRESS, null, true);
    }

    private void writeClaimOperation(Connection connection, CityClaimDraft draft, String hash, String payload,
                                     String state, CityClaimCode code, String now) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO city_operations(operation_uuid, operation_type, request_hash, request_payload, state,
                    result_code, city_uuid, actor_uuid, created_at, updated_at)
                VALUES (?, 'CLAIM_CHUNK', ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, draft.operationId().toString()); insert.setString(2, hash); insert.setString(3, payload);
            insert.setString(4, state); insert.setString(5, code.name()); insert.setString(6, draft.cityId().toString());
            insert.setString(7, draft.actorId().toString()); insert.setString(8, now); insert.setString(9, now);
            insert.executeUpdate();
        }
    }

    private String claimPayload(CityClaimDraft draft) {
        return draft.cityId() + "\n" + draft.actorId() + "\n" + draft.expectedRevision() + "\n"
                + draft.target().worldId() + "\n" + draft.target().x() + "\n" + draft.target().z() + "\n" + draft.price();
    }

    private CityClaimCode parseClaimCode(String value) {
        try { return CityClaimCode.valueOf(value); }
        catch (IllegalArgumentException invalid) { return CityClaimCode.INTERNAL_ERROR; }
    }

    private CityClaimResult claimResult(UUID operationId, CityClaimCode code, CityView city, boolean replay) {
        Optional<CityView> view = code == CityClaimCode.CLAIMED || code == CityClaimCode.REPLAYED
                ? Optional.ofNullable(city) : Optional.empty();
        if (view.isEmpty() && (code == CityClaimCode.CLAIMED || code == CityClaimCode.REPLAYED)) code = CityClaimCode.INTERNAL_ERROR;
        return new CityClaimResult(operationId, code, view, replay);
    }

    private void setClaimState(Connection connection, UUID operationId, String state, CityClaimCode code) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE city_operations SET state=?, result_code=?, updated_at=?
                WHERE operation_uuid=? AND operation_type='CLAIM_CHUNK'
                """)) {
            update.setString(1, state); update.setString(2, code.name()); update.setString(3, Instant.now().toString());
            update.setString(4, operationId.toString());
            if (update.executeUpdate() != 1) throw new SQLException("Claim journal entry disappeared");
        }
    }

    private void updateClaimState(UUID operationId, String expected, String state, CityClaimCode code) throws SQLException {
        try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement("""
                UPDATE city_operations SET state=?, result_code=?, updated_at=?
                WHERE operation_uuid=? AND operation_type='CLAIM_CHUNK' AND state=?
                """)) {
            update.setString(1, state); update.setString(2, code.name()); update.setString(3, Instant.now().toString());
            update.setString(4, operationId.toString()); update.setString(5, expected); update.executeUpdate();
        }
    }

    private CityPromotionCode promotionFailure(Connection connection, CityPromotionDraft draft) throws SQLException {
        CityView city = readCity(connection, draft.cityId()).orElse(null);
        if (city == null) return CityPromotionCode.NOT_FOUND;
        if (city.revision() != draft.expectedRevision()) return CityPromotionCode.STALE_REVISION;
        if (city.stage() != draft.fromStage() || city.stage().ordinal() + 1 != draft.toStage().ordinal()) {
            return CityPromotionCode.INVALID_STAGE;
        }
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT role_id FROM city_members WHERE city_uuid=? AND player_uuid=?
                """)) {
            query.setString(1, draft.cityId().toString());
            query.setString(2, draft.actorId().toString());
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) return CityPromotionCode.NOT_MEMBER;
                if (!CityRole.fromStorage(result.getString("role_id"))
                        .allows(ru.servermine.cities.api.CityPermission.PROMOTE_STAGE)) return CityPromotionCode.PERMISSION_DENIED;
            }
        }
        if (city.residents().size() < draft.minimumResidents() || city.chunks().size() < draft.minimumChunks()) {
            return CityPromotionCode.REQUIREMENTS_NOT_MET;
        }
        if (city.treasury() < draft.price()) return CityPromotionCode.INSUFFICIENT_TREASURY;
        return null;
    }

    private CityPromotionResult promotionReplay(Connection connection, CityPromotionDraft draft,
                                                 ExistingOperation operation, String hash) throws SQLException {
        if (!operation.type.equals("PROMOTE_CITY") || !operation.requestHash.equals(hash)) {
            connection.commit();
            return promotionResult(draft.operationId(), CityPromotionCode.OPERATION_CONFLICT, null, true);
        }
        if (operation.state.equals("COMPLETED")) {
            CityView city = operation.cityId == null ? null : readCity(connection, UUID.fromString(operation.cityId)).orElse(null);
            connection.commit();
            return promotionResult(draft.operationId(), city == null ? CityPromotionCode.INTERNAL_ERROR
                    : CityPromotionCode.REPLAYED, city, true);
        }
        if (operation.state.equals("FAILED") || operation.state.equals("RELEASED")) {
            connection.commit();
            return promotionResult(draft.operationId(), parsePromotionCode(operation.resultCode), null, true);
        }
        connection.commit();
        return promotionResult(draft.operationId(), CityPromotionCode.OPERATION_IN_PROGRESS, null, true);
    }

    private void writePromotionOperation(Connection connection, CityPromotionDraft draft, String hash,
                                         String payload, String state, CityPromotionCode code, String now) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO city_operations(operation_uuid, operation_type, request_hash, request_payload, state,
                    result_code, city_uuid, actor_uuid, created_at, updated_at)
                VALUES (?, 'PROMOTE_CITY', ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, draft.operationId().toString());
            insert.setString(2, hash);
            insert.setString(3, payload);
            insert.setString(4, state);
            insert.setString(5, code.name());
            insert.setString(6, draft.cityId().toString());
            insert.setString(7, draft.actorId().toString());
            insert.setString(8, now);
            insert.setString(9, now);
            insert.executeUpdate();
        }
    }

    private String promotionPayload(CityPromotionDraft draft) {
        return draft.cityId() + "\n" + draft.actorId() + "\n" + draft.expectedRevision() + "\n"
                + draft.fromStage() + "\n" + draft.toStage() + "\n" + draft.price() + "\n"
                + draft.minimumResidents() + "\n" + draft.minimumChunks();
    }

    private CityPromotionCode parsePromotionCode(String value) {
        try { return CityPromotionCode.valueOf(value); }
        catch (IllegalArgumentException invalid) { return CityPromotionCode.INTERNAL_ERROR; }
    }

    private CityPromotionResult promotionResult(UUID operationId, CityPromotionCode code, CityView city, boolean replay) {
        Optional<CityView> view = code == CityPromotionCode.PROMOTED || code == CityPromotionCode.REPLAYED
                ? Optional.ofNullable(city) : Optional.empty();
        if (view.isEmpty() && (code == CityPromotionCode.PROMOTED || code == CityPromotionCode.REPLAYED)) {
            code = CityPromotionCode.INTERNAL_ERROR;
        }
        return new CityPromotionResult(operationId, code, view, replay);
    }

    private <T> T promotionTransaction(SqlTransaction<T> action) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try { return action.run(connection); }
            catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
        }
    }

    private void setOperationState(Connection connection, UUID operationId, String state, CityPromotionCode code) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE city_operations SET state=?, result_code=?, updated_at=?
                WHERE operation_uuid=? AND operation_type='PROMOTE_CITY'
                """)) {
            update.setString(1, state);
            update.setString(2, code.name());
            update.setString(3, Instant.now().toString());
            update.setString(4, operationId.toString());
            if (update.executeUpdate() != 1) throw new SQLException("Promotion journal entry disappeared");
        }
    }

    private void updatePromotionState(UUID operationId, String expected, String target, CityPromotionCode code) throws SQLException {
        try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement("""
                UPDATE city_operations SET state=?, result_code=?, updated_at=?
                WHERE operation_uuid=? AND operation_type='PROMOTE_CITY' AND state=?
                """)) {
            update.setString(1, target);
            update.setString(2, code.name());
            update.setString(3, Instant.now().toString());
            update.setString(4, operationId.toString());
            update.setString(5, expected);
            update.executeUpdate();
        }
    }

    private Optional<CityView> readCity(UUID cityId) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            Optional<CityView> city = readCity(connection, cityId);
            connection.commit();
            return city;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load city " + cityId, e);
        }
    }

    private Optional<CityView> readCity(Connection connection, UUID cityId) throws SQLException {
        String name;
        CityStage stage;
        long treasury;
        long revision;
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT name, stage, treasury, revision FROM cities WHERE city_uuid = ?
                """)) {
            query.setString(1, cityId.toString());
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) return Optional.empty();
                name = result.getString("name");
                stage = CityStage.valueOf(result.getString("stage"));
                treasury = result.getLong("treasury");
                revision = result.getLong("revision");
            }
        }

        var chunks = new java.util.LinkedHashSet<ChunkPosition>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT world_uuid, chunk_x, chunk_z FROM city_chunks WHERE city_uuid = ? ORDER BY world_uuid, chunk_x, chunk_z")) {
            query.setString(1, cityId.toString());
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    chunks.add(new ChunkPosition(UUID.fromString(result.getString("world_uuid")),
                            result.getInt("chunk_x"), result.getInt("chunk_z")));
                }
            }
        }

        List<CityView.ResidentView> residents = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT player_uuid, last_known_name, role_id FROM city_members
                WHERE city_uuid = ? ORDER BY joined_at, player_uuid
                """)) {
            query.setString(1, cityId.toString());
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    residents.add(new CityView.ResidentView(UUID.fromString(result.getString("player_uuid")),
                            result.getString("last_known_name"), result.getString("role_id")));
                }
            }
        }
        Set<CityUpgradeId> upgrades = new HashSet<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT upgrade_id FROM city_upgrades WHERE city_uuid=? ORDER BY upgrade_id")) {
            query.setString(1, cityId.toString());
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) upgrades.add(CityUpgradeId.valueOf(result.getString("upgrade_id")));
            }
        }
        CityLaws laws = CityLaws.DEFAULT;
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT pvp_allowed, outsider_interactions_allowed, fire_allowed, explosions_allowed
                FROM city_laws WHERE city_uuid=?
                """)) {
            query.setString(1, cityId.toString());
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) laws = new CityLaws(result.getInt(1) != 0, result.getInt(2) != 0,
                        result.getInt(3) != 0, result.getInt(4) != 0);
            }
        }
        CityTaxPolicy taxPolicy = CityTaxPolicy.DEFAULT;
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT enabled, daily_amount, next_collection_at FROM city_tax_policy WHERE city_uuid=?
                """)) {
            query.setString(1, cityId.toString());
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    String nextAt = result.getString("next_collection_at");
                    taxPolicy = new CityTaxPolicy(result.getInt("enabled") != 0, result.getLong("daily_amount"),
                            nextAt == null ? null : Instant.parse(nextAt));
                }
            }
        }
        return Optional.of(new CityView(cityId, name, stage, Set.copyOf(chunks), treasury, revision,
                List.copyOf(residents), Set.copyOf(upgrades), laws, taxPolicy));
    }

    private Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=" + busyTimeoutMillis);
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA synchronous=FULL");
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    private <T> CompletableFuture<T> async(SqlOperation<T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return operation.run();
            } catch (SQLException e) {
                throw new IllegalStateException("Cities database operation failed", e);
            }
        }, ioExecutor);
    }

    @FunctionalInterface
    private interface SqlOperation<T> {
        T run() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlTransaction<T> {
        T run(Connection connection) throws SQLException;
    }
}
