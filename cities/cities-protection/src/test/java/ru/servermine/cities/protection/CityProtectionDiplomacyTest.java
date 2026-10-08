package ru.servermine.cities.protection;

import org.junit.jupiter.api.Test;
import ru.servermine.cities.api.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CityProtectionDiplomacyTest {
    @Test void nonAggressionProtectsSignatoryClaimsAndAllianceProtectsMembersGlobally() {
        UUID world = UUID.randomUUID(), cityA = UUID.randomUUID(), cityB = UUID.randomUUID(), cityC = UUID.randomUUID();
        UUID playerA = UUID.randomUUID(), playerB = UUID.randomUUID();
        ChunkPosition claimA = new ChunkPosition(world, 1, 1), claimB = new ChunkPosition(world, 2, 2);
        CityProtectionIndex index = new CityProtectionIndex();
        index.replaceAll(List.of(city(cityA, "A", claimA, playerA), city(cityB, "B", claimB, playerB),
                city(cityC, "C", new ChunkPosition(world, 9, 9), UUID.randomUUID())));

        CityTreatyView nonAggression = treaty(cityA, cityB, CityTreatyType.NON_AGGRESSION,
                CityTreatyState.ACTIVE, Instant.now().plusSeconds(3600));
        index.replaceTreaties(List.of(nonAggression));
        assertTrue(index.preventsTreatyCombat(playerA, playerB, claimA));
        assertTrue(index.preventsTreatyCombat(playerA, playerB, claimB));
        assertFalse(index.preventsTreatyCombat(playerA, playerB, new ChunkPosition(world, 4, 4)));
        assertFalse(index.preventsTreatyCombat(playerA, UUID.randomUUID(), claimA));

        CityTreatyView alliance = treaty(cityA, cityB, CityTreatyType.ALLIANCE,
                CityTreatyState.ACTIVE, Instant.now().plusSeconds(3600));
        index.replaceTreaties(List.of(alliance));
        assertTrue(index.preventsTreatyCombat(playerA, playerB, new ChunkPosition(world, 4, 4)));

        index.replaceTreaty(new CityTreatyView(alliance.id(), cityA, "A", cityB, "B", CityTreatyType.ALLIANCE,
                CityTreatyState.TERMINATED, alliance.createdAt(), alliance.expiresAt()));
        assertFalse(index.preventsTreatyCombat(playerA, playerB, new ChunkPosition(world, 4, 4)));
    }

    private CityView city(UUID id, String name, ChunkPosition claim, UUID resident) {
        return new CityView(id, name, CityStage.CITY, Set.of(claim), 0, 1,
                List.of(new CityView.ResidentView(resident, name + "Player", "RULER")));
    }

    private CityTreatyView treaty(UUID a, UUID b, CityTreatyType type, CityTreatyState state, Instant expiry) {
        Instant now = Instant.now();
        return new CityTreatyView(UUID.randomUUID(), a, "A", b, "B", type, state, now, expiry);
    }
}
