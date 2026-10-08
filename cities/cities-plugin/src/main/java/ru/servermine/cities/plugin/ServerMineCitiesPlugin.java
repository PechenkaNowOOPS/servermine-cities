package ru.servermine.cities.plugin;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.servermine.cities.api.CitiesService;
import ru.servermine.cities.api.CityFoundingService;
import ru.servermine.cities.api.CityStage;
import ru.servermine.cities.api.CityUpgradeId;
import ru.servermine.cities.diplomacy.CityDiplomacyServiceImpl;
import ru.servermine.cities.market.CityMarketServiceImpl;
import ru.servermine.cities.core.PersistedCitiesService;
import ru.servermine.cities.storage.SqliteCityRepository;
import ru.servermine.cities.creation.CityFoundingServiceImpl;
import ru.servermine.cities.protection.BukkitFoundationPolicy;
import ru.servermine.cities.protection.CityProtectionIndex;
import ru.servermine.cities.protection.CityProtectionListener;
import ru.servermine.cities.protection.CityLawsServiceImpl;
import ru.servermine.cities.progression.CityProgressionServiceImpl;
import ru.servermine.cities.progression.StagePromotionPolicy;
import ru.servermine.cities.territory.CityTerritoryServiceImpl;
import ru.servermine.cities.members.CityMembershipServiceImpl;
import ru.servermine.cities.roles.CityRoleServiceImpl;
import ru.servermine.cities.treasury.CityTreasuryServiceImpl;
import ru.servermine.cities.treasury.CityTaxServiceImpl;
import ru.servermine.cities.upgrades.CityUpgradePolicy;
import ru.servermine.cities.upgrades.CityUpgradeServiceImpl;
import ru.servermine.cities.admin.CitiesAdminCommand;
import ru.servermine.cities.gui.*;
import ru.servermine.economy.api.EconomyService;
import java.util.Objects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ServerMineCitiesPlugin extends JavaPlugin {
    private ExecutorService databaseExecutor;
    private PersistedCitiesService citiesService;
    private CityFoundingServiceImpl cityFoundingService;
    private CityProgressionServiceImpl cityProgressionService;
    private CityTerritoryServiceImpl cityTerritoryService;
    private CityMembershipServiceImpl cityMembershipService;
    private CityRoleServiceImpl cityRoleService;
    private CityUpgradeServiceImpl cityUpgradeService;
    private CityDiplomacyServiceImpl cityDiplomacyService;
    private CityMarketServiceImpl cityMarketService;
    private CityLawsServiceImpl cityLawsService;
    private CityTreasuryServiceImpl cityTreasuryService;
    private CityTaxServiceImpl cityTaxService;
    private org.bukkit.scheduler.BukkitTask taxCollectionTask;
    private TerritoryOverviewManager territoryOverview;

    @Override public void onEnable() {
        try {
            saveDefaultConfig();
            mergeConfigDefaults();
            Files.createDirectories(getDataFolder().toPath());
            databaseExecutor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "ServerMineCities-DB");
                thread.setDaemon(true);
                return thread;
            });

            Path database = getDataFolder().toPath().resolve(getConfig().getString("storage.sqlite-file", "cities.db"));
            int busyTimeout = getConfig().getInt("storage.busy-timeout-ms", 5000);
            SqliteCityRepository repository = new SqliteCityRepository(database, busyTimeout, databaseExecutor);
            repository.initialize();

            CityProtectionIndex protectionIndex = new CityProtectionIndex();
            var storedCities = repository.findAllCities().toCompletableFuture().join();
            protectionIndex.replaceAll(storedCities);
            java.util.List<ru.servermine.cities.api.CityTreatyView> storedTreaties = new java.util.ArrayList<>();
            for (var city : storedCities) storedTreaties.addAll(repository.diplomacyTreaties(city.id()).toCompletableFuture().join());
            protectionIndex.replaceTreaties(storedTreaties);
            BukkitFoundationPolicy protection = new BukkitFoundationPolicy(this, loadSystemZones());
            cityFoundingService = new CityFoundingServiceImpl(repository, protection, protectionIndex::replaceCity);
            EconomyService economy = getServer().getServicesManager().load(EconomyService.class);
            cityProgressionService = new CityProgressionServiceImpl(repository, loadPromotionPolicies());
            cityTerritoryService = new CityTerritoryServiceImpl(repository,
                    getConfig().getLong("territory.chunk-price", 750),
                    target -> protection.checkTarget(target).thenApply(decision -> decision == ru.servermine.cities.protection.FoundationDecision.ALLOWED),
                    protection::checkTargets, protectionIndex::replaceCity);
            cityMembershipService = new CityMembershipServiceImpl(repository, protectionIndex::replaceCity);
            cityRoleService = new CityRoleServiceImpl(repository, protectionIndex::replaceCity);
            cityTreasuryService = new CityTreasuryServiceImpl(repository, economy, protectionIndex::replaceCity,
                    getConfig().getLong("diplomacy.maximum-transfer-amount", 100_000));
            cityUpgradeService = new CityUpgradeServiceImpl(repository, loadUpgradePolicies(), protectionIndex::replaceCity);
            cityDiplomacyService = new CityDiplomacyServiceImpl(repository, protectionIndex::replaceTreaty);
            cityMarketService = new CityMarketServiceImpl(repository, protectionIndex::replaceCity,
                    getConfig().getLong("market.maximum-listing-price", 1_000_000),
                    getConfig().getInt("market.fee-basis-points", 500));
            cityLawsService = new CityLawsServiceImpl(repository, protectionIndex::replaceCity);
            cityTaxService = new CityTaxServiceImpl(repository, cityTreasuryService, protectionIndex::replaceCity);
            citiesService = new PersistedCitiesService(repository, cityFoundingService, cityProgressionService,
                    cityTerritoryService, cityMembershipService, cityTreasuryService, cityRoleService, cityUpgradeService,
                    cityDiplomacyService, cityMarketService, cityLawsService, cityTaxService);
            getServer().getServicesManager().register(CitiesService.class, citiesService, this, ServicePriority.Normal);
            CityBookService books = new CityBookService(this);
            MenuManager menus = new MenuManager(this, citiesService, repository);
            territoryOverview = menus.territoryOverview();
            getServer().getPluginManager().registerEvents(new GuiListener(this, books, menus), this);
            getServer().getPluginManager().registerEvents(territoryOverview, this);
            getServer().getPluginManager().registerEvents(new CityProtectionListener(this, protectionIndex), this);
            getLogger().info("City protection cache loaded: " + protectionIndex.claimCount() + " claimed chunks.");
            CitiesAdminCommand citiesCommand = new CitiesAdminCommand(this, citiesService, books, menus, repository);
            PluginCommand command = Objects.requireNonNull(getCommand("smcities"), "smcities command is missing");
            command.setExecutor(citiesCommand);
            command.setTabCompleter(citiesCommand);

            getLogger().info("Cities storage is ready. Economy ready=" + (economy != null && economy.isReady()));
            getLogger().info("City founding, progression, territory, roles, treasury, upgrades, diplomacy, market, laws, and taxes are ready.");
            cityTreasuryService.recoverPending().whenComplete((ignored, error) -> {
                if (error != null) getLogger().severe("City treasury recovery failed: " + error.getMessage());
                else {
                    getLogger().info("City treasury recovery completed.");
                    taxCollectionTask = getServer().getScheduler().runTaskTimer(this, this::collectCityTaxes, 1200L, 1200L);
                }
            });
        } catch (Exception error) {
            getLogger().severe("ServerMineCities could not initialize its database: " + error.getMessage());
            getLogger().log(java.util.logging.Level.SEVERE, "Cities startup failed", error);
            closeDatabaseExecutor();
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable() {
        if (taxCollectionTask != null) taxCollectionTask.cancel();
        if (cityTaxService != null) cityTaxService.deactivate();
        if (territoryOverview != null) territoryOverview.shutdown();
        if (cityProgressionService != null) cityProgressionService.deactivate();
        if (cityLawsService != null) cityLawsService.deactivate();
        if (cityTerritoryService != null) cityTerritoryService.deactivate();
        if (cityMembershipService != null) cityMembershipService.deactivate();
        if (cityRoleService != null) cityRoleService.deactivate();
        if (cityUpgradeService != null) cityUpgradeService.deactivate();
        if (cityDiplomacyService != null) cityDiplomacyService.deactivate();
        if (cityMarketService != null) cityMarketService.deactivate();
        if (cityTreasuryService != null) cityTreasuryService.deactivate();
        if (cityFoundingService != null) cityFoundingService.deactivate();
        if (citiesService != null) citiesService.deactivate();
        getServer().getServicesManager().unregisterAll(this);
        getServer().getOnlinePlayers().stream()
            .filter(p -> p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder)
            .forEach(org.bukkit.entity.Player::closeInventory);
        closeDatabaseExecutor();
    }

    private void collectCityTaxes() {
        if (cityTaxService == null || !cityTaxService.isReady()) return;
        java.util.Set<java.util.UUID> online = getServer().getOnlinePlayers().stream()
                .map(org.bukkit.entity.Player::getUniqueId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        cityTaxService.collectDue(online).whenComplete((collections, error) -> {
            if (!isEnabled()) return;
            if (error != null) {
                getLogger().warning("City tax collection did not finish: " + error.getMessage());
                return;
            }
            getServer().getScheduler().runTask(this, () -> {
                if (collections == null) return;
                for (var collection : collections) {
                    org.bukkit.entity.Player player = getServer().getPlayer(collection.playerId());
                    if (player == null) continue;
                    if (collection.result() == ru.servermine.cities.api.CityTreasuryCode.DEPOSITED) {
                        player.sendMessage(net.kyori.adventure.text.Component.text(
                                "Город «" + collection.cityName() + "» получил ежедневный налог: "
                                        + collection.amount() + " монет.", net.kyori.adventure.text.format.NamedTextColor.GOLD));
                    } else if (collection.result() == ru.servermine.cities.api.CityTreasuryCode.INSUFFICIENT_FUNDS) {
                        player.sendMessage(net.kyori.adventure.text.Component.text(
                                "Не удалось удержать городской налог: в инвентаре не хватает монет. Повторного списания за этот день не будет.",
                                net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                    } else if (collection.result() == ru.servermine.cities.api.CityTreasuryCode.UNKNOWN_OUTCOME) {
                        player.sendMessage(net.kyori.adventure.text.Component.text(
                                "Результат списания налога проверяется. Не повторяйте платёж вручную.",
                                net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                    }
                }
            });
        });
    }

    private java.util.Map<CityStage, StagePromotionPolicy> loadPromotionPolicies() {
        java.util.Map<CityStage, StagePromotionPolicy> policies = new java.util.EnumMap<>(CityStage.class);
        policies.put(CityStage.SETTLEMENT, promotionPolicy("settlement-to-village", true, 0, 1, 4));
        policies.put(CityStage.VILLAGE, promotionPolicy("village-to-city", true, 10_000, 4, 8));
        policies.put(CityStage.CITY, promotionPolicy("city-to-capital", true, 50_000, 8, 16));
        policies.put(CityStage.CAPITAL, promotionPolicy("capital-to-kingdom", true, 200_000, 16, 32));
        return java.util.Map.copyOf(policies);
    }

    private void mergeConfigDefaults() throws java.io.IOException {
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(getResource("config.yml"), "config.yml is missing from the plugin jar"),
                StandardCharsets.UTF_8)) {
            getConfig().setDefaults(YamlConfiguration.loadConfiguration(reader));
            getConfig().options().copyDefaults(true);
            saveConfig();
        }
    }

    private StagePromotionPolicy promotionPolicy(String key, boolean enabled, long price,
                                                  int minimumResidents, int minimumChunks) {
        String path = "progression." + key + ".";
        return new StagePromotionPolicy(getConfig().getBoolean(path + "enabled", enabled),
                getConfig().getLong(path + "price", price),
                getConfig().getInt(path + "minimum-residents", minimumResidents),
                getConfig().getInt(path + "minimum-chunks", minimumChunks));
    }

    private java.util.Map<CityUpgradeId, CityUpgradePolicy> loadUpgradePolicies() {
        java.util.Map<CityUpgradeId, CityUpgradePolicy> policies = new java.util.EnumMap<>(CityUpgradeId.class);
        policies.put(CityUpgradeId.CITY_WARDING, upgradePolicy("city-warding", true, 25_000,
                CityStage.CITY, java.util.Set.of()));
        policies.put(CityUpgradeId.CITY_GUARDS, upgradePolicy("city-guards", true, 100_000,
                CityStage.CAPITAL, java.util.Set.of(CityUpgradeId.CITY_WARDING)));
        return java.util.Map.copyOf(policies);
    }

    private CityUpgradePolicy upgradePolicy(String key, boolean enabled, long price, CityStage minimumStage,
                                             java.util.Set<CityUpgradeId> prerequisites) {
        String path = "upgrades." + key + ".";
        java.util.Set<CityUpgradeId> configuredPrerequisites = new java.util.HashSet<>();
        for (String value : getConfig().getStringList(path + "prerequisites")) {
            configuredPrerequisites.add(CityUpgradeId.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
        }
        if (getConfig().contains(path + "prerequisites")) prerequisites = configuredPrerequisites;
        return new CityUpgradePolicy(getConfig().getBoolean(path + "enabled", enabled),
                getConfig().getLong(path + "price", price),
                CityStage.valueOf(getConfig().getString(path + "minimum-stage", minimumStage.name())
                        .toUpperCase(java.util.Locale.ROOT)), prerequisites);
    }

    private java.util.List<BukkitFoundationPolicy.SystemZone> loadSystemZones() {
        java.util.List<BukkitFoundationPolicy.SystemZone> zones = new java.util.ArrayList<>();
        for (java.util.Map<?, ?> row : getConfig().getMapList("protection.system-zones")) {
            Object world = row.get("world");
            if (world == null) throw new IllegalArgumentException("protection.system-zones entry is missing world UUID");
            try {
                zones.add(new BukkitFoundationPolicy.SystemZone(java.util.UUID.fromString(world.toString()),
                        integer(row, "min-x"), integer(row, "max-x"), integer(row, "min-z"), integer(row, "max-z")));
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("Invalid protection.system-zones entry: " + row, invalid);
            }
        }
        return java.util.List.copyOf(zones);
    }

    private int integer(java.util.Map<?, ?> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Missing integer " + key);
        return number.intValue();
    }

    private void closeDatabaseExecutor() {
        if (databaseExecutor == null) return;
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) databaseExecutor.shutdownNow();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            databaseExecutor.shutdownNow();
        }
    }
}
