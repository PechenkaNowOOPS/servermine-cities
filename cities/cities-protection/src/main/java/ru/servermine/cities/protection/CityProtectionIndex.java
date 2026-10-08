package ru.servermine.cities.protection;

import ru.servermine.cities.api.ChunkPosition;
import ru.servermine.cities.api.CityView;
import ru.servermine.cities.api.CityRole;
import ru.servermine.cities.api.CityPermission;
import ru.servermine.cities.api.CityUpgradeId;
import ru.servermine.cities.api.CityTreatyType;
import ru.servermine.cities.api.CityTreatyState;
import ru.servermine.cities.api.CityTreatyView;
import ru.servermine.cities.api.CityLaws;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Lock-free event reads backed by immutable snapshots; writes copy and publish a new snapshot. */
public final class CityProtectionIndex {
    private volatile Snapshot snapshot = Snapshot.empty();

    public synchronized void replaceAll(Collection<CityView> cities) {
        Snapshot previous = snapshot;
        Map<ChunkPosition, Claim> claims = new HashMap<>();
        Map<UUID, UUID> membershipCities = new HashMap<>();
        Map<UUID, Set<ChunkPosition>> cityChunks = new HashMap<>();
        Map<UUID, Set<UUID>> cityResidents = new HashMap<>();
        for (CityView city : cities) add(city, claims, membershipCities, cityChunks, cityResidents);
        snapshot = new Snapshot(Map.copyOf(claims), Map.copyOf(membershipCities), immutableSets(cityChunks),
                immutableSets(cityResidents), previous.treaties(), groupTreaties(previous.treaties()));
    }

    public synchronized void replaceCity(CityView city) {
        Snapshot previous = snapshot;
        Map<ChunkPosition, Claim> claims = new HashMap<>(previous.claims());
        Map<UUID, UUID> membershipCities = new HashMap<>(previous.membershipCities());
        Map<UUID, Set<ChunkPosition>> cityChunks = mutableSets(previous.cityChunks());
        Map<UUID, Set<UUID>> cityResidents = mutableSets(previous.cityResidents());
        for (ChunkPosition chunk : cityChunks.getOrDefault(city.id(), Set.of())) claims.remove(chunk);
        for (UUID resident : cityResidents.getOrDefault(city.id(), Set.of())) membershipCities.remove(resident, city.id());
        cityChunks.remove(city.id());
        cityResidents.remove(city.id());
        add(city, claims, membershipCities, cityChunks, cityResidents);
        snapshot = new Snapshot(Map.copyOf(claims), Map.copyOf(membershipCities), immutableSets(cityChunks),
                immutableSets(cityResidents), previous.treaties(), groupTreaties(previous.treaties()));
    }

    public synchronized void replaceTreaties(Collection<CityTreatyView> treaties) {
        Map<UUID, CityTreatyView> active = new HashMap<>();
        for (CityTreatyView treaty : treaties) {
            if (isCombatTreaty(treaty, Instant.now())) active.put(treaty.id(), treaty);
        }
        publishTreaties(active);
    }

    public synchronized void replaceTreaty(CityTreatyView treaty) {
        Map<UUID, CityTreatyView> active = new HashMap<>(snapshot.treaties());
        if (isCombatTreaty(treaty, Instant.now())) active.put(treaty.id(), treaty);
        else active.remove(treaty.id());
        publishTreaties(active);
    }

    private void publishTreaties(Map<UUID, CityTreatyView> treaties) {
        Snapshot previous = snapshot;
        snapshot = new Snapshot(previous.claims(), previous.membershipCities(), previous.cityChunks(),
                previous.cityResidents(), Map.copyOf(treaties), groupTreaties(treaties));
    }

    public boolean preventsTreatyCombat(UUID attackerId, UUID victimId, ChunkPosition victimChunk) {
        Snapshot current = snapshot;
        UUID attackerCity = current.membershipCities().get(attackerId);
        UUID victimCity = current.membershipCities().get(victimId);
        if (attackerCity == null || victimCity == null || attackerCity.equals(victimCity)) return false;
        Map<UUID, List<CityTreatyView>> byCity = current.treatyPairs().get(attackerCity);
        List<CityTreatyView> treaties = byCity == null ? null : byCity.get(victimCity);
        if (treaties == null) return false;
        Instant now = Instant.now();
        Claim location = current.claims().get(victimChunk);
        for (CityTreatyView treaty : treaties) {
            if (!treaty.expiresAt().isAfter(now)) continue;
            if (treaty.type() == CityTreatyType.ALLIANCE) return true;
            if (location != null && (location.cityId().equals(attackerCity) || location.cityId().equals(victimCity))) return true;
        }
        return false;
    }

    public boolean isClaimed(ChunkPosition chunk) { return snapshot.claims().containsKey(chunk); }

    public boolean allowsTransfer(ChunkPosition source, ChunkPosition destination) {
        Snapshot current = snapshot;
        Claim from = current.claims().get(source);
        Claim to = current.claims().get(destination);
        if (from == null || to == null) return from == to;
        return from.cityId().equals(to.cityId());
    }

    public int claimCount() { return snapshot.claims().size(); }

