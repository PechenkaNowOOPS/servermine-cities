package ru.servermine.cities.protection;

import org.junit.jupiter.api.Test;
import ru.servermine.cities.api.ChunkPosition;
import ru.servermine.cities.api.CityStage;
import ru.servermine.cities.api.CityUpgradeId;
import ru.servermine.cities.api.CityView;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CityProtectionUpgradesTest {
    @Test void combatUpgradesOnlyProtectMembersInClaimedCityChunks() {
        UUID worldId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();
        UUID residentId = UUID.randomUUID();
        UUID visitorId = UUID.randomUUID();
        ChunkPosition claimed = new ChunkPosition(worldId, 4, -2);
        CityProtectionIndex index = new CityProtectionIndex();
        CityView city = new CityView(cityId, "Example", CityStage.CITY, Set.of(claimed), 1000, 7,
                List.of(new CityView.ResidentView(residentId, "Resident", "MEMBER")),
                Set.of(CityUpgradeId.CITY_WARDING, CityUpgradeId.CITY_GUARDS));

        index.replaceAll(List.of(city));

        assertTrue(index.wardsResidentFromPlayers(residentId, claimed));
        assertTrue(index.guardsResidentFromMobs(residentId, claimed));
        assertFalse(index.wardsResidentFromPlayers(visitorId, claimed));
        assertFalse(index.guardsResidentFromMobs(visitorId, claimed));
        assertFalse(index.wardsResidentFromPlayers(residentId, new ChunkPosition(worldId, 5, -2)));
    }

    @Test void replacingCityRefreshesUpgradeEffects() {
        UUID worldId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();
        UUID residentId = UUID.randomUUID();
        ChunkPosition claimed = new ChunkPosition(worldId, 1, 1);
        CityProtectionIndex index = new CityProtectionIndex();
        CityView withoutWard = new CityView(cityId, "Example", CityStage.CITY, Set.of(claimed), 1000, 7,
                List.of(new CityView.ResidentView(residentId, "Resident", "MEMBER")));
        index.replaceAll(List.of(withoutWard));
        assertFalse(index.wardsResidentFromPlayers(residentId, claimed));

        CityView withWard = new CityView(cityId, "Example", CityStage.CITY, Set.of(claimed), 500, 8,
                withoutWard.residents(), Set.of(CityUpgradeId.CITY_WARDING));
        index.replaceCity(withWard);
        assertTrue(index.wardsResidentFromPlayers(residentId, claimed));
    }
}
