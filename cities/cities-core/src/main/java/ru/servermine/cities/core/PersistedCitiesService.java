package ru.servermine.cities.core;

import ru.servermine.cities.api.CitiesService;
import ru.servermine.cities.api.CityView;
import ru.servermine.cities.api.CityFoundingService;
import ru.servermine.cities.api.CityProgressionService;
import ru.servermine.cities.api.CityTerritoryService;
import ru.servermine.cities.api.CityMembershipService;
import ru.servermine.cities.api.CityTreasuryService;
import ru.servermine.cities.api.CityRoleService;
import ru.servermine.cities.api.CityUpgradeService;
import ru.servermine.cities.api.CityDiplomacyService;
import ru.servermine.cities.api.CityMarketService;
import ru.servermine.cities.api.CityLawsService;
import ru.servermine.cities.api.CityTaxService;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

/** Read-only service adapter. Readiness is enabled only after the repository finishes startup recovery. */
public final class PersistedCitiesService implements CitiesService {
    private final CityRepository repository;
    private final CityFoundingService foundingService;
    private final CityProgressionService progressionService;
    private final CityTerritoryService territoryService;
    private final CityMembershipService membershipService;
    private final CityTreasuryService treasuryService;
    private final CityRoleService roleService;
    private final CityUpgradeService upgradeService;
    private final CityDiplomacyService diplomacyService;
    private final CityMarketService marketService;
    private final CityLawsService lawsService;
    private final CityTaxService taxService;
    private volatile boolean ready = true;

    public PersistedCitiesService(CityRepository repository) {
        this(repository, null, null, null);
    }

    public PersistedCitiesService(CityRepository repository, CityFoundingService foundingService) {
        this(repository, foundingService, null, null);
    }

    public PersistedCitiesService(CityRepository repository, CityFoundingService foundingService,
                                  CityProgressionService progressionService) {
        this(repository, foundingService, progressionService, null);
    }

    public PersistedCitiesService(CityRepository repository, CityFoundingService foundingService,
                                  CityProgressionService progressionService, CityTerritoryService territoryService) {
        this(repository, foundingService, progressionService, territoryService, null);
    }

    public PersistedCitiesService(CityRepository repository, CityFoundingService foundingService,
                                  CityProgressionService progressionService, CityTerritoryService territoryService,
                                  CityMembershipService membershipService) {
        this(repository, foundingService, progressionService, territoryService, membershipService, null);
    }

    public PersistedCitiesService(CityRepository repository, CityFoundingService foundingService,
                                  CityProgressionService progressionService, CityTerritoryService territoryService,
                                  CityMembershipService membershipService, CityTreasuryService treasuryService) {
        this(repository, foundingService, progressionService, territoryService, membershipService, treasuryService, null);
    }

    public PersistedCitiesService(CityRepository repository, CityFoundingService foundingService,
                                  CityProgressionService progressionService, CityTerritoryService territoryService,
                                  CityMembershipService membershipService, CityTreasuryService treasuryService,
                                  CityRoleService roleService) {
        this(repository, foundingService, progressionService, territoryService, membershipService, treasuryService,
                roleService, null);
    }

    public PersistedCitiesService(CityRepository repository, CityFoundingService foundingService,
                                  CityProgressionService progressionService, CityTerritoryService territoryService,
                                  CityMembershipService membershipService, CityTreasuryService treasuryService,
                                  CityRoleService roleService, CityUpgradeService upgradeService) {
        this(repository, foundingService, progressionService, territoryService, membershipService, treasuryService,
                roleService, upgradeService, null);
    }

    public PersistedCitiesService(CityRepository repository, CityFoundingService foundingService,
                                  CityProgressionService progressionService, CityTerritoryService territoryService,
                                  CityMembershipService membershipService, CityTreasuryService treasuryService,
                                  CityRoleService roleService, CityUpgradeService upgradeService,
                                  CityDiplomacyService diplomacyService) {
        this(repository, foundingService, progressionService, territoryService, membershipService, treasuryService,
                roleService, upgradeService, diplomacyService, null);
    }