    public boolean canModify(UUID playerId, ChunkPosition chunk) {
        Claim claim = snapshot.claims().get(chunk);
        if (claim == null) return true;
        CityRole role = claim.residents().get(playerId);
        return role != null && role.allows(CityPermission.BUILD);
    }

    public boolean canInteract(UUID playerId, ChunkPosition chunk) {
        Claim claim = snapshot.claims().get(chunk);
        if (claim == null) return true;
        CityRole role = claim.residents().get(playerId);
        return role != null ? role.allows(CityPermission.BUILD) : claim.laws().outsiderInteractionsAllowed();
    }

    public boolean pvpAllowed(ChunkPosition chunk) {
        Claim claim = snapshot.claims().get(chunk);
        return claim == null || claim.laws().pvpAllowed();
    }

    public boolean fireAllowed(ChunkPosition chunk) {
        Claim claim = snapshot.claims().get(chunk);
        return claim == null || claim.laws().fireAllowed();
    }

    public boolean explosionsAllowed(ChunkPosition chunk) {
        Claim claim = snapshot.claims().get(chunk);
        return claim == null || claim.laws().explosionsAllowed();
    }

    public boolean wardsResidentFromPlayers(UUID playerId, ChunkPosition chunk) {
        Claim claim = snapshot.claims().get(chunk);
        return claim != null && claim.residents().containsKey(playerId)
                && claim.upgrades().contains(CityUpgradeId.CITY_WARDING);
    }

    public boolean guardsResidentFromMobs(UUID playerId, ChunkPosition chunk) {
        Claim claim = snapshot.claims().get(chunk);
        return claim != null && claim.residents().containsKey(playerId)
                && claim.upgrades().contains(CityUpgradeId.CITY_GUARDS);
    }

    private static boolean isCombatTreaty(CityTreatyView treaty, Instant now) {
        return treaty.state() == CityTreatyState.ACTIVE && treaty.expiresAt().isAfter(now)
                && (treaty.type() == CityTreatyType.ALLIANCE || treaty.type() == CityTreatyType.NON_AGGRESSION);
    }

    private static void add(CityView city, Map<ChunkPosition, Claim> claims, Map<UUID, UUID> membershipCities,
                            Map<UUID, Set<ChunkPosition>> cityChunks, Map<UUID, Set<UUID>> cityResidents) {
        Map<UUID, CityRole> residents = new HashMap<>();
        Set<UUID> residentIds = new HashSet<>();
        city.residents().forEach(resident -> {
            residents.put(resident.playerId(), CityRole.fromStorage(resident.role()));
            residentIds.add(resident.playerId());
            membershipCities.put(resident.playerId(), city.id());
        });
        Claim claim = new Claim(city.id(), Map.copyOf(residents), city.upgrades(), city.laws());
        city.chunks().forEach(chunk -> claims.put(chunk, claim));
        cityChunks.put(city.id(), Set.copyOf(city.chunks()));
        cityResidents.put(city.id(), Set.copyOf(residentIds));
    }

    private static <K, V> Map<K, Set<V>> immutableSets(Map<K, Set<V>> source) {
        Map<K, Set<V>> copy = new HashMap<>();
        source.forEach((key, values) -> copy.put(key, Set.copyOf(values)));
        return Map.copyOf(copy);
    }

    private static <K, V> Map<K, Set<V>> mutableSets(Map<K, Set<V>> source) {
        Map<K, Set<V>> copy = new HashMap<>();
        source.forEach((key, values) -> copy.put(key, new HashSet<>(values)));
        return copy;
    }

    private static Map<UUID, Map<UUID, List<CityTreatyView>>> groupTreaties(Map<UUID, CityTreatyView> treaties) {
        Map<UUID, Map<UUID, java.util.ArrayList<CityTreatyView>>> grouped = new HashMap<>();
        treaties.values().forEach(treaty -> {
            grouped.computeIfAbsent(treaty.proposingCityId(), ignored -> new HashMap<>())
                    .computeIfAbsent(treaty.targetCityId(), ignored -> new java.util.ArrayList<>()).add(treaty);
            grouped.computeIfAbsent(treaty.targetCityId(), ignored -> new HashMap<>())
                    .computeIfAbsent(treaty.proposingCityId(), ignored -> new java.util.ArrayList<>()).add(treaty);
        });
        Map<UUID, Map<UUID, List<CityTreatyView>>> result = new HashMap<>();
        grouped.forEach((city, pairs) -> {
            Map<UUID, List<CityTreatyView>> immutablePairs = new HashMap<>();
            pairs.forEach((otherCity, values) -> immutablePairs.put(otherCity, List.copyOf(values)));
            result.put(city, Map.copyOf(immutablePairs));
        });
        return Map.copyOf(result);
    }

    private record Claim(UUID cityId, Map<UUID, CityRole> residents, Set<CityUpgradeId> upgrades, CityLaws laws) { }

    private record Snapshot(Map<ChunkPosition, Claim> claims, Map<UUID, UUID> membershipCities,
                           Map<UUID, Set<ChunkPosition>> cityChunks, Map<UUID, Set<UUID>> cityResidents,
                           Map<UUID, CityTreatyView> treaties, Map<UUID, Map<UUID, List<CityTreatyView>>> treatyPairs) {
        private static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
