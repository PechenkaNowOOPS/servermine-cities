package ru.servermine.cities.protection;

import org.junit.jupiter.api.Test;
import ru.servermine.cities.api.ChunkPosition;
import ru.servermine.cities.api.CityLaws;
import ru.servermine.cities.api.CityStage;
import ru.servermine.cities.api.CityView;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CityProtectionLawsTest {
    @Test void lawsApplyToClaimedChunksAndRefreshWhenCityChanges() {
        UUID cityId = UUID.randomUUID(), worldId = UUID.randomUUID(), resident = UUID.randomUUID(), outsider = UUID.randomUUID();
        ChunkPosition claim = new ChunkPosition(worldId, -3, 8);
        CityProtectionIndex index = new CityProtectionIndex();
        CityView city = new CityView(cityId, "Lawhold", CityStage.CITY, Set.of(claim), 10, 1,
                List.of(new CityView.ResidentView(resident, "Resident", "MEMBER")), Set.of(),
                new CityLaws(false, true, true, false));
        index.replaceAll(List.of(city));

        assertFalse(index.pvpAllowed(claim));
        assertTrue(index.canInteract(outsider, claim));
        assertFalse(index.canModify(outsider, claim));
        assertTrue(index.fireAllowed(claim));
        assertFalse(index.explosionsAllowed(claim));
        assertTrue(index.pvpAllowed(new ChunkPosition(worldId, -2, 8)));

        index.replaceCity(new CityView(cityId, city.name(), city.stage(), city.chunks(), city.treasury(), 2,
                city.residents(), city.upgrades(), CityLaws.DEFAULT));
        assertTrue(index.pvpAllowed(claim));
        assertFalse(index.canInteract(outsider, claim));
        assertFalse(index.fireAllowed(claim));
    }
}
