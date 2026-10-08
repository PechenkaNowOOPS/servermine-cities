package ru.servermine.cities.api;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable read model; permission and purchase validation must use current domain state. */
public record CityView(UUID id, String name, CityStage stage, Set<ChunkPosition> chunks,
                       long treasury, long revision, List<ResidentView> residents, Set<CityUpgradeId> upgrades,
                       CityLaws laws, CityTaxPolicy taxPolicy) {
    public CityView(UUID id, String name, CityStage stage, Set<ChunkPosition> chunks,
                    long treasury, long revision, List<ResidentView> residents) {
        this(id, name, stage, chunks, treasury, revision, residents, Set.of(), CityLaws.DEFAULT, CityTaxPolicy.DEFAULT);
    }

    public CityView(UUID id, String name, CityStage stage, Set<ChunkPosition> chunks, long treasury, long revision,
                    List<ResidentView> residents, Set<CityUpgradeId> upgrades) {
        this(id, name, stage, chunks, treasury, revision, residents, upgrades, CityLaws.DEFAULT, CityTaxPolicy.DEFAULT);
    }

    public CityView(UUID id, String name, CityStage stage, Set<ChunkPosition> chunks, long treasury, long revision,
                    List<ResidentView> residents, Set<CityUpgradeId> upgrades, CityLaws laws) {
        this(id, name, stage, chunks, treasury, revision, residents, upgrades, laws, CityTaxPolicy.DEFAULT);
    }

    public CityView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(stage, "stage");
        chunks = Set.copyOf(chunks);
        residents = List.copyOf(residents);
        upgrades = Set.copyOf(upgrades);
        Objects.requireNonNull(laws, "laws");
        Objects.requireNonNull(taxPolicy, "taxPolicy");
        if (name.isBlank() || treasury < 0 || revision < 0) throw new IllegalArgumentException("Invalid city view");
    }
    public record ResidentView(UUID playerId, String name, String role) {
        public ResidentView {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(role, "role");
        }
    }
}
