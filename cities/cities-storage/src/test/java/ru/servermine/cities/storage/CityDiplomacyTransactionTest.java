package ru.servermine.cities.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.servermine.cities.api.*;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import ru.servermine.cities.core.CityTreasuryDraft;

import static org.junit.jupiter.api.Assertions.*;

class CityDiplomacyTransactionTest {
    @TempDir Path directory;

    @Test void proposalsRequireBothCityLeadersAndTransitionsAreJournaled() throws Exception {
        Path database = directory.resolve("cities.db");
        SqliteCityRepository repository = new SqliteCityRepository(database, 5_000, Runnable::run);
        repository.initialize();
        UUID firstCity = UUID.randomUUID(), firstRuler = UUID.randomUUID();
        UUID secondCity = UUID.randomUUID(), secondRuler = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        found(repository, firstCity, firstRuler, world, 0, "Northhold");
        found(repository, secondCity, secondRuler, world, 20, "Southhold");

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var insert = connection.prepareStatement("INSERT INTO city_members(city_uuid,player_uuid,last_known_name,role_id,joined_at) VALUES(?,?,?,?,?)")) {
            insert.setString(1, firstCity.toString()); insert.setString(2, UUID.randomUUID().toString());
            insert.setString(3, "Builder"); insert.setString(4, "BUILDER"); insert.setString(5, "now"); insert.executeUpdate();
        }

