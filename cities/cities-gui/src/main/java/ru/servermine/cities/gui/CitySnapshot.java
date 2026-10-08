package ru.servermine.cities.gui;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import ru.servermine.cities.api.CityStage;
import ru.servermine.cities.api.ChunkPosition;
import ru.servermine.cities.api.CityUpgradeId;
import ru.servermine.cities.api.CityLaws;
import ru.servermine.cities.api.CityTaxPolicy;

public record CitySnapshot(
        String name,
        CityStage stage,
        int territory,
        long treasury,
        long revision,
        List<ResidentEntry> residents,
        Set<ChunkPosition> chunks,
        Set<CityUpgradeId> upgrades,
        CityLaws laws,
        CityTaxPolicy taxPolicy
) {
    public CitySnapshot(String name, CityStage stage, int territory, long treasury, long revision,
                        List<ResidentEntry> residents, Set<ChunkPosition> chunks) {
        this(name, stage, territory, treasury, revision, residents, chunks, Set.of(), CityLaws.DEFAULT, CityTaxPolicy.DEFAULT);
    }

    public CitySnapshot(String name, CityStage stage, int territory, long treasury, long revision,
                        List<ResidentEntry> residents, Set<ChunkPosition> chunks, Set<CityUpgradeId> upgrades) {
        this(name, stage, territory, treasury, revision, residents, chunks, upgrades, CityLaws.DEFAULT, CityTaxPolicy.DEFAULT);
    }

    public CitySnapshot(String name, CityStage stage, int territory, long treasury, long revision,
                        List<ResidentEntry> residents, Set<ChunkPosition> chunks, Set<CityUpgradeId> upgrades,
                        CityLaws laws) {
        this(name, stage, territory, treasury, revision, residents, chunks, upgrades, laws, CityTaxPolicy.DEFAULT);
    }

    public CitySnapshot {
        residents = List.copyOf(residents);
        chunks = Set.copyOf(chunks);
        upgrades = Set.copyOf(upgrades);
        if (laws == null) laws = CityLaws.DEFAULT;
        if (taxPolicy == null) taxPolicy = CityTaxPolicy.DEFAULT;
    }
    public int territoryLimit() {
        return stage.chunkLimit();
    }

    public boolean territoryFull() {
        return territory >= territoryLimit();
    }

    public record ResidentEntry(UUID playerId, String name, String role) {}
}