    public PersistedCitiesService(CityRepository repository, CityFoundingService foundingService,
                                  CityProgressionService progressionService, CityTerritoryService territoryService,
                                  CityMembershipService membershipService, CityTreasuryService treasuryService,
                                  CityRoleService roleService, CityUpgradeService upgradeService,
                                  CityDiplomacyService diplomacyService, CityMarketService marketService) {
        this(repository, foundingService, progressionService, territoryService, membershipService, treasuryService,
                roleService, upgradeService, diplomacyService, marketService, null);
    }

    public PersistedCitiesService(CityRepository repository, CityFoundingService foundingService,
                                  CityProgressionService progressionService, CityTerritoryService territoryService,
                                  CityMembershipService membershipService, CityTreasuryService treasuryService,
                                  CityRoleService roleService, CityUpgradeService upgradeService,
                                  CityDiplomacyService diplomacyService, CityMarketService marketService,
                                  CityLawsService lawsService) {
        this(repository, foundingService, progressionService, territoryService, membershipService, treasuryService,
                roleService, upgradeService, diplomacyService, marketService, lawsService, null);
    }

    public PersistedCitiesService(CityRepository repository, CityFoundingService foundingService,
                                  CityProgressionService progressionService, CityTerritoryService territoryService,
                                  CityMembershipService membershipService, CityTreasuryService treasuryService,
                                  CityRoleService roleService, CityUpgradeService upgradeService,
                                  CityDiplomacyService diplomacyService, CityMarketService marketService,
                                  CityLawsService lawsService, CityTaxService taxService) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.foundingService = foundingService;
        this.progressionService = progressionService;
        this.territoryService = territoryService;
        this.membershipService = membershipService;
        this.treasuryService = treasuryService;
        this.roleService = roleService;
        this.upgradeService = upgradeService;
        this.diplomacyService = diplomacyService;
        this.marketService = marketService;
        this.lawsService = lawsService;
        this.taxService = taxService;
    }

    public String apiVersion() {
        return "1";
    }

    public boolean isReady() {
        return ready;
    }

    @Override
    public Optional<CityFoundingService> foundingService() {
        return ready ? Optional.ofNullable(foundingService) : Optional.empty();
    }

    @Override
    public Optional<CityProgressionService> progressionService() {
        return ready ? Optional.ofNullable(progressionService) : Optional.empty();
    }

    @Override
    public Optional<CityTerritoryService> territoryService() {
        return ready ? Optional.ofNullable(territoryService) : Optional.empty();
    }

    @Override
    public Optional<CityMembershipService> membershipService() {
        return ready ? Optional.ofNullable(membershipService) : Optional.empty();
    }

    @Override
    public Optional<CityTreasuryService> treasuryService() {
        return ready ? Optional.ofNullable(treasuryService) : Optional.empty();
    }

    @Override
    public Optional<CityRoleService> roleService() {
        return ready ? Optional.ofNullable(roleService) : Optional.empty();
    }

    @Override
    public Optional<CityUpgradeService> upgradeService() {
        return ready ? Optional.ofNullable(upgradeService) : Optional.empty();
    }

    @Override
    public Optional<CityDiplomacyService> diplomacyService() {
        return ready ? Optional.ofNullable(diplomacyService) : Optional.empty();
    }

    @Override public Optional<CityMarketService> marketService() {
        return ready ? Optional.ofNullable(marketService) : Optional.empty();
    }

    @Override public Optional<CityLawsService> lawsService() {
        return ready ? Optional.ofNullable(lawsService) : Optional.empty();
    }

    @Override public Optional<CityTaxService> taxService() {
        return ready ? Optional.ofNullable(taxService) : Optional.empty();
    }

    public void deactivate() {
        ready = false;
    }

    public CompletionStage<Optional<CityView>> city(UUID cityId) {
        if (!ready) return CompletableFuture.failedFuture(new IllegalStateException("Cities service is unavailable"));
        return repository.find(Objects.requireNonNull(cityId, "cityId"));
    }

    public CompletionStage<Optional<CityView>> cityForPlayer(UUID playerId) {
        if (!ready) return CompletableFuture.failedFuture(new IllegalStateException("Cities service is unavailable"));
        return repository.findForPlayer(Objects.requireNonNull(playerId, "playerId"));
    }
}