        UUID treatyId = UUID.randomUUID();
        CityDiplomacyRequest propose = new CityDiplomacyRequest(treatyId, firstCity, firstRuler,
                CityDiplomacyRequest.Action.PROPOSE, secondCity, CityTreatyType.ALLIANCE, 30);
        assertEquals(CityDiplomacyCode.PERMISSION_DENIED,
                repository.executeDiplomacy(new CityDiplomacyRequest(UUID.randomUUID(), firstCity,
                        repository.find(firstCity).toCompletableFuture().join().orElseThrow().residents().stream()
                                .filter(r -> r.name().equals("Builder")).findFirst().orElseThrow().playerId(),
                        CityDiplomacyRequest.Action.PROPOSE, secondCity, CityTreatyType.ALLIANCE, 30))
                        .toCompletableFuture().join().code());
        assertEquals(CityDiplomacyCode.CREATED, repository.executeDiplomacy(propose).toCompletableFuture().join().code());
        assertEquals(CityDiplomacyCode.ALREADY_EXISTS, repository.executeDiplomacy(new CityDiplomacyRequest(
                UUID.randomUUID(), firstCity, firstRuler, CityDiplomacyRequest.Action.PROPOSE,
                secondCity, CityTreatyType.ALLIANCE, 30)).toCompletableFuture().join().code());
        assertEquals(CityDiplomacyCode.WRONG_SIDE, repository.executeDiplomacy(new CityDiplomacyRequest(
                treatyId, firstCity, firstRuler, CityDiplomacyRequest.Action.ACCEPT,
                null, null, 30)).toCompletableFuture().join().code());
        var accepted = repository.executeDiplomacy(new CityDiplomacyRequest(treatyId, secondCity, secondRuler,
                CityDiplomacyRequest.Action.ACCEPT, null, null, 30)).toCompletableFuture().join();
        assertEquals(CityDiplomacyCode.ACCEPTED, accepted.code());
        assertEquals(CityTreatyState.ACTIVE, accepted.treaty().orElseThrow().state());
        assertTrue(accepted.treaty().orElseThrow().expiresAt().isAfter(java.time.Instant.now().plusSeconds(29L * 86400)));
        assertEquals(CityDiplomacyCode.TERMINATED, repository.executeDiplomacy(new CityDiplomacyRequest(treatyId,
                secondCity, secondRuler, CityDiplomacyRequest.Action.TERMINATE, null, null, 30))
                .toCompletableFuture().join().code());
        assertEquals(CityTreatyState.TERMINATED, repository.diplomacyTreaties(firstCity).toCompletableFuture().join()
                .getFirst().state());
        assertEquals(3, repository.diplomacyHistory(firstCity, 10).toCompletableFuture().join().size());
    }

    @Test void expiredProposalIsPersistedAndAddedToHistoryOnRead() throws Exception {
        Path database = directory.resolve("expiry.db");
        SqliteCityRepository repository = new SqliteCityRepository(database, 5_000, Runnable::run);
        repository.initialize();
        UUID a = UUID.randomUUID(), ownerA = UUID.randomUUID(), b = UUID.randomUUID(), ownerB = UUID.randomUUID();
        found(repository, a, ownerA, UUID.randomUUID(), 0, "East");
        found(repository, b, ownerB, UUID.randomUUID(), 0, "West");
        UUID treaty = UUID.randomUUID();
        assertEquals(CityDiplomacyCode.CREATED, repository.executeDiplomacy(new CityDiplomacyRequest(treaty, a,
                ownerA, CityDiplomacyRequest.Action.PROPOSE, b, CityTreatyType.TRADE, 7)).toCompletableFuture().join().code());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var update = connection.prepareStatement("UPDATE city_treaties SET expires_at='2000-01-01T00:00:00Z' WHERE treaty_uuid=?")) {
            update.setString(1, treaty.toString()); update.executeUpdate();
        }
        var treaties = repository.diplomacyTreaties(a).toCompletableFuture().join();
        assertEquals(CityTreatyState.EXPIRED, treaties.getFirst().state());
        assertEquals("EXPIRED", repository.diplomacyHistory(a, 10).toCompletableFuture().join().getFirst().event());
    }

    @Test void treasuryTransferRequiresTradeTreatyAndAtomicallyWritesBothLedgers() throws Exception {
        Path database = directory.resolve("trade.db");
        SqliteCityRepository repository = new SqliteCityRepository(database, 5_000, Runnable::run);
        repository.initialize();
        UUID a = UUID.randomUUID(), ownerA = UUID.randomUUID(), b = UUID.randomUUID(), ownerB = UUID.randomUUID();
        found(repository, a, ownerA, UUID.randomUUID(), 0, "TraderA");
        found(repository, b, ownerB, UUID.randomUUID(), 0, "TraderB");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var update = connection.prepareStatement("UPDATE cities SET treasury=CASE city_uuid WHEN ? THEN 500 ELSE 100 END")) {
            update.setString(1, a.toString()); update.executeUpdate();
        }
        UUID operation = UUID.randomUUID();
        CityTreasuryTransferRequest request = new CityTreasuryTransferRequest(operation, ownerA, b, 120);
        assertEquals(CityTreasuryTransferCode.TRADE_REQUIRED, repository.transferTreasury(request, 200).toCompletableFuture().join().code());

        UUID treaty = UUID.randomUUID();
        assertEquals(CityDiplomacyCode.CREATED, repository.executeDiplomacy(new CityDiplomacyRequest(treaty, a, ownerA,
                CityDiplomacyRequest.Action.PROPOSE, b, CityTreatyType.TRADE, 30)).toCompletableFuture().join().code());
        assertEquals(CityDiplomacyCode.ACCEPTED, repository.executeDiplomacy(new CityDiplomacyRequest(treaty, b, ownerB,
                CityDiplomacyRequest.Action.ACCEPT, null, null, 30)).toCompletableFuture().join().code());
        assertEquals(CityTreasuryTransferCode.SENT, repository.transferTreasury(request, 200).toCompletableFuture().join().code());
        assertEquals(CityTreasuryTransferCode.REPLAYED, repository.transferTreasury(request, 200).toCompletableFuture().join().code());
        CityTreasuryTransferRequest conflict = new CityTreasuryTransferRequest(operation, ownerA, b, 121);
        assertEquals(CityTreasuryTransferCode.OPERATION_CONFLICT, repository.transferTreasury(conflict, 200).toCompletableFuture().join().code());
        assertEquals(CityTreasuryTransferCode.LIMIT_EXCEEDED, repository.transferTreasury(
                new CityTreasuryTransferRequest(UUID.randomUUID(), ownerA, b, 201), 200).toCompletableFuture().join().code());
        assertEquals(380, repository.find(a).toCompletableFuture().join().orElseThrow().treasury());
        assertEquals(220, repository.find(b).toCompletableFuture().join().orElseThrow().treasury());
        assertEquals(1, repository.treasuryHistory(a, 10).toCompletableFuture().join().size());
        assertEquals(1, repository.treasuryHistory(b, 10).toCompletableFuture().join().size());
    }

    @Test void cityLawsArePersistedPermissionCheckedAndRevisionGuarded() throws Exception {
        Path database = directory.resolve("laws.db");
        SqliteCityRepository repository = new SqliteCityRepository(database, 5_000, Runnable::run);
        repository.initialize();
        UUID cityId = UUID.randomUUID(), rulerId = UUID.randomUUID(), world = UUID.randomUUID();
        found(repository, cityId, rulerId, world, 0, "Lawhold");
        CityView city = repository.find(cityId).toCompletableFuture().join().orElseThrow();
        assertEquals(CityLaws.DEFAULT, city.laws());
        var changed = new CityLaws(true, true, true, false);
        UUID outsider = UUID.randomUUID();
        assertEquals(CityLawsCode.PERMISSION_DENIED, repository.updateCityLaws(new CityLawsRequest(cityId,
                outsider, city.revision(), changed)).toCompletableFuture().join().code());
        assertEquals(CityLawsCode.UPDATED, repository.updateCityLaws(new CityLawsRequest(cityId,
                rulerId, city.revision(), changed)).toCompletableFuture().join().code());
        CityView updated = repository.find(cityId).toCompletableFuture().join().orElseThrow();
        assertEquals(changed, updated.laws());
        assertEquals(city.revision() + 1, updated.revision());
        assertEquals(CityLawsCode.STALE_CITY, repository.updateCityLaws(new CityLawsRequest(cityId,
                rulerId, city.revision(), CityLaws.DEFAULT)).toCompletableFuture().join().code());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var audit = statement.executeQuery("SELECT COUNT(*) FROM city_law_audit")) {
            assertTrue(audit.next());
            assertEquals(1, audit.getInt(1));
        }
    }

    @Test void dailyTaxPolicyIsPermissionCheckedAuditedAndSchedulesFirstChargeAfterOneDay() throws Exception {
        Path database = directory.resolve("tax-policy.db");
        SqliteCityRepository repository = new SqliteCityRepository(database, 5_000, Runnable::run);
        repository.initialize();
        UUID cityId = UUID.randomUUID(), rulerId = UUID.randomUUID();
        found(repository, cityId, rulerId, UUID.randomUUID(), 2, "Taxhold");
        CityView city = repository.find(cityId).toCompletableFuture().join().orElseThrow();
        assertEquals(CityTaxPolicy.DEFAULT, city.taxPolicy());
        assertEquals(CityTaxCode.PERMISSION_DENIED, repository.updateCityTaxPolicy(new CityTaxRequest(cityId,
                UUID.randomUUID(), city.revision(), true, 50)).toCompletableFuture().join().code());

        Instant before = Instant.now();
        var enabled = repository.updateCityTaxPolicy(new CityTaxRequest(cityId, rulerId, city.revision(), true, 50))
                .toCompletableFuture().join();
        assertEquals(CityTaxCode.UPDATED, enabled.code());
        CityView updated = enabled.city().orElseThrow();
        assertTrue(updated.taxPolicy().enabled());
        assertEquals(50, updated.taxPolicy().dailyAmount());
        assertTrue(updated.taxPolicy().nextCollectionAt().isAfter(before.plusSeconds(86_399)));
        assertTrue(repository.dueCityTaxes(Instant.now()).toCompletableFuture().join().isEmpty());
        Instant overdue = Instant.now().minusSeconds(1);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var update = connection.prepareStatement("UPDATE city_tax_policy SET next_collection_at=? WHERE city_uuid=?")) {
            update.setString(1, overdue.toString()); update.setString(2, cityId.toString()); update.executeUpdate();
        }
        var due = repository.dueCityTaxes(Instant.now()).toCompletableFuture().join();
        assertEquals(1, due.size());
        assertEquals(cityId, due.getFirst().cityId());
        assertEquals(50, due.getFirst().dailyAmount());
        assertEquals(overdue, due.getFirst().cycle());
        assertEquals(1, due.getFirst().residents().size());
        assertEquals(CityTaxCode.STALE_CITY, repository.updateCityTaxPolicy(new CityTaxRequest(cityId, rulerId,
                city.revision(), false, 0)).toCompletableFuture().join().code());

        Instant next = Instant.now().plusSeconds(86_400);
        assertTrue(repository.completeCityTaxCycle(cityId, overdue, next)
                .toCompletableFuture().join());
        assertEquals(next, repository.find(cityId).toCompletableFuture().join().orElseThrow().taxPolicy().nextCollectionAt());
        UUID taxPayment = UUID.randomUUID();
        CityTreasuryDraft taxDraft = new CityTreasuryDraft(taxPayment, cityId, rulerId, 50, "CITY_TAX");
        assertEquals(CityTreasuryCode.OPERATION_IN_PROGRESS,
                repository.prepareTreasuryDeposit(taxDraft).toCompletableFuture().join().code());
        assertTrue(repository.reserveTreasuryDeposit(taxPayment).toCompletableFuture().join());
        assertEquals(CityTreasuryCode.DEPOSITED,
                repository.applyTreasuryDeposit(taxDraft).toCompletableFuture().join().code());
        assertEquals("CITY_TAX", repository.treasuryHistory(cityId, 10).toCompletableFuture().join().getFirst().reason());
        var pendingTax = repository.pendingTreasuryDeposits().toCompletableFuture().join().stream()
                .filter(payment -> payment.operationId().equals(taxPayment)).findFirst().orElseThrow();
        assertEquals("CITY_TAX", pendingTax.reason());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var audit = statement.executeQuery("SELECT COUNT(*) FROM city_tax_policy_audit")) {
            assertTrue(audit.next()); assertEquals(1, audit.getInt(1));
        }
    }

    private static void found(SqliteCityRepository repository, UUID cityId, UUID ownerId, UUID worldId,
                              int offset, String name) {
        repository.found(new CityFoundationDraft(UUID.randomUUID(), cityId, ownerId, name + "Owner", name,
                name.toLowerCase(), Set.of(new ChunkPosition(worldId, offset, 0), new ChunkPosition(worldId, offset, 1),
                new ChunkPosition(worldId, offset + 1, 0), new ChunkPosition(worldId, offset + 1, 1))))
                .toCompletableFuture().join();
    }
}
