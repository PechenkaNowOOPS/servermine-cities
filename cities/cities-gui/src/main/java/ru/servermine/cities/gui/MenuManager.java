package ru.servermine.cities.gui;

import org.bukkit.plugin.java.JavaPlugin;
import ru.servermine.cities.api.CitiesService;
import ru.servermine.cities.api.CityFoundationCode;
import ru.servermine.cities.api.CityFoundationRequest;
import ru.servermine.cities.api.CityFoundingService;
import ru.servermine.cities.api.CityProgressionService;
import ru.servermine.cities.api.CityTerritoryService;
import ru.servermine.cities.api.CityClaimCode;
import ru.servermine.cities.api.CityClaimRequest;
import ru.servermine.cities.api.CityPromotionCode;
import ru.servermine.cities.api.CityPromotionRequest;
import ru.servermine.cities.api.CityPromotionResult;
import ru.servermine.cities.api.ChunkPosition;
import ru.servermine.cities.api.CityStage;
import ru.servermine.cities.api.CityUpgradeCode;
import ru.servermine.cities.api.CityUpgradeId;
import ru.servermine.cities.api.CityUpgradeRequest;
import ru.servermine.cities.api.CityUpgradeService;
import ru.servermine.cities.api.CityUpgradeView;
import ru.servermine.cities.api.CityView;
import ru.servermine.cities.api.CityTreasuryEntry;
import ru.servermine.cities.api.CityTreasuryRecoveryView;
import ru.servermine.cities.api.CityRole;
import ru.servermine.cities.api.CityRoleCode;
import ru.servermine.cities.api.CityDiplomacyCode;
import ru.servermine.cities.api.CityDiplomacyRequest;
import ru.servermine.cities.api.CityDiplomacySnapshot;
import ru.servermine.cities.api.CityTreatyState;
import ru.servermine.cities.api.CityTreatyType;
import ru.servermine.cities.api.CityTreatyView;
import ru.servermine.cities.api.CityTreasuryTransferRequest;
import ru.servermine.cities.api.CityTreasuryTransferCode;
import ru.servermine.cities.api.CityPermission;
import ru.servermine.cities.api.CityMarketListing;
import ru.servermine.cities.api.CityMarketCode;
import ru.servermine.cities.api.CityMarketCreateRequest;
import ru.servermine.cities.api.CityMarketPurchaseRequest;
import ru.servermine.cities.api.CityMarketDelivery;
import ru.servermine.cities.api.CityLaws;
import ru.servermine.cities.api.CityLawsCode;
import ru.servermine.cities.api.CityLawsRequest;
import ru.servermine.cities.api.CityLawsService;
import ru.servermine.cities.api.CityTaxPolicy;
import ru.servermine.cities.api.CityTaxCode;
import ru.servermine.cities.api.CityTaxRequest;
import ru.servermine.cities.api.CityTaxService;
import ru.servermine.cities.core.CityRepository;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.time.Instant;
import java.util.UUID;

public final class MenuManager {
    private static final Key GUI_FONT = Key.key("gradostroy", "gui");
    private static final Key DEFAULT_FONT = Key.key("minecraft", "default");
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU"));

    private final JavaPlugin plugin;
    private final CitiesService cities;
    private final CityRepository repository;
    private final java.util.Map<UUID, UUID> adminTargets = new java.util.HashMap<>();
    private final java.util.Map<UUID, List<CityTreasuryRecoveryView>> adminRecoveryRows = new java.util.HashMap<>();
    private final java.util.Map<UUID, UUID> adminRecoverySelection = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer> adminRecoveryPages = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, java.util.UUID> requests = new java.util.HashMap<>();
    private final java.util.concurrent.ConcurrentMap<java.util.UUID, PendingFounding> pendingFounding = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<java.util.UUID, PendingPromotion> pendingPromotions = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<java.util.UUID, PendingUpgrade> pendingUpgrades = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<java.util.UUID, PendingClaim> pendingClaims = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, PendingMemberKick> pendingMemberKicks = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, PendingRoleTarget> pendingRoleTargets = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, java.util.UUID> cityIds = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, CitySnapshot> cityViews = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, List<CityTreasuryEntry>> treasuryHistory = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, CityDiplomacySnapshot> diplomacySnapshots = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, List<ru.servermine.cities.api.CityDiplomacySnapshot.CitySummary>> diplomacyTargets = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, UUID> diplomacySelectedTarget = new java.util.HashMap<>();
    private final java.util.Map<UUID, List<CityDiplomacySnapshot.CitySummary>> treasuryTransferTargets = new java.util.HashMap<>();
    private final java.util.concurrent.ConcurrentMap<UUID, PendingTreasuryTransfer> pendingTreasuryTransfers = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<UUID, UUID> awaitingTransferAmount = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<UUID, List<CityMarketListing>> marketListings = new java.util.HashMap<>();
    private final java.util.Map<UUID, List<CityMarketListing>> myMarketListings = new java.util.HashMap<>();
    private final java.util.Map<UUID, List<CityMarketDelivery>> marketDeliveries = new java.util.HashMap<>();
    private final java.util.Map<UUID, CityMarketListing> selectedMarketListing = new java.util.HashMap<>();
    private final java.util.concurrent.ConcurrentMap<UUID, PendingMarketListing> pendingMarketListings = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<UUID> awaitingMarketPrice = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<java.util.UUID, CityTreatyType> diplomacySelectedType = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> diplomacyDuration = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, List<ChunkPosition>> purchaseTargets = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> purchasePages = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, TerritoryOrigin> territoryOrigins = new java.util.HashMap<>();
    private final TerritoryOverviewManager territoryOverview;

    public MenuManager(JavaPlugin plugin, CitiesService cities, CityRepository repository) {
        this.plugin = plugin;
        this.cities = cities;
        this.repository = repository;
        this.territoryOverview = new TerritoryOverviewManager(plugin, this::selectOverviewChunk,
                player -> open(player, MenuType.TERRITORY));
    }

    public TerritoryOverviewManager territoryOverview() { return territoryOverview; }

    private void selectOverviewChunk(Player player, TerritoryOverviewManager.Selection selection) {
        territoryOverview.exit(player, false);
        pendingClaims.put(player.getUniqueId(), new PendingClaim(UUID.randomUUID(), selection.cityId(),
                selection.target(), selection.revision()));
        open(player, MenuType.PURCHASE_CONFIRM);
    }

    public void forget(Player player) { requests.remove(player.getUniqueId()); }

    public void clearPlayer(Player player) {
        forget(player);
        pendingFounding.remove(player.getUniqueId());
        pendingPromotions.remove(player.getUniqueId());
        pendingUpgrades.remove(player.getUniqueId());
        pendingClaims.remove(player.getUniqueId());
        pendingMemberKicks.remove(player.getUniqueId());
        pendingRoleTargets.remove(player.getUniqueId());
        cityIds.remove(player.getUniqueId());
        cityViews.remove(player.getUniqueId());
        treasuryHistory.remove(player.getUniqueId());
        diplomacySnapshots.remove(player.getUniqueId());
        diplomacyTargets.remove(player.getUniqueId());
        diplomacySelectedTarget.remove(player.getUniqueId());
        treasuryTransferTargets.remove(player.getUniqueId());
        pendingTreasuryTransfers.remove(player.getUniqueId());
        awaitingTransferAmount.remove(player.getUniqueId());
        marketListings.remove(player.getUniqueId()); myMarketListings.remove(player.getUniqueId()); marketDeliveries.remove(player.getUniqueId());
        selectedMarketListing.remove(player.getUniqueId()); pendingMarketListings.remove(player.getUniqueId());
        awaitingMarketPrice.remove(player.getUniqueId());
        diplomacySelectedType.remove(player.getUniqueId());
        diplomacyDuration.remove(player.getUniqueId());
        purchaseTargets.remove(player.getUniqueId());
        purchasePages.remove(player.getUniqueId());
        territoryOrigins.remove(player.getUniqueId());
        adminTargets.remove(player.getUniqueId());
        adminRecoveryRows.remove(player.getUniqueId());
        adminRecoverySelection.remove(player.getUniqueId());
        adminRecoveryPages.remove(player.getUniqueId());
    }

    public boolean isAwaitingCityName(java.util.UUID playerId) {
        PendingFounding pending = pendingFounding.get(playerId);
        if (pending == null) return false;
        if (pending.createdAt.plusSeconds(300).isBefore(Instant.now())) {
            pendingFounding.remove(playerId, pending);
            return false;
        }
        return pending.cityName == null;
    }

    public boolean isAwaitingTransferAmount(UUID playerId) { return awaitingTransferAmount.containsKey(playerId); }

    public boolean isAwaitingMarketPrice(UUID playerId) { return awaitingMarketPrice.contains(playerId); }

    public void acceptMarketPrice(Player player, String input) {
        UUID playerId = player.getUniqueId();
        if (!awaitingMarketPrice.remove(playerId)) return;
        PendingMarketListing pending = pendingMarketListings.get(playerId);
        if (pending == null) return;
        String value = input.strip();
        if (value.equalsIgnoreCase("отмена") || value.equalsIgnoreCase("cancel")) {
            pendingMarketListings.remove(playerId, pending);
            player.sendMessage(Component.text("Создание объявления отменено.", NamedTextColor.YELLOW)); return;
        }
        long price;
        try { price = Long.parseLong(value); }
        catch (NumberFormatException invalid) {
            awaitingMarketPrice.add(playerId);
            player.sendMessage(Component.text("Введите целую цену в монетах или напишите «отмена».", NamedTextColor.RED)); return;
        }
        long max = Math.max(1L, plugin.getConfig().getLong("market.maximum-listing-price", 1_000_000));
        if (price <= 0 || price > max) {
            awaitingMarketPrice.add(playerId);
            player.sendMessage(Component.text("Цена должна быть от 1 до " + max + " монет.", NamedTextColor.RED)); return;
        }
        pendingMarketListings.put(playerId, pending.withPrice(price));
        open(player, MenuType.MARKET_CREATE_CONFIRM);
    }

    public void acceptTransferAmount(Player player, String input) {
        UUID playerId = player.getUniqueId();
        UUID targetId = awaitingTransferAmount.remove(playerId);
        if (targetId == null) return;
        String value = input.strip();
        if (value.equalsIgnoreCase("отмена") || value.equalsIgnoreCase("cancel")) {
            player.sendMessage(Component.text("Перевод отменён.", NamedTextColor.YELLOW));
            return;
        }
        long amount;
        try { amount = Long.parseLong(value); }
        catch (NumberFormatException invalid) {
            awaitingTransferAmount.put(playerId, targetId);
            player.sendMessage(Component.text("Введите целое число монет или напишите «отмена».", NamedTextColor.RED));
            return;
        }
        long maximum = Math.max(1L, plugin.getConfig().getLong("diplomacy.maximum-transfer-amount", 100000));
        if (amount <= 0 || amount > maximum) {
            awaitingTransferAmount.put(playerId, targetId);
            player.sendMessage(Component.text("Сумма должна быть от 1 до " + maximum + " монет.", NamedTextColor.RED));
            return;
        }
        pendingTreasuryTransfers.put(playerId, new PendingTreasuryTransfer(UUID.randomUUID(), targetId, amount));
        open(player, MenuType.TREASURY_TRANSFER_CONFIRM);
    }

    public void acceptCityName(Player player, String input) {
        PendingFounding pending = pendingFounding.get(player.getUniqueId());
        if (pending == null || pending.cityName != null) return;
        String name = input.strip();
        if (name.equalsIgnoreCase("отмена") || name.equalsIgnoreCase("cancel")) {
            pendingFounding.remove(player.getUniqueId(), pending);
            player.sendMessage(Component.text("Основание города отменено.", NamedTextColor.YELLOW));
            return;
        }
        int length = name.codePointCount(0, name.length());
        if (length < 3 || length > 32) {
            player.sendMessage(Component.text("Название должно содержать от 3 до 32 символов. Напишите название в чат.", NamedTextColor.RED));
            return;
        }
        PendingFounding named = pending.withName(name);
        pendingFounding.put(player.getUniqueId(), named);
        showFounding(player, MenuType.FOUNDING_CONFIRM, named);
    }

    public void open(Player player, MenuType type) {
        if (type == MenuType.ADMIN_TOOLS || type == MenuType.ADMIN_TARGET
                || type == MenuType.ADMIN_RECOVERY || type == MenuType.ADMIN_RECOVERY_DETAIL) {
            if (!player.isOp()) {
                player.sendMessage(Component.text("Админ-раздел доступен только OP.", NamedTextColor.RED));
                return;
            }
            showAdmin(player, type);
            return;
        }
        if (!cities.isReady()) {
            player.sendMessage(Component.text("Городские механики ещё не подключены. Книга готова к подключению Cities.", NamedTextColor.YELLOW));
            return;
        }
        if (type == MenuType.DIPLOMACY) { openDiplomacy(player); return; }
        java.util.UUID requestId = java.util.UUID.randomUUID();
        requests.put(player.getUniqueId(), requestId);
        cities.cityForPlayer(player.getUniqueId()).whenComplete((city, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !requestId.equals(requests.get(player.getUniqueId()))) return;
                requests.remove(player.getUniqueId());
                if (error != null) {
                    plugin.getLogger().warning("Cannot read city: " + error);
                    player.sendMessage(Component.text("Не удалось загрузить город.", NamedTextColor.RED));
                } else if (city.isEmpty()) {
                    showFounding(player, MenuType.FOUNDING, null);
                } else if (type == MenuType.TREASURY) {
                    openTreasury(player, city.get());
                } else if (type == MenuType.MARKET) {
                    openMarket(player, city.get());
                } else if (type == MenuType.MARKET_MY_LISTINGS) {
                    openMyMarketListings(player, city.get());
                } else if (type == MenuType.TERRITORY) {
                    openTerritory(player, city.get());
                } else show(player, type, snapshot(city.get()), false, city.get().id());
            });
        });
    }

    private void openMarket(Player player, CityView city) {
        var market = cities.marketService().orElse(null);
        if (market == null || !market.isReady()) {
            show(player, MenuType.MARKET, snapshot(city), false, city.id());
            player.sendMessage(Component.text("Городской рынок временно недоступен.", NamedTextColor.RED)); return;
        }
        UUID playerId = player.getUniqueId(); UUID requestId = UUID.randomUUID(); requests.put(playerId, requestId);
        market.listings(playerId).thenCombine(market.deliveries(playerId), MarketData::new).whenComplete((data, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !requestId.equals(requests.get(playerId))) return;
                requests.remove(playerId, requestId);
                marketListings.put(playerId, error == null ? data.listings : List.of());
                marketDeliveries.put(playerId, error == null ? data.deliveries : List.of());
                show(player, MenuType.MARKET, snapshot(city), false, city.id());
                if (error != null) player.sendMessage(Component.text("Не удалось загрузить рынок.", NamedTextColor.RED));
            });
        });
    }

    private void openMyMarketListings(Player player, CityView city) {
        var market = cities.marketService().orElse(null);
        if (market == null || !market.isReady()) { player.sendMessage(Component.text("Рынок временно недоступен.", NamedTextColor.RED)); return; }
        UUID playerId = player.getUniqueId(); UUID requestId = UUID.randomUUID(); requests.put(playerId, requestId);
        market.myListings(playerId).whenComplete((listings, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !requestId.equals(requests.get(playerId))) return;
                requests.remove(playerId, requestId); myMarketListings.put(playerId, error == null ? listings : List.of());
                show(player, MenuType.MARKET_MY_LISTINGS, snapshot(city), false, city.id());
            });
        });
    }

    private void openDiplomacy(Player player) {
        var service = cities.diplomacyService().orElse(null);
        if (service == null || !service.isReady()) {
            player.sendMessage(Component.text("Дипломатия пока недоступна.", NamedTextColor.YELLOW)); return;
        }
        UUID playerId = player.getUniqueId();
        UUID requestId = UUID.randomUUID(); requests.put(playerId, requestId);
        service.snapshot(playerId).whenComplete((snapshot, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !requestId.equals(requests.get(playerId))) return;
                if (error != null || snapshot == null || snapshot.isEmpty()) {
                    requests.remove(playerId, requestId);
                    player.sendMessage(Component.text("Не удалось загрузить дипломатию города.", NamedTextColor.RED)); return;
                }
                diplomacySnapshots.put(playerId, snapshot.get());
                cities.cityForPlayer(playerId).whenComplete((city, cityError) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || !requestId.equals(requests.get(playerId))) return;
                    requests.remove(playerId, requestId);
                    if (cityError != null || city == null || city.isEmpty()) return;
                    cityIds.put(playerId, city.get().id());
                    show(player, MenuType.DIPLOMACY, snapshot(city.get()), false, city.get().id());
                }));
            });
        });
    }

    public void openPreview(Player player, MenuType type) {
        // Explicit admin preview only; never publishes fixture data through CitiesService.
        requests.remove(player.getUniqueId());
        pendingUpgrades.remove(player.getUniqueId());
        CitySnapshot city = new CitySnapshot("Камнедолбинск · макет", CityStage.CITY, 17, 24580, 0,
            List.of(new CitySnapshot.ResidentEntry(UUID.randomUUID(), "PechenkaNow", "RULER"),
                    new CitySnapshot.ResidentEntry(UUID.randomUUID(), "Neronius", "DEPUTY"),
                    new CitySnapshot.ResidentEntry(UUID.randomUUID(), "Artemis", "TREASURER"),
                    new CitySnapshot.ResidentEntry(UUID.randomUUID(), "Luna", "BUILDER"),
                    new CitySnapshot.ResidentEntry(UUID.randomUUID(), "Kray", "MEMBER"),
                    new CitySnapshot.ResidentEntry(UUID.randomUUID(), "Velmira", "MEMBER"),
                    new CitySnapshot.ResidentEntry(UUID.randomUUID(), "Witch", "MEMBER")), java.util.Set.of());
        cityIds.remove(player.getUniqueId());
        cityViews.remove(player.getUniqueId());
        treasuryHistory.remove(player.getUniqueId());
        purchaseTargets.remove(player.getUniqueId());
        purchasePages.remove(player.getUniqueId());
        territoryOrigins.remove(player.getUniqueId());
        show(player, type, city, true, null);
    }

    private void openTreasury(Player player, CityView city) {
        var diplomacy = cities.diplomacyService().orElse(null);
        if (diplomacy != null && diplomacy.isReady()) diplomacy.snapshot(player.getUniqueId()).whenComplete((snapshot, error) -> {
            if (error != null || snapshot == null || snapshot.isEmpty() || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                diplomacySnapshots.put(player.getUniqueId(), snapshot.get());
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder
                        && holder.session().menuType() == MenuType.TREASURY) show(player, MenuType.TREASURY, snapshot(city), false, city.id());
            });
        });
        var treasury = cities.treasuryService().orElse(null);
        if (treasury == null || !treasury.isReady()) {
            treasuryHistory.put(player.getUniqueId(), List.of());
            show(player, MenuType.TREASURY, snapshot(city), false, city.id());
            player.sendMessage(Component.text("История казны временно недоступна.", NamedTextColor.YELLOW));
            return;
        }
        UUID requestId = UUID.randomUUID();
        requests.put(player.getUniqueId(), requestId);
        treasury.history(player.getUniqueId(), 5).whenComplete((entries, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !requestId.equals(requests.get(player.getUniqueId()))) return;
                requests.remove(player.getUniqueId());
                treasuryHistory.put(player.getUniqueId(), error == null && entries != null ? entries : List.of());
                show(player, MenuType.TREASURY, snapshot(city), false, city.id());
                if (isSecurityError(error)) {
                    player.sendMessage(Component.text("Историю казны могут смотреть Казначей, Заместитель и Правитель.", NamedTextColor.YELLOW));
                } else if (error != null) plugin.getLogger().warning("Cannot load treasury history: " + error);
            });
        });
    }

    private CitySnapshot snapshot(CityView city) {
        return new CitySnapshot(city.name(), city.stage(), city.chunks().size(), city.treasury(), city.revision(),
            city.residents().stream().map(r -> new CitySnapshot.ResidentEntry(r.playerId(), r.name(), r.role())).toList(),
            city.chunks(), city.upgrades(), city.laws(), city.taxPolicy());
    }

    private void openTerritory(Player player, CityView city) {
        CityTerritoryService service = cities.territoryService().orElse(null);
        if (service == null || !service.isReady()) {
            show(player, MenuType.TERRITORY, snapshot(city), false, city.id());
            player.sendMessage(Component.text("Список доступных чанков временно недоступен.", NamedTextColor.YELLOW));
            return;
        }
        UUID requestId = UUID.randomUUID();
        requests.put(player.getUniqueId(), requestId);
        service.availableTargets(city.id()).whenComplete((targets, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !requestId.equals(requests.get(player.getUniqueId()))) return;
                requests.remove(player.getUniqueId());
                if (error != null) {
                    plugin.getLogger().warning("Cannot load territory map: " + error);
                    player.sendMessage(Component.text("Не удалось загрузить доступные участки.", NamedTextColor.YELLOW));
                }
                List<ChunkPosition> safeTargets = error == null && targets != null ? List.copyOf(targets) : List.of();
                purchaseTargets.put(player.getUniqueId(), safeTargets);
                purchasePages.put(player.getUniqueId(), 0);
                territoryOrigins.put(player.getUniqueId(), territoryOrigin(city, player.getWorld().getUID()));
                show(player, MenuType.TERRITORY, snapshot(city), false, city.id());
            });
        });
    }

    private TerritoryOrigin territoryOrigin(CityView city, UUID preferredWorld) {
        ChunkPosition reference = city.chunks().stream().filter(chunk -> chunk.worldId().equals(preferredWorld))
                .findFirst().orElseGet(() -> city.chunks().stream()
                        .min(java.util.Comparator.comparing(ChunkPosition::worldId)
                                .thenComparingInt(ChunkPosition::x).thenComparingInt(ChunkPosition::z)).orElse(null));
        if (reference == null) return null;
        UUID worldId = reference.worldId();
        List<ChunkPosition> owned = city.chunks().stream().filter(chunk -> chunk.worldId().equals(worldId)).toList();
        int centerX = (int) (((long) owned.stream().mapToInt(ChunkPosition::x).min().orElse(reference.x())
                + owned.stream().mapToInt(ChunkPosition::x).max().orElse(reference.x())) / 2);
        int centerZ = (int) (((long) owned.stream().mapToInt(ChunkPosition::z).min().orElse(reference.z())
                + owned.stream().mapToInt(ChunkPosition::z).max().orElse(reference.z())) / 2);
        return new TerritoryOrigin(worldId, centerX, centerZ);
    }

    private String roleName(String role) {
        return CityRole.fromStorage(role).displayName();
    }

    private void show(Player player, MenuType type, CitySnapshot city, boolean preview, java.util.UUID cityId) {
        if (cityId != null) {
            cityIds.put(player.getUniqueId(), cityId);
            cityViews.put(player.getUniqueId(), city);
        }
        MenuHolder holder = new MenuHolder(MenuSession.create(player.getUniqueId(), type, city.revision(), preview));
        Inventory inventory = Bukkit.createInventory(holder, 54, title(type, preview, city));
        holder.bind(inventory);
        render(inventory, type, city, player.getUniqueId(), preview);
        if (preview) inventory.setItem(0, button(Material.BARRIER, "Макет интерфейса", "Все данные демонстрационные. Покупки отключены."));
        player.openInventory(inventory);
    }

    private Component title(MenuType type, boolean preview) {
        return title(type, preview, null);
    }

    private Component title(MenuType type, boolean preview, CitySnapshot city) {
        boolean backgrounds = preview && plugin.getConfig().getBoolean("visual.use-pixel-backgrounds", false);
        if (preview && backgrounds && type != MenuType.MARKET && type.glyph() != '\u0000') {
            // Исходные полноэкранные макеты оставлены только для read-only предпросмотра.
            return Component.text(String.valueOf(type.glyph()))
                    .font(GUI_FONT)
                    .color(NamedTextColor.WHITE)
                    .append(Component.text("  Градострой — " + type.fallbackTitle(), NamedTextColor.DARK_GRAY).font(DEFAULT_FONT));
        }
        if (!plugin.getConfig().getBoolean("visual.use-pixel-backgrounds", false)) {
            return Component.text("Градострой — " + type.fallbackTitle(), NamedTextColor.DARK_GRAY);
        }

        return BookMenuTitle.create(type, city);
    }

    private void render(Inventory inv, MenuType type, CitySnapshot city, UUID playerId, boolean preview) {
        switch (type) {
            case MAIN -> renderMain(inv, city, !preview && Bukkit.getPlayer(playerId) != null && Bukkit.getPlayer(playerId).isOp(),
                    !preview && plugin.getConfig().getBoolean("visual.use-pixel-backgrounds", false));
            case TERRITORY -> renderTerritory(inv, city, playerId, preview);
            case PURCHASE -> renderPurchase(inv, city, playerId, preview);
            case PURCHASE_CONFIRM -> renderClaimConfirm(inv, city, playerId, preview);
            case UPGRADES -> renderUpgrades(inv, city);
            case TREASURY -> renderTreasury(inv, city, playerId);
            case TREASURY_TRANSFER_TARGETS -> renderTreasuryTransferTargets(inv, playerId);
            case TREASURY_TRANSFER_CONFIRM -> renderTreasuryTransferConfirm(inv, playerId, preview);
            case RESIDENTS -> renderResidents(inv, city);
            case MEMBER_KICK_CONFIRM -> renderMemberKickConfirm(inv, playerId, preview);
            case MEMBER_ROLE_MANAGEMENT -> renderMemberRoleManagement(inv, city, playerId, preview);
            case MANAGEMENT -> renderManagement(inv, city);
            case CITY_LAWS -> renderCityLaws(inv, city, playerId);
            case CITY_TAX -> renderCityTax(inv, city, playerId);
            case DIPLOMACY -> renderDiplomacy(inv, city, playerId);
            case DIPLOMACY_TARGETS -> renderDiplomacyTargets(inv, playerId);
            case DIPLOMACY_CREATE -> renderDiplomacyCreate(inv, playerId);
            case MARKET -> renderMarket(inv, city, playerId);
            case MARKET_MY_LISTINGS -> renderMyMarketListings(inv, playerId);
            case MARKET_CONFIRM -> renderMarketConfirm(inv, playerId);
            case MARKET_CANCEL_CONFIRM -> renderMarketCancelConfirm(inv, playerId);
            case MARKET_CREATE_CONFIRM -> renderMarketCreateConfirm(inv, playerId);
            case FOUNDING, FOUNDING_CONFIRM -> { }
            case PROGRESSION -> renderProgression(inv, city, false);
            case PROMOTION_CONFIRM -> renderProgression(inv, city, true);
            case UPGRADE_CONFIRM -> renderUpgradeConfirm(inv, playerId, preview);
            case ADMIN_TOOLS, ADMIN_TARGET, ADMIN_RECOVERY, ADMIN_RECOVERY_DETAIL -> { }
        }
    }

    private void renderProgression(Inventory inv, CitySnapshot city, boolean confirm) {
        CityProgressionService service = cities.progressionService().orElse(null);
        java.util.Optional<CityStage> next = service == null ? java.util.Optional.empty() : service.nextStage(city.stage());
        if (next.isEmpty()) {
            inv.setItem(13, button(Material.BOOK, "Развитие города", "Переход для этапа " + city.stage().displayName() + " не настроен."));
            inv.setItem(45, back());
            return;
        }
        long price = service.promotionPrice(city.stage());
        int minResidents = service.minimumResidents(city.stage());
        int minChunks = service.minimumChunks(city.stage());
        if (confirm) {
            inv.setItem(13, button(Material.EXPERIENCE_BOTTLE, "Переход в «" + next.get().displayName() + "»",
                    "Текущий этап: " + city.stage().displayName(), "Стоимость: " + money(price),
                    "Жителей: " + city.residents().size() + " / " + minResidents,
                    "Чанков: " + city.territory() + " / " + minChunks,
                    "Ревизия города: " + city.revision()));
            inv.setItem(29, button(Material.LIME_CONCRETE, "Подтвердить переход", "Стоимость спишется не более одного раза."));
            inv.setItem(33, button(Material.RED_CONCRETE, "Отмена", "Вернуться к развитию города."));
            return;
        }
        boolean eligible = city.residents().size() >= minResidents && city.territory() >= minChunks;
        inv.setItem(13, button(Material.BOOK, "Развитие: " + city.name(),
                "Этап: " + city.stage().displayName() + " → " + next.get().displayName(),
                "Жители: " + city.residents().size() + " / " + minResidents,
                "Территория: " + city.territory() + " / " + minChunks + " чанков",
                "Стоимость: " + money(price)));
        inv.setItem(31, button(eligible ? Material.LIME_CONCRETE : Material.BARRIER,
                eligible ? "Перейти в «" + next.get().displayName() + "»" : "Требования не выполнены",
                "Открыть подтверждение."));
        inv.setItem(45, back());
    }

    private void showFounding(Player player, MenuType type, PendingFounding pending) {
        MenuHolder holder = new MenuHolder(MenuSession.create(player.getUniqueId(), type, 0, false));
        Inventory inventory = Bukkit.createInventory(holder, 54, title(type, false));
        holder.bind(inventory);
        if (type == MenuType.FOUNDING) {
            boolean available = cities.foundingService().filter(CityFoundingService::isReady).isPresent();
            inventory.setItem(13, button(Material.BOOK, "Основание города",
                    "Город получит 4 соседних чанка 2×2.", "Опорным будет чанк под вами.", "",
                    available ? "Создание бесплатное." : "Сервис основания сейчас недоступен."));
            inventory.setItem(31, button(available ? Material.LIME_CONCRETE : Material.BARRIER,
                    available ? "Начать основание" : "Недоступно", "Выберите название города в чате."));
            if (player.isOp()) inventory.setItem(40, button(Material.COMMAND_BLOCK, "Админ", "Тестовые настройки городов"));
        } else {
            inventory.setItem(13, button(Material.GRASS_BLOCK, pending.cityName,
                    "Мир: " + pending.anchor.worldId(), "Опорный чанк: X " + pending.anchor.x() + ", Z " + pending.anchor.z(),
                    "Будут закреплены 4 чанка в квадрате 2×2.", "Стоимость: бесплатно"));
            inventory.setItem(29, button(Material.LIME_CONCRETE, "Основать город", "Подтвердить создание и занять стартовые чанки."));
            inventory.setItem(33, button(Material.RED_CONCRETE, "Отмена", "Создание города не начнётся."));
        }
        player.openInventory(inventory);
    }

    private void beginFounding(Player player) {
        CityFoundingService service = cities.foundingService().orElse(null);
        if (service == null || !service.isReady()) {
            player.sendMessage(Component.text("Сервис основания города сейчас недоступен.", NamedTextColor.RED));
            return;
        }
        ChunkPosition anchor = new ChunkPosition(player.getWorld().getUID(), player.getLocation().getChunk().getX(),
                player.getLocation().getChunk().getZ());
        pendingFounding.put(player.getUniqueId(), new PendingFounding(java.util.UUID.randomUUID(), anchor, null, Instant.now()));
        player.closeInventory();
        player.sendMessage(Component.text("Напишите название города в чат (3–32 символа). Ваш стартовый участок будет 2×2 чанка вокруг текущего.", NamedTextColor.YELLOW));
    }

    private void confirmFounding(Player player) {
        PendingFounding pending = pendingFounding.remove(player.getUniqueId());
        if (pending == null || pending.cityName == null) return;
        CityFoundingService service = cities.foundingService().orElse(null);
        if (service == null || !service.isReady()) {
            player.sendMessage(Component.text("Сервис основания города сейчас недоступен.", NamedTextColor.RED));
            return;
        }
        player.closeInventory();
        player.sendMessage(Component.text("Проверяю участок и создаю город…", NamedTextColor.YELLOW));
        CityFoundationRequest request = new CityFoundationRequest(pending.operationId, player.getUniqueId(),
                player.getName(), pending.cityName, pending.anchor);
        service.found(request).whenComplete((result, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (error != null) {
                    player.sendMessage(Component.text("Не удалось создать город. Попробуйте начать заново.", NamedTextColor.RED));
                    plugin.getLogger().warning("City founding failed for operation " + pending.operationId + ": " + error);
                } else if (result.code() == CityFoundationCode.CREATED || result.code() == CityFoundationCode.REPLAYED) {
                    player.sendMessage(Component.text("Город «" + result.city().orElseThrow().name() + "» основан!", NamedTextColor.GREEN));
                    open(player, MenuType.MAIN);
                } else {
                    player.sendMessage(Component.text(foundingMessage(result.code()), NamedTextColor.RED));
                    open(player, MenuType.FOUNDING);
                }
            });
        });
    }

    private String foundingMessage(CityFoundationCode code) {
        return switch (code) {
            case INVALID_NAME -> "Название города не подходит. Используйте 3–32 буквы, цифры, пробел, _ или - .";
            case ALREADY_IN_CITY -> "Вы уже состоите в городе.";
            case NAME_ALREADY_USED -> "Город с таким названием уже существует.";
            case CHUNK_ALREADY_CLAIMED -> "Часть стартовой территории уже занята.";
            case WORLD_UNAVAILABLE -> "Мир для основания сейчас недоступен.";
            case PROTECTED_ZONE -> "Стартовая территория находится в защищённой зоне или за границей мира.";
            case OPERATION_CONFLICT -> "Запрос основания конфликтует с предыдущей операцией.";
            case SERVICE_UNAVAILABLE -> "Сервис основания города сейчас недоступен.";
            case INTERNAL_ERROR -> "Не удалось создать город из-за внутренней ошибки.";
            case CREATED, REPLAYED -> "Город создан.";
        };
    }

    private void beginPromotion(Player player, MenuSession session) {
        CityProgressionService progression = cities.progressionService().orElse(null);
        java.util.UUID cityId = cityIds.get(player.getUniqueId());
        CitySnapshot city = cityViews.get(player.getUniqueId());
        if (progression == null || !progression.isReady() || cityId == null || city == null) {
            player.sendMessage(Component.text("Развитие города сейчас недоступно.", NamedTextColor.RED));
            return;
        }
        if (progression.nextStage(city.stage()).isEmpty()) {
            player.sendMessage(Component.text("Для этого этапа переход пока не настроен.", NamedTextColor.RED));
            return;
        }
        if (city.residents().size() < progression.minimumResidents(city.stage())
                || city.territory() < progression.minimumChunks(city.stage())) {
            player.sendMessage(Component.text("Город пока не выполняет требования для перехода.", NamedTextColor.RED));
            return;
        }
        pendingPromotions.put(player.getUniqueId(), new PendingPromotion(UUID.randomUUID(), cityId,
                city.stage(), session.cityRevision()));
        open(player, MenuType.PROMOTION_CONFIRM);
    }

    private void confirmPromotion(Player player) {
        PendingPromotion pending = pendingPromotions.remove(player.getUniqueId());
        CityProgressionService progression = cities.progressionService().orElse(null);
        if (pending == null || progression == null || !progression.isReady()) {
            player.sendMessage(Component.text("Развитие города сейчас недоступно.", NamedTextColor.RED));
            return;
        }
        player.closeInventory();
        player.sendMessage(Component.text("Проверяю требования и выполняю переход…", NamedTextColor.YELLOW));
        CityPromotionRequest request = new CityPromotionRequest(pending.operationId, pending.cityId,
                player.getUniqueId(), pending.expectedStage, pending.revision);
        progression.promote(request).whenComplete((result, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (error != null) {
                    player.sendMessage(Component.text("Не удалось подтвердить результат перехода. Не повторяйте оплату; обратитесь к администратору.", NamedTextColor.RED));
                    plugin.getLogger().warning("City promotion failed for operation " + pending.operationId + ": " + error);
                } else if (result.code() == CityPromotionCode.PROMOTED || result.code() == CityPromotionCode.REPLAYED) {
                    player.sendMessage(Component.text("Город перешёл на этап «" + result.city().orElseThrow().stage().displayName() + "»!", NamedTextColor.GREEN));
                    open(player, MenuType.MAIN);
                } else {
                    player.sendMessage(Component.text(promotionMessage(result.code()), NamedTextColor.RED));
                    open(player, MenuType.PROGRESSION);
                }
            });
        });
    }

    private String promotionMessage(CityPromotionCode code) {
        return switch (code) {
            case NOT_FOUND -> "Город не найден.";
            case NOT_MEMBER -> "Вы не состоите в этом городе.";
            case PERMISSION_DENIED -> "У вашей должности нет права повышать этап города.";
            case INVALID_STAGE -> "Такой переход этапа сейчас недоступен.";
            case STALE_REVISION -> "Состояние города изменилось. Откройте книгу заново.";
            case REQUIREMENTS_NOT_MET -> "Город пока не выполняет требования для перехода.";
            case INSUFFICIENT_FUNDS -> "Недостаточно монет для перехода.";
            case INSUFFICIENT_TREASURY -> "В казне города недостаточно монет.";
            case ECONOMY_UNAVAILABLE -> "Экономика сейчас недоступна.";
            case OPERATION_CONFLICT -> "Операция конфликтует с предыдущим запросом.";
            case OPERATION_IN_PROGRESS, UNKNOWN_OUTCOME -> "Операция ещё не подтверждена. Не повторяйте оплату; обратитесь к администратору.";
            case SERVICE_UNAVAILABLE -> "Сервис развития города сейчас недоступен.";
            case INTERNAL_ERROR -> "Не удалось выполнить переход из-за внутренней ошибки.";
            case PROMOTED, REPLAYED -> "Переход выполнен.";
        };
    }

    private void beginUpgrade(Player player, MenuSession session, CityUpgradeId id) {
        if (session.preview()) {
            player.sendMessage(Component.text("Покупки улучшений отключены в макете.", NamedTextColor.YELLOW));
            return;
        }
        CityUpgradeService service = cities.upgradeService().orElse(null);
        CitySnapshot city = cityViews.get(player.getUniqueId());
        UUID cityId = cityIds.get(player.getUniqueId());
        CityUpgradeView definition = service == null ? null : service.upgrade(id).orElse(null);
        if (service == null || !service.isReady() || city == null || cityId == null || definition == null) {
            player.sendMessage(Component.text("Улучшения города сейчас недоступны.", NamedTextColor.RED));
            return;
        }
        if (!definition.enabled()) {
            player.sendMessage(Component.text("Это улучшение отключено в конфигурации.", NamedTextColor.YELLOW));
            return;
        }
        if (city.upgrades().contains(id)) {
            player.sendMessage(Component.text("Это улучшение уже куплено.", NamedTextColor.YELLOW));
            return;
        }
        if (city.stage().ordinal() < definition.minimumStage().ordinal()) {
            player.sendMessage(Component.text("Улучшение откроется на этапе «" + definition.minimumStage().displayName() + "».", NamedTextColor.YELLOW));
            return;
        }
        if (!city.upgrades().containsAll(definition.prerequisites())) {
            player.sendMessage(Component.text("Сначала купите предварительные улучшения.", NamedTextColor.YELLOW));
            return;
        }
        pendingUpgrades.put(player.getUniqueId(), new PendingUpgrade(UUID.randomUUID(), cityId,
                session.cityRevision(), id));
        open(player, MenuType.UPGRADE_CONFIRM);
    }

    private void confirmUpgrade(Player player) {
        PendingUpgrade pending = pendingUpgrades.remove(player.getUniqueId());
        CityUpgradeService service = cities.upgradeService().orElse(null);
        if (pending == null || service == null || !service.isReady()) {
            player.sendMessage(Component.text("Покупка улучшения сейчас недоступна.", NamedTextColor.RED));
            return;
        }
        player.closeInventory();
        player.sendMessage(Component.text("Проверяю условия и покупаю улучшение…", NamedTextColor.YELLOW));
        CityUpgradeRequest request = new CityUpgradeRequest(pending.operationId, pending.cityId,
                player.getUniqueId(), pending.revision, pending.upgradeId);
        service.purchase(request).whenComplete((result, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (error != null || result == null) {
                    player.sendMessage(Component.text("Не удалось подтвердить результат покупки. Не повторяйте оплату; обратитесь к администратору.", NamedTextColor.RED));
                    plugin.getLogger().warning("City upgrade purchase failed for operation " + pending.operationId + ": " + error);
                } else if (result.code() == CityUpgradeCode.PURCHASED || result.code() == CityUpgradeCode.REPLAYED) {
                    player.sendMessage(Component.text("Улучшение установлено. Эффект действует на территории города.", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text(upgradeMessage(result.code()), NamedTextColor.RED));
                }
                open(player, MenuType.UPGRADES);
            });
        });
    }

    private String upgradeMessage(CityUpgradeCode code) {
        return switch (code) {
            case NO_CITY, NOT_FOUND -> "Город не найден.";
            case NOT_MEMBER -> "Вы не состоите в этом городе.";
            case PERMISSION_DENIED -> "У вашей должности нет права покупать улучшения.";
            case STALE_REVISION -> "Город изменился. Откройте улучшения и повторите покупку.";
            case UPGRADE_DISABLED -> "Это улучшение отключено.";
            case STAGE_REQUIRED -> "Сначала повысьте этап города.";
            case PREREQUISITE_REQUIRED -> "Сначала купите предварительное улучшение.";
            case ALREADY_OWNED -> "Это улучшение уже куплено.";
            case INSUFFICIENT_TREASURY -> "В городской казне недостаточно монет.";
            case OPERATION_CONFLICT -> "Операция конфликтует с предыдущим запросом.";
            case OPERATION_IN_PROGRESS -> "Покупка ещё обрабатывается. Проверьте улучшения позже.";
            case SERVICE_UNAVAILABLE -> "Сервис улучшений временно недоступен.";
            case INTERNAL_ERROR -> "Не удалось купить улучшение.";
            case PURCHASED, REPLAYED -> "Улучшение установлено.";
        };
    }

    private void renderMain(Inventory inv, CitySnapshot c, boolean isOp, boolean bookLayout) {
        inv.setItem(4, button(Material.BOOK, c.name(),
                "Этап: " + c.stage().displayName(),
                "Территория: " + c.territory() + " / " + c.territoryLimit(),
                "Казна: " + money(c.treasury()),
                "Жители: " + c.residents().size()));
        inv.setItem(49, button(Material.CHEST, "Рынок", "Торговые места города"));
        inv.setItem(20, button(Material.GRASS_BLOCK, "Территория", "Управление землями города"));
        inv.setItem(24, button(Material.PLAYER_HEAD, "Жители", "Состав города и должности"));
        inv.setItem(29, button(Material.COMPARATOR, "Улучшения", "Дерево развития города"));
        inv.setItem(33, button(Material.WRITABLE_BOOK, "Управление", "Права, законы и развитие"));
        inv.setItem(38, button(Material.GOLD_INGOT, "Казна", "Баланс и история операций"));
        inv.setItem(42, button(Material.BLUE_BANNER, "Дипломатия", "Отношения с другими городами"));
        if (isOp) inv.setItem(47, button(Material.COMMAND_BLOCK, "Админ", "Тестовые настройки городов"));
        if (bookLayout) {
            ItemStack treasury = inv.getItem(38);
            ItemStack diplomacy = inv.getItem(42);
            ItemStack admin = inv.getItem(47);
            ItemStack market = inv.getItem(49);
            inv.setItem(4, withModel(inv.getItem(4), "gradostroy:gui/empty"));
            inv.setItem(29, treasury);
            inv.setItem(38, diplomacy);
            inv.setItem(42, button(Material.WRITABLE_BOOK, "Законы", "Законы и правила города"));
            inv.setItem(47, market);
            inv.setItem(51, admin);
            inv.setItem(49, null);
            for (int center : MainMenuLayout.centers()) {
                ItemStack item = inv.getItem(center);
                if (item == null) continue;
                String model = switch (center) {
                    case 20 -> "territory"; case 24 -> "resident";
                    case 29 -> "treasury"; case 33 -> "management";
                    case 38 -> "diplomacy"; case 42 -> "law";
                    case 47 -> "market"; case 51 -> "admin";
                    default -> throw new IllegalStateException("Unknown main-menu button");
                };
                inv.setItem(center, withModel(item, "gradostroy:gui/button/menu_" + model));
                // Transparent neighbors preserve the same tooltip over the full clickable button.
                inv.setItem(center - 1, withModel(item, "gradostroy:gui/empty"));
                inv.setItem(center + 1, withModel(item, "gradostroy:gui/empty"));
            }
        }
    }

    private static ItemStack withModel(ItemStack source, String model) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString(model));
        item.setItemMeta(meta);
        return item;
    }

    private void showAdmin(Player player, MenuType type) {
        MenuHolder holder = new MenuHolder(MenuSession.create(player.getUniqueId(), type, 0, false));
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Градострой — " + type.fallbackTitle(), NamedTextColor.DARK_RED));
        holder.bind(inventory);
        if (type == MenuType.ADMIN_TOOLS) {
            inventory.setItem(4, button(Material.COMMAND_BLOCK, "Админ-возможности",
                    "Только для OP. Изменения тестовые и записываются в аудит.", "Выберите онлайн-игрока."));
            int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
            List<? extends Player> online = Bukkit.getOnlinePlayers().stream().sorted(java.util.Comparator.comparing(Player::getName,
                    String.CASE_INSENSITIVE_ORDER)).limit(slots.length).toList();
            for (int i = 0; i < online.size(); i++) {
                Player target = online.get(i);
                inventory.setItem(slots[i], button(Material.PLAYER_HEAD, target.getName(),
                        target.getUniqueId().toString(), "Нажмите, чтобы выбрать игрока."));
            }
            inventory.setItem(49, button(Material.CLOCK, "Обновить список", "Показать игроков в сети."));
            inventory.setItem(40, button(Material.RECOVERY_COMPASS, "Сверка платежей",
                    "Незавершённые пополнения казны", "Показать состояния Cities и Economy."));
            inventory.setItem(45, button(Material.ARROW, "Назад", "Вернуться в книгу города."));
        } else if (type == MenuType.ADMIN_RECOVERY) {
            List<CityTreasuryRecoveryView> pending = adminRecoveryRows.getOrDefault(player.getUniqueId(), List.of());
            int pages = Math.max(1, (pending.size() + 20) / 21);
            int page = Math.max(0, Math.min(adminRecoveryPages.getOrDefault(player.getUniqueId(), 0), pages - 1));
            adminRecoveryPages.put(player.getUniqueId(), page);
            inventory.setItem(4, button(Material.RECOVERY_COMPASS, "Платежи: " + pending.size() + " · стр. " + (page + 1) + "/" + pages,
                    "Только пополнения казны и ежедневные налоги.", "Выберите операцию для сверки."));
            int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
            int start = page * slots.length;
            for (int i = start; i < Math.min(start + slots.length, pending.size()); i++) {
                CityTreasuryRecoveryView row = pending.get(i);
                inventory.setItem(slots[i - start], button(Material.GOLD_INGOT,
                        (row.reason().equals("CITY_TAX") ? "Налог" : "Пополнение") + " · " + money(row.amount()),
                        "Город: " + row.cityId(), "Плательщик: " + row.actorId(),
                        "Cities: " + row.cityState(), "Economy: " + row.economyState(),
                        "Нажмите для просмотра."));
            }
            if (pending.isEmpty()) inventory.setItem(22, button(Material.LIME_DYE, "Зависших платежей нет",
                    "Журнал казны и Economy согласованы."));
            if (page > 0) inventory.setItem(48, button(Material.ARROW, "Предыдущая страница"));
            inventory.setItem(49, button(Material.CLOCK, "Обновить", "Заново прочитать журналы."));
            if (page + 1 < pages) inventory.setItem(50, button(Material.ARROW, "Следующая страница"));
            inventory.setItem(45, button(Material.ARROW, "Назад", "В админ-раздел."));
        } else if (type == MenuType.ADMIN_RECOVERY_DETAIL) {
            UUID selected = adminRecoverySelection.get(player.getUniqueId());
            CityTreasuryRecoveryView row = adminRecoveryRows.getOrDefault(player.getUniqueId(), List.of()).stream()
                    .filter(item -> item.operationId().equals(selected)).findFirst().orElse(null);
            if (row == null) inventory.setItem(22, button(Material.BARRIER, "Операция уже закрыта",
                    "Обновите список платежей."));
            else {
                inventory.setItem(4, button(Material.GOLD_INGOT,
                        row.reason().equals("CITY_TAX") ? "Ежедневный налог" : "Пополнение казны",
                        "Сумма: " + money(row.amount()), "Состояние Cities: " + row.cityState(),
                        "Состояние Economy: " + row.economyState()));
                inventory.setItem(20, button(Material.BOOK, "UUID операции", row.operationId().toString()));
                inventory.setItem(22, button(Material.PLAYER_HEAD, "Плательщик", row.actorId().toString()));
                inventory.setItem(24, button(Material.MAP, "Город", row.cityId().toString()));
                inventory.setItem(29, button(Material.LIME_DYE, "Сверить операцию",
                        "Продолжить известный этап", "без нового UUID и повторного списания."));
                inventory.setItem(33, button(Material.CLOCK, "Обновить состояния"));
            }
            inventory.setItem(45, button(Material.ARROW, "К платежам", "К списку операций."));
        } else {
            UUID targetId = adminTargets.get(player.getUniqueId());
            Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
            inventory.setItem(13, button(target == null ? Material.BARRIER : Material.PLAYER_HEAD,
                    target == null ? "Игрок вышел" : target.getName(),
                    "Тестовые действия для города игрока.", "Все изменения записываются в аудит."));
            if (target != null) {
                inventory.setItem(20, button(Material.GOLD_INGOT, "Добавить 10 000 в казну", "Тестовое пополнение без списания монет."));
                inventory.setItem(22, button(Material.GOLD_BLOCK, "Добавить 100 000 в казну", "Тестовое пополнение без списания монет."));
                inventory.setItem(24, button(Material.EMERALD_BLOCK, "Добавить 1 000 000 в казну", "Тестовое пополнение без списания монет."));
                CityStage[] stages = CityStage.values();
                int[] stageSlots = {29,30,31,32,33};
                for (int i = 0; i < stages.length; i++) {
                    inventory.setItem(stageSlots[i], button(Material.EXPERIENCE_BOTTLE,
                            "Установить: " + stages[i].displayName(), "Этап " + stages[i].name(), "Пропускает стоимость и требования."));
                }
            }
            inventory.setItem(45, button(Material.ARROW, "К игрокам", "Вернуться к выбору игрока."));
        }
        player.openInventory(inventory);
    }

    private void renderTerritory(Inventory inv, CitySnapshot c, UUID playerId, boolean preview) {
        inv.setItem(4, button(Material.GRASS_BLOCK, "Земли города",
                c.territory() + " / " + c.territoryLimit() + " чанков",
                c.territoryFull() ? "Лимит этапа достигнут" : "Можно расширяться"));
        TerritoryOrigin origin = preview ? null : territoryOrigins.get(playerId);
        org.bukkit.World world = origin == null ? null : Bukkit.getWorld(origin.worldId);
        String worldName = world == null ? "не загружен" : world.getName();
        inv.setItem(20, button(Material.GREEN_CONCRETE, "Принадлежит городу", c.territory() + " чанков"));
        inv.setItem(22, button(Material.YELLOW_CONCRETE, "Доступно для покупки",
                preview ? "Макет" : purchaseTargets.getOrDefault(playerId, List.of()).size() + " участков"));
        inv.setItem(24, button(Material.BARRIER, "Лимит этапа", c.territory() + " / " + c.territoryLimit() + " чанков"));
        inv.setItem(31, button(Material.COMPASS, "Мир города", worldName,
                "Точные границы смотрите в режиме полёта."));
        inv.setItem(49, button(Material.GOLD_INGOT, "Купить участок", "Список доступных соседних чанков",
                "Покупка подтверждается отдельно."));
        inv.setItem(47, button(Material.SPYGLASS, "Обзор с высоты",
                "WASD — движение по горизонтали", "Высота и направление камеры зафиксированы",
                "ЛКМ — купить выделенный чанк", "Shift — выйти из обзора"));
        inv.setItem(45, back());
    }

    private void renderPurchase(Inventory inv, CitySnapshot c, UUID playerId, boolean preview) {
        CityTerritoryService service = cities.territoryService().orElse(null);
        long price = service == null ? chunkPrice() : service.chunkPrice();
        inv.setItem(4, button(Material.GOLD_INGOT, "Цена за чанк: " + money(price),
                "Город: " + c.territory() + " / " + c.territoryLimit() + " чанков"));
        List<ChunkPosition> targets = preview ? List.of() : purchaseTargets.getOrDefault(playerId, List.of());
        int page = purchasePages.getOrDefault(playerId, 0);
        int start = page * purchaseSlots().length;
        int end = Math.min(targets.size(), start + purchaseSlots().length);
        int[] slots = purchaseSlots();
        for (int index = start; index < end; index++) {
            ChunkPosition target = targets.get(index);
            org.bukkit.World world = Bukkit.getWorld(target.worldId());
            String worldName = world == null ? target.worldId().toString().substring(0, 8) : world.getName();
            inv.setItem(slots[index - start], button(Material.GRASS_BLOCK,
                    "Чанк X " + target.x() + ", Z " + target.z(), worldName,
                    "Свободен и соседствует с городом.", "Нажмите, чтобы проверить и подтвердить покупку."));
        }
        if (targets.isEmpty()) {
            inv.setItem(22, button(Material.BARRIER, "Нет доступных чанков",
                    "Проверьте этап, лимит территории, границу мира и системные зоны."));
        }
        if (page > 0) inv.setItem(48, button(Material.ARROW, "Предыдущая страница"));
        if (end < targets.size()) inv.setItem(50, button(Material.ARROW, "Следующая страница"));
        inv.setItem(45, button(Material.ARROW, "Назад", "Вернуться к территории"));
    }

    private void renderClaimConfirm(Inventory inv, CitySnapshot city, UUID playerId, boolean preview) {
        PendingClaim pending = preview ? null : pendingClaims.get(playerId);
        if (pending == null) {
            inv.setItem(22, button(Material.BARRIER, "Покупка недоступна", "Выберите чанк на предыдущем экране."));
            inv.setItem(45, back());
            return;
        }
        CityTerritoryService service = cities.territoryService().orElse(null);
        long price = service == null ? chunkPrice() : service.chunkPrice();
        org.bukkit.World world = Bukkit.getWorld(pending.target.worldId());
        String worldName = world == null ? pending.target.worldId().toString() : world.getName();
        inv.setItem(13, button(Material.GRASS_BLOCK, "Покупка соседнего чанка",
                "Мир: " + worldName, "Координаты: X " + pending.target.x() + ", Z " + pending.target.z(),
                "Текущая территория: " + city.territory() + " / " + city.territoryLimit(),
                "После покупки: " + (city.territory() + 1) + " / " + city.territoryLimit(),
                "Стоимость: " + money(price), "Казна города: " + money(city.treasury()),
                "Оплата: из казны города."));
        inv.setItem(29, button(Material.LIME_CONCRETE, "Подтвердить покупку", "Средства спишутся из казны города."));
        inv.setItem(33, button(Material.RED_CONCRETE, "Отмена", "Вернуться к выбору чанка."));
        inv.setItem(45, button(Material.ARROW, "Назад", "Вернуться к выбору чанка."));
    }

    private int[] purchaseSlots() {
        return new int[]{10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    }

    private void renderUpgrades(Inventory inv, CitySnapshot c) {
        CityUpgradeService service = cities.upgradeService().orElse(null);
        inv.setItem(13, button(Material.BELL, "Ратуша", "Этап: " + c.stage().displayName(),
                "Лимит территории: " + c.territoryLimit() + " чанков"));
        renderUpgradeNode(inv, c, service, CityUpgradeId.CITY_WARDING, 20, Material.SHIELD,
                "Городской щит", "Защищает жителей от PvP на земле города.");
        renderUpgradeNode(inv, c, service, CityUpgradeId.CITY_GUARDS, 24, Material.IRON_CHESTPLATE,
                "Городская стража", "Защищает жителей от враждебных мобов на земле города.");
        inv.setItem(31, button(Material.WRITABLE_BOOK, "Развитие", "Повышение этапа открывает более высокий лимит чанков."));
        inv.setItem(45, back());
    }

    private void renderUpgradeNode(Inventory inv, CitySnapshot city, CityUpgradeService service,
                                   CityUpgradeId id, int slot, Material material, String name, String effect) {
        CityUpgradeView definition = service == null ? null : service.upgrade(id).orElse(null);
        boolean owned = city.upgrades().contains(id);
        if (definition == null) {
            inv.setItem(slot, button(Material.BARRIER, name, effect, "Улучшение не настроено."));
            return;
        }
        String status = owned ? "Улучшение куплено" : !definition.enabled() ? "Отключено в конфигурации"
                : city.stage().ordinal() < definition.minimumStage().ordinal()
                ? "Откроется на этапе: " + definition.minimumStage().displayName()
                : !city.upgrades().containsAll(definition.prerequisites()) ? "Сначала купите предварительное улучшение"
                : "Нажмите, чтобы купить";
        inv.setItem(slot, button(owned ? Material.ENCHANTED_BOOK : material, name, effect,
                "Минимальный этап: " + definition.minimumStage().displayName(),
                "Стоимость: " + money(definition.price()), status));
    }

    private void renderUpgradeConfirm(Inventory inv, UUID playerId, boolean preview) {
        PendingUpgrade pending = pendingUpgrades.get(playerId);
        CityUpgradeService service = cities.upgradeService().orElse(null);
        CityUpgradeView definition = pending == null || service == null ? null
                : service.upgrade(pending.upgradeId).orElse(null);
        if (preview || pending == null || definition == null) {
            inv.setItem(13, button(Material.BARRIER, "Подтверждение недоступно", "Откройте улучшения настоящего города."));
            inv.setItem(45, back());
            return;
        }
        CitySnapshot city = cityViews.get(playerId);
        if (city == null) {
            inv.setItem(13, button(Material.BARRIER, "Город не найден", "Откройте книгу управления заново."));
            inv.setItem(45, back());
            return;
        }
        boolean warding = pending.upgradeId == CityUpgradeId.CITY_WARDING;
        inv.setItem(13, button(warding ? Material.SHIELD : Material.IRON_CHESTPLATE,
                warding ? "Построить городской щит?" : "Нанять городскую стражу?",
                warding ? "Жители получат защиту от PvP в чанках города."
                        : "Враждебные мобы не смогут ранить жителей на земле города.",
                "Требуется этап: " + definition.minimumStage().displayName(),
                "Стоимость: " + money(definition.price()), "Списание — из городской казны."));
        inv.setItem(29, button(Material.LIME_CONCRETE, "Подтвердить покупку", "Эффект включится после оплаты."));
        inv.setItem(33, button(Material.RED_CONCRETE, "Отмена", "Вернуться к улучшениям."));
        inv.setItem(45, back());
    }

    private void renderTreasury(Inventory inv, CitySnapshot c, UUID playerId) {
        inv.setItem(13, button(Material.GOLD_BLOCK, "Баланс города", money(c.treasury())));
        List<CityTreasuryEntry> entries = treasuryHistory.getOrDefault(playerId, List.of());
        boolean canViewHistory = c.residents().stream().filter(resident -> resident.playerId().equals(playerId))
                .map(resident -> CityRole.fromStorage(resident.role()).allows(ru.servermine.cities.api.CityPermission.VIEW_TREASURY))
                .findFirst().orElse(false);
        int[] slots = {19, 20, 21, 22, 23};
        for (int i = 0; i < Math.min(slots.length, entries.size()); i++) {
            CityTreasuryEntry entry = entries.get(i);
            String title = entry.amount() > 0 ? "+" + money(entry.amount()) : "−" + money(Math.abs(entry.amount()));
            inv.setItem(slots[i], button(entry.amount() > 0 ? Material.EMERALD : Material.GOLD_NUGGET,
                    title, treasuryReason(entry.reason()), "Баланс после: " + money(entry.balanceAfter()),
                    entry.occurredAt().atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))));
        }
        if (entries.isEmpty()) inv.setItem(21, button(canViewHistory ? Material.BOOK : Material.BARRIER,
                "История операций", canViewHistory ? "Операций пока нет." : "Доступно Казначею, Заместителю и Правителю."));
        inv.setItem(29, button(Material.EMERALD, "Пополнить казну", "Команда: /smcities treasury deposit <сумма>"));
        inv.setItem(38, button(canViewHistory ? Material.BOOK : Material.BARRIER, "История операций",
                canViewHistory ? "Показать последние записи: /smcities treasury history" : "Нужна должность Казначея или выше."));
        boolean canTransfer = c.residents().stream().filter(resident -> resident.playerId().equals(playerId))
                .map(resident -> CityRole.fromStorage(resident.role()).allows(CityPermission.TRANSFER_TREASURY)).findFirst().orElse(false);
        CityDiplomacySnapshot snapshot = diplomacySnapshots.get(playerId);
        boolean hasTrade = snapshot != null && snapshot.treaties().stream().anyMatch(t -> t.type() == CityTreatyType.TRADE
                && t.state() == CityTreatyState.ACTIVE && t.expiresAt().isAfter(Instant.now()));
        if (canTransfer && hasTrade) inv.setItem(31, button(Material.GOLD_INGOT, "Перевести в другой город",
                "Требуется действующий торговый договор.", "Лимит: " + money(Math.max(1L, plugin.getConfig().getLong("diplomacy.maximum-transfer-amount", 100000)))));
        inv.setItem(45, back());
    }

    private void renderTreasuryTransferTargets(Inventory inv, UUID playerId) {
        List<CityDiplomacySnapshot.CitySummary> targets = treasuryTransferTargets.getOrDefault(playerId, List.of());
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        for (int i = 0; i < Math.min(slots.length, targets.size()); i++) {
            var city = targets.get(i);
            inv.setItem(slots[i], button(Material.CHEST, city.name(), "Этап: " + city.stage().displayName(), "Нажмите, чтобы выбрать город"));
        }
        if (targets.isEmpty()) inv.setItem(22, button(Material.BARRIER, "Нет доступных городов",
                "Нужен действующий торговый договор с другим городом."));
        inv.setItem(45, back());
    }

    private void renderTreasuryTransferConfirm(Inventory inv, UUID playerId, boolean preview) {
        PendingTreasuryTransfer pending = pendingTreasuryTransfers.get(playerId);
        CityDiplomacySnapshot snapshot = diplomacySnapshots.get(playerId);
        var target = snapshot == null || pending == null ? null : snapshot.otherCities().stream()
                .filter(city -> city.id().equals(pending.targetCityId)).findFirst().orElse(null);
        if (preview || pending == null || target == null) {
            inv.setItem(13, button(Material.BARRIER, "Перевод недоступен", "Откройте казну и выберите город."));
            inv.setItem(45, back()); return;
        }
        inv.setItem(13, button(Material.GOLD_INGOT, "Перевести в «" + target.name() + "»?",
                "Сумма: " + money(pending.amount), "Будет списана из казны вашего города.",
                "Обе казны получат запись в истории."));
        inv.setItem(29, button(Material.LIME_CONCRETE, "Подтвердить перевод"));
        inv.setItem(33, button(Material.RED_CONCRETE, "Отмена"));
        inv.setItem(45, back());
    }

    private void renderMarket(Inventory inv, CitySnapshot city, UUID playerId) {
        List<CityMarketListing> listings = marketListings.getOrDefault(playerId, List.of());
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        for (int i = 0; i < Math.min(slots.length, listings.size()); i++) {
            CityMarketListing listing = listings.get(i);
            ItemStack item = marketItem(listing.itemData());
            if (item == null) item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(listing.itemName(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Город: " + listing.cityName(), NamedTextColor.GRAY),
                    Component.text("Цена за товар: " + money(listing.price()), NamedTextColor.YELLOW),
                    Component.text("Покупка со сборами: " + money(marketTotal(listing.price())), NamedTextColor.GRAY),
                    Component.text("Нажмите для подтверждения", NamedTextColor.GRAY)));
            item.setItemMeta(meta); inv.setItem(slots[i], item);
        }
        if (listings.isEmpty()) inv.setItem(22, button(Material.MAP, "Нет объявлений",
                "Откройте торговый договор с другим городом.", "Затем здесь появятся его товары."));
        boolean canManage = city.residents().stream().filter(r -> r.playerId().equals(playerId))
                .map(r -> CityRole.fromStorage(r.role()).allows(CityPermission.MANAGE_MARKET)).findFirst().orElse(false);
        inv.setItem(49, button(canManage ? Material.EMERALD : Material.BARRIER,
                canManage ? "Выставить предмет" : "Нет права выставлять товары",
                canManage ? "Выставит весь предмет из основной руки." : "Нужно быть Правителем, Заместителем или Казначеем."));
        int deliveries = marketDeliveries.getOrDefault(playerId, List.of()).size();
        inv.setItem(50, button(deliveries > 0 ? Material.CHEST_MINECART : Material.CHEST,
                "Доставка товаров: " + deliveries, deliveries > 0 ? "Нажмите, чтобы получить покупки." : "Неполученных покупок нет."));
        inv.setItem(48, button(Material.WRITABLE_BOOK, "Мои объявления", "Отменить продажу и вернуть товар в доставку."));
        inv.setItem(45, back());
    }

    private void renderMyMarketListings(Inventory inv, UUID playerId) {
        List<CityMarketListing> listings = myMarketListings.getOrDefault(playerId, List.of());
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        for (int i = 0; i < Math.min(slots.length, listings.size()); i++) {
            CityMarketListing listing = listings.get(i);
            ItemStack item = marketItem(listing.itemData()); if (item == null) item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(listing.itemName(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Цена: " + money(listing.price()), NamedTextColor.YELLOW),
                    Component.text("Нажмите, чтобы отменить объявление", NamedTextColor.GRAY)));
            item.setItemMeta(meta); inv.setItem(slots[i], item);
        }
        if (listings.isEmpty()) inv.setItem(22, button(Material.MAP, "Нет активных объявлений"));
        inv.setItem(48, button(Material.ARROW, "К объявлениям других городов")); inv.setItem(45, back());
    }

    private void renderMarketConfirm(Inventory inv, UUID playerId) {
        CityMarketListing listing = selectedMarketListing.get(playerId);
        if (listing == null) { inv.setItem(13, button(Material.BARRIER, "Объявление недоступно")); inv.setItem(45, back()); return; }
        ItemStack item = marketItem(listing.itemData());
        inv.setItem(13, button(item == null ? Material.BARRIER : item.getType(), "Купить у города «" + listing.cityName() + "»?",
                listing.itemName(), "Цена: " + money(listing.price()), "Сбор: " + money(marketFee(listing.price())),
                "Всего из казны: " + money(marketTotal(listing.price())), "Предмет поступит в доставку."));
        inv.setItem(29, button(Material.LIME_CONCRETE, "Подтвердить покупку"));
        inv.setItem(33, button(Material.RED_CONCRETE, "Отмена"));
        inv.setItem(45, back());
    }

    private void renderMarketCancelConfirm(Inventory inv, UUID playerId) {
        CityMarketListing listing = selectedMarketListing.get(playerId);
        if (listing == null) { inv.setItem(13, button(Material.BARRIER, "Объявление недоступно")); inv.setItem(45, back()); return; }
        inv.setItem(13, button(Material.PAPER, "Отменить объявление?", listing.itemName(),
                "Товар вернётся в раздел доставки."));
        inv.setItem(29, button(Material.LIME_CONCRETE, "Подтвердить отмену"));
        inv.setItem(33, button(Material.RED_CONCRETE, "Назад")); inv.setItem(45, back());
    }

    private void renderMarketCreateConfirm(Inventory inv, UUID playerId) {
        PendingMarketListing pending = pendingMarketListings.get(playerId);
        if (pending == null || pending.price <= 0) { inv.setItem(13, button(Material.BARRIER, "Объявление недоступно")); inv.setItem(45, back()); return; }
        inv.setItem(13, button(pending.item.getType(), "Выставить на рынок?", "Товаров в стеке: " + pending.item.getAmount(),
                "Цена за весь стек: " + money(pending.price), "Ваш город получит цену и сбор с продажи."));
        inv.setItem(29, button(Material.LIME_CONCRETE, "Подтвердить объявление"));
        inv.setItem(33, button(Material.RED_CONCRETE, "Отмена")); inv.setItem(45, back());
    }

    private long marketFee(long price) {
        int basisPoints = Math.max(0, Math.min(2_000, plugin.getConfig().getInt("market.fee-basis-points", 500)));
        return java.math.BigInteger.valueOf(price).multiply(java.math.BigInteger.valueOf(basisPoints))
                .add(java.math.BigInteger.valueOf(9_999)).divide(java.math.BigInteger.valueOf(10_000)).longValue();
    }
    private long marketTotal(long price) { return Math.addExact(price, marketFee(price)); }

    private ItemStack marketItem(byte[] data) {
        try {
            ItemStack item = ItemStack.deserializeBytes(data);
            if (isMarketEscrowItem(item)) {
                ItemMeta meta = item.getItemMeta(); meta.getPersistentDataContainer().remove(marketEscrowKey()); item.setItemMeta(meta);
            }
            return item;
        }
        catch (RuntimeException invalid) { return null; }
    }

    private org.bukkit.NamespacedKey marketEscrowKey() { return new org.bukkit.NamespacedKey(plugin, "market_escrow"); }

    public boolean isMarketEscrowItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(marketEscrowKey(), org.bukkit.persistence.PersistentDataType.STRING);
    }

    private boolean hasEscrowMarker(Player player, String listingId) {
        for (ItemStack item : player.getInventory().getContents()) if (isMarketEscrowItem(item)
                && listingId.equals(item.getItemMeta().getPersistentDataContainer().get(marketEscrowKey(),
                org.bukkit.persistence.PersistentDataType.STRING))) return true;
        return false;
    }

    private boolean removeEscrowItem(Player player, String listingId) {
        boolean removed = false;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!isMarketEscrowItem(item) || !listingId.equals(item.getItemMeta().getPersistentDataContainer()
                    .get(marketEscrowKey(), org.bukkit.persistence.PersistentDataType.STRING))) continue;
            player.getInventory().setItem(slot, null); removed = true;
        }
        if (removed) player.saveData();
        return removed;
    }

    public void reconcileMarketListings(Player player) {
        var market = cities.marketService().orElse(null);
        if (market == null || !market.isReady()) return;
        UUID sellerId = player.getUniqueId();
        market.pendingListings(sellerId).whenComplete((pending, error) -> {
            if (error != null || pending == null || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                java.util.Set<String> pendingIds = pending.stream().map(listing -> listing.id().toString())
                        .collect(java.util.stream.Collectors.toSet());
                boolean cleaned = false;
                for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                    ItemStack item = player.getInventory().getItem(slot);
                    if (!isMarketEscrowItem(item)) continue;
                    String id = item.getItemMeta().getPersistentDataContainer().get(marketEscrowKey(),
                            org.bukkit.persistence.PersistentDataType.STRING);
                    if (id != null && pendingIds.contains(id)) continue;
                    ItemMeta meta = item.getItemMeta(); meta.getPersistentDataContainer().remove(marketEscrowKey());
                    item.setItemMeta(meta); cleaned = true;
                }
                if (cleaned) player.saveData();
                for (CityMarketListing listing : pending) {
                    String id = listing.id().toString();
                    boolean tagged = hasEscrowMarker(player, id);
                    if (tagged) removeEscrowItem(player, id);
                    if (!tagged && listing.state() == ru.servermine.cities.api.CityMarketState.PREPARING) {
                        market.abort(listing.id(), sellerId);
                    } else {
                        market.markRemoving(listing.id(), sellerId).thenCompose(marked -> marked
                                ? market.activate(listing.id(), sellerId)
                                : java.util.concurrent.CompletableFuture.completedFuture(false));
                    }
                }
            });
        });
    }

    private String treasuryReason(String reason) {
        if (reason.equals("DEPOSIT")) return "Пополнение казны";
        if (reason.equals("CITY_TAX")) return "Городской налог";
        if (reason.startsWith("CHUNK_CLAIM:")) return "Покупка городского чанка";
        if (reason.startsWith("STAGE_PROMOTION:")) return "Развитие города";
        return reason;
    }

    private void renderResidents(Inventory inv, CitySnapshot c) {
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int i = 0;
        for (CitySnapshot.ResidentEntry resident : c.residents()) {
            if (i >= slots.length) break;
            inv.setItem(slots[i++], button(Material.PLAYER_HEAD, resident.name(), roleName(resident.role()), "ЛКМ: должность и действия участника"));
        }
        inv.setItem(40, button(Material.LIME_DYE, "Пригласить игрока", "Команда: /smcities invite <игрок>"));
        inv.setItem(41, button(Material.PAPER, "Принять приглашение", "Команда: /smcities accept"));
        inv.setItem(45, back());
    }

    private void renderMemberKickConfirm(Inventory inv, UUID playerId, boolean preview) {
        PendingMemberKick pending = pendingMemberKicks.get(playerId);
        String name = pending == null ? "Участник" : pending.targetName;
        inv.setItem(13, button(Material.PLAYER_HEAD, name, "Исключить из города?"));
        inv.setItem(29, button(Material.REDSTONE_BLOCK, "Исключить", "Участник потеряет доступ к территории."));
        inv.setItem(33, button(Material.BARRIER, "Отмена", "Вернуться к составу города."));
        inv.setItem(45, back());
    }

    private void renderMemberRoleManagement(Inventory inv, CitySnapshot city, UUID playerId, boolean preview) {
        PendingRoleTarget pending = pendingRoleTargets.get(playerId);
        if (pending == null) {
            inv.setItem(13, button(Material.BARRIER, "Участник не выбран", "Откройте список жителей заново."));
            inv.setItem(45, back());
            return;
        }
        inv.setItem(4, button(Material.PLAYER_HEAD, pending.targetName,
                "Текущая должность: " + roleName(pending.currentRole)));
        CityRole actorRole = city.residents().stream().filter(resident -> resident.playerId().equals(playerId))
                .map(resident -> CityRole.fromStorage(resident.role())).findFirst().orElse(CityRole.MEMBER);
        java.util.Set<CityRole> assignable = preview ? java.util.Set.of() : CityRole.assignableBy(actorRole);
        for (var option : List.of(
                new RoleOption(20, CityRole.DEPUTY, Material.GREEN_BANNER, "Приглашения, жители, территория и развитие"),
                new RoleOption(22, CityRole.TREASURER, Material.GOLD_INGOT, "Просмотр казны и внесение монет"),
                new RoleOption(24, CityRole.BUILDER, Material.BRICKS, "Строительство и покупка территории"),
                new RoleOption(29, CityRole.MEMBER, Material.PLAYER_HEAD, "Строительство и внесение монет"))) {
            boolean allowed = assignable.contains(option.role);
            String current = pending.currentRole.equals(option.role.name()) ? " · сейчас" : "";
            inv.setItem(option.slot, button(allowed ? option.material : Material.GRAY_DYE,
                    option.role.displayName() + current,
                    allowed ? option.description : "Недоступно для вашей должности"));
        }
        inv.setItem(33, button(Material.IRON_DOOR, "Действия с участником", "Исключить из города"));
        inv.setItem(45, back());
    }

    private void renderManagement(Inventory inv, CitySnapshot c) {
        inv.setItem(10, button(Material.BOOK, "Информация о городе", c.name(), "Этап: " + c.stage().displayName()));
        inv.setItem(19, button(Material.MAP, "Настройки территории", "Параметры городских земель"));
        inv.setItem(28, button(Material.TRIPWIRE_HOOK, "Должности и права", "Роли и разрешения жителей"));
        inv.setItem(37, button(Material.GOLD_INGOT, "Налоги и сборы", "Ставка, онлайн-сбор и поступления в казну"));
        inv.setItem(46, button(Material.PAPER, "Законы города", "Локальные правила города"));
        inv.setItem(32, button(Material.BLUE_BANNER, "Внешний вид", "Герб и цвет города"));
        inv.setItem(41, button(Material.EXPERIENCE_BOTTLE, "Развитие города",
                "Текущий этап: " + c.stage().displayName(),
                "Лимит территории: " + c.territoryLimit() + " чанков"));
        inv.setItem(45, back());
    }

    private void renderCityLaws(Inventory inv, CitySnapshot city, UUID playerId) {
        CityRole role = city.residents().stream().filter(resident -> resident.playerId().equals(playerId))
                .map(resident -> CityRole.fromStorage(resident.role())).findFirst().orElse(null);
        boolean editable = role != null && role.allows(CityPermission.MANAGE_LAWS);
        CityLaws laws = city.laws();
        inv.setItem(4, button(Material.WRITABLE_BOOK, "Законы города: " + city.name(),
                editable ? "Нажмите на правило, чтобы изменить его." : "Изменять законы могут Правитель и Заместитель.",
                "Настройки применяются сразу."));
        lawButton(inv, 20, Material.IRON_SWORD, "PvP на территории", laws.pvpAllowed(), editable);
        lawButton(inv, 22, Material.LEVER, "Взаимодействие чужих игроков", laws.outsiderInteractionsAllowed(), editable);
        lawButton(inv, 24, Material.FLINT_AND_STEEL, "Огонь", laws.fireAllowed(), editable);
        lawButton(inv, 31, Material.TNT, "Взрывы", laws.explosionsAllowed(), editable);
        inv.setItem(45, back());
    }

    private void lawButton(Inventory inv, int slot, Material material, String label, boolean enabled, boolean editable) {
        inv.setItem(slot, button(enabled ? material : Material.BARRIER, label + ": " + (enabled ? "разрешено" : "запрещено"),
                editable ? "Нажмите, чтобы переключить." : "Нет прав на изменение."));
    }

    private void renderCityTax(Inventory inv, CitySnapshot city, UUID playerId) {
        CityRole role = city.residents().stream().filter(resident -> resident.playerId().equals(playerId))
                .map(resident -> CityRole.fromStorage(resident.role())).findFirst().orElse(null);
        boolean editable = role != null && role.allows(CityPermission.MANAGE_TAXES);
        CityTaxPolicy tax = city.taxPolicy();
        inv.setItem(4, button(Material.GOLD_INGOT, "Налоги города: " + city.name(),
                "Фиксированный сбор с каждого жителя раз в 24 часа.",
                "Взимаются только у игроков онлайн в момент сбора.", "Офлайн-жителям долг не начисляется.",
                "Если монет не хватает, сбор пропускается."));
        inv.setItem(20, button(Material.RED_DYE, "Уменьшить на 25", "Ставка не станет ниже нуля."));
        inv.setItem(22, button(tax.enabled() ? Material.GOLD_BLOCK : Material.GOLD_NUGGET,
                "Ставка: " + money(tax.dailyAmount()) + " в день", tax.enabled() ? "Налог включён." : "Налог выключен."));
        inv.setItem(24, button(Material.LIME_DYE, "Увеличить на 25", "Максимум: " + money(100_000)));
        inv.setItem(31, button(tax.enabled() ? Material.REDSTONE_BLOCK
                        : (tax.dailyAmount() > 0 ? Material.LIME_CONCRETE : Material.BARRIER),
                tax.enabled() ? "Выключить налог" : (tax.dailyAmount() > 0 ? "Включить налог" : "Сначала задайте ставку"),
                tax.enabled() ? "При повторном включении следующий сбор будет через 24 часа."
                        : "Первый сбор будет не раньше, чем через 24 часа после включения.",
                editable ? "Нажмите, чтобы переключить." : "Изменять налог могут Правитель и Заместитель."));
        inv.setItem(45, back());
    }

    private void renderDiplomacy(Inventory inv, CitySnapshot c, UUID playerId) {
        CityDiplomacySnapshot data = diplomacySnapshots.get(playerId);
        inv.setItem(4, button(Material.WRITABLE_BOOK, "Соглашения города",
                "Левый клик по входящему предложению — принять", "Правый клик — отклонить",
                "Нажатие по действующему договору — прекратить"));
        if (data != null) {
            List<CityTreatyView> treaties = data.treaties();
            for (int i = 0; i < Math.min(30, treaties.size()); i++) {
                CityTreatyView treaty = treaties.get(i);
                String other = treaty.proposingCityId().equals(cityIds.get(playerId))
                        ? treaty.targetCityName() : treaty.proposingCityName();
                String status = treaty.state() == CityTreatyState.PENDING ? "Ожидает решения"
                        : treaty.state() == CityTreatyState.ACTIVE ? "Действует до " + treaty.expiresAt().toString().substring(0, 10)
                        : treaty.state().name();
                inv.setItem(10 + i, button(treaty.state() == CityTreatyState.ACTIVE ? Material.LIME_BANNER : Material.PAPER,
                        treaty.type().displayName() + " · " + other, status,
                        treaty.state() == CityTreatyState.PENDING && !treaty.targetCityId().equals(cityIds.get(playerId))
                                ? "Ваше предложение — нажмите, чтобы отозвать" : "Нажмите, чтобы выполнить действие"));
            }
            String[] eventLore = new String[1 + Math.min(3, data.events().size())];
            eventLore[0] = "Последние события: " + data.events().size();
            for (int i = 1; i < eventLore.length; i++) {
                var event = data.events().get(i - 1);
                eventLore[i] = event.event() + " · " + event.firstCity() + " / " + event.secondCity();
            }
            inv.setItem(40, button(Material.BOOK, "Журнал дипломатии", eventLore));
            if (data.treaties().isEmpty()) inv.setItem(22, button(Material.MAP, "Пока нет соглашений", "Предложите договор другому городу."));
        }
        inv.setItem(49, button(Material.WRITABLE_BOOK, "Новое предложение", "Выбрать город и условия договора"));
        inv.setItem(45, back());
    }

    private void renderDiplomacyTargets(Inventory inv, UUID playerId) {
        List<ru.servermine.cities.api.CityDiplomacySnapshot.CitySummary> targets = diplomacyTargets.getOrDefault(playerId, List.of());
        for (int i = 0; i < Math.min(34, targets.size()); i++) {
            var city = targets.get(i);
            inv.setItem(10 + i, button(Material.MAP, city.name(), "Этап: " + city.stage().displayName(), "Нажмите, чтобы составить предложение"));
        }
        if (targets.isEmpty()) inv.setItem(22, button(Material.BARRIER, "Других городов пока нет", "Для дипломатии нужно основание другого игрока."));
        inv.setItem(45, back());
    }

    private void renderDiplomacyCreate(Inventory inv, UUID playerId) {
        CityDiplomacySnapshot data = diplomacySnapshots.get(playerId);
        UUID targetId = diplomacySelectedTarget.get(playerId);
        var target = data == null || targetId == null ? null : data.otherCities().stream().filter(c -> c.id().equals(targetId)).findFirst().orElse(null);
        inv.setItem(13, button(Material.MAP, target == null ? "Город не выбран" : "Договор с «" + target.name() + "»"));
        CityTreatyType selected = diplomacySelectedType.getOrDefault(playerId, CityTreatyType.ALLIANCE);
        inv.setItem(20, button(Material.LIME_BANNER, "Союз", "Совместные дружеские отношения", selected == CityTreatyType.ALLIANCE ? "Выбрано" : ""));
        inv.setItem(22, button(Material.SHIELD, "Пакт о ненападении", "Соглашение о мире", selected == CityTreatyType.NON_AGGRESSION ? "Выбрано" : ""));
        inv.setItem(24, button(Material.EMERALD, "Торговый договор", "Соглашение о торговле", selected == CityTreatyType.TRADE ? "Выбрано" : ""));
        int duration = diplomacyDuration.getOrDefault(playerId, 30);
        inv.setItem(29, button(Material.CLOCK, "7 дней", duration == 7 ? "Выбран срок" : ""));
        inv.setItem(31, button(Material.CLOCK, "30 дней", duration == 30 ? "Выбран срок" : ""));
        inv.setItem(33, button(Material.CLOCK, "90 дней", duration == 90 ? "Выбран срок" : ""));
        inv.setItem(40, button(Material.LIME_DYE, "Отправить предложение", "Вторая сторона должна его принять."));
        inv.setItem(45, back());
    }

    public void handleClick(Player player, MenuHolder holder, int rawSlot) {
        handleClick(player, holder, rawSlot, false);
    }

    public void handleClick(Player player, MenuHolder holder, int rawSlot, boolean rightClick) {
        MenuSession session = holder.session();
        if (!session.playerId().equals(player.getUniqueId()) || player.getOpenInventory().getTopInventory().getHolder() != holder) return;
        if (session.openedAt().plusSeconds(300).isBefore(java.time.Instant.now())) {
            player.closeInventory();
            return;
        }
        boolean visualBook = session.menuType() == MenuType.MAIN && !session.preview()
                && plugin.getConfig().getBoolean("visual.use-pixel-backgrounds", false);
        if (visualBook) {
            rawSlot = MainMenuLayout.actionSlot(rawSlot);
            if (rawSlot < 0) return;
        }
        if (session.menuType() == MenuType.ADMIN_TOOLS || session.menuType() == MenuType.ADMIN_TARGET
                || session.menuType() == MenuType.ADMIN_RECOVERY || session.menuType() == MenuType.ADMIN_RECOVERY_DETAIL) {
            handleAdminClick(player, session.menuType(), rawSlot);
            return;
        }
        if (session.menuType() == MenuType.DIPLOMACY || session.menuType() == MenuType.DIPLOMACY_TARGETS
                || session.menuType() == MenuType.DIPLOMACY_CREATE) {
            handleDiplomacyClick(player, session, rawSlot, rightClick);
            return;
        }
        if (session.menuType() == MenuType.TREASURY_TRANSFER_TARGETS || session.menuType() == MenuType.TREASURY_TRANSFER_CONFIRM) {
            handleTreasuryTransferClick(player, session.menuType(), rawSlot);
            return;
        }
        if (session.menuType() == MenuType.MARKET || session.menuType() == MenuType.MARKET_CONFIRM
                || session.menuType() == MenuType.MARKET_CREATE_CONFIRM || session.menuType() == MenuType.MARKET_MY_LISTINGS
                || session.menuType() == MenuType.MARKET_CANCEL_CONFIRM) {
            handleMarketClick(player, session.menuType(), rawSlot);
            return;
        }
        MenuType target = null;
        if (rawSlot == 45) target = switch (session.menuType()) {
            case PURCHASE -> MenuType.TERRITORY;
            case PURCHASE_CONFIRM -> MenuType.PURCHASE;
            case UPGRADE_CONFIRM -> MenuType.UPGRADES;
            case MEMBER_ROLE_MANAGEMENT -> MenuType.RESIDENTS;
            case CITY_LAWS -> MenuType.MANAGEMENT;
            case CITY_TAX -> MenuType.MANAGEMENT;
            default -> MenuType.MAIN;
        };
        else if (session.menuType() == MenuType.MAIN && visualBook) target = switch (rawSlot) {
            case 20 -> MenuType.TERRITORY; case 24 -> MenuType.RESIDENTS;
            case 29 -> MenuType.TREASURY; case 33 -> MenuType.MANAGEMENT;
            case 38 -> MenuType.DIPLOMACY; case 42 -> MenuType.CITY_LAWS;
            case 47 -> MenuType.MARKET; case 51 -> player.isOp() ? MenuType.ADMIN_TOOLS : null; default -> null;
        };
        else if (session.menuType() == MenuType.MAIN) target = switch (rawSlot) {
            case 20 -> MenuType.TERRITORY; case 24 -> MenuType.RESIDENTS;
            case 29 -> MenuType.UPGRADES; case 33 -> MenuType.MANAGEMENT;
            case 38 -> MenuType.TREASURY; case 42 -> MenuType.DIPLOMACY;
            case 49 -> MenuType.MARKET; case 47 -> player.isOp() ? MenuType.ADMIN_TOOLS : null; default -> null;
        };
        else if (session.menuType() == MenuType.TERRITORY && rawSlot == 49) {
            if (session.preview()) openPreview(player, MenuType.PURCHASE); else openPurchaseTargets(player);
            return;
        }
        else if (session.menuType() == MenuType.TERRITORY && rawSlot == 47) {
            if (session.preview()) {
                player.sendMessage(Component.text("Режим обзора доступен только для настоящего города.", NamedTextColor.YELLOW));
            } else {
                CitySnapshot city = cityViews.get(player.getUniqueId());
                TerritoryOrigin origin = territoryOrigins.get(player.getUniqueId());
                UUID cityId = cityIds.get(player.getUniqueId());
                if (city == null || origin == null || cityId == null) {
                    player.sendMessage(Component.text("Не удалось определить территорию города. Откройте раздел заново.", NamedTextColor.RED));
                } else {
                    territoryOverview.start(player, cityId, session.cityRevision(), city.chunks(),
                            purchaseTargets.getOrDefault(player.getUniqueId(), List.of()),
                            origin.worldId(), origin.centerX(), origin.centerZ());
                }
            }
            return;
        }
        else if (session.menuType() == MenuType.PURCHASE && rawSlot == 42) target = MenuType.TERRITORY;
        else if (session.menuType() == MenuType.MANAGEMENT && rawSlot == 28) target = MenuType.RESIDENTS;
        else if (session.menuType() == MenuType.MANAGEMENT && rawSlot == 41) target = MenuType.PROGRESSION;
        else if (session.menuType() == MenuType.MANAGEMENT && rawSlot == 46) target = MenuType.CITY_LAWS;
        else if (session.menuType() == MenuType.MANAGEMENT && rawSlot == 37) target = MenuType.CITY_TAX;
        else if (session.menuType() == MenuType.CITY_LAWS && lawSlot(rawSlot)) {
            toggleCityLaw(player, session, rawSlot);
            return;
        } else if (session.menuType() == MenuType.CITY_TAX && (rawSlot == 20 || rawSlot == 24 || rawSlot == 31)) {
            updateCityTax(player, session, rawSlot);
            return;
        }
        else if (session.menuType() == MenuType.RESIDENTS && residentSlot(rawSlot)) {
            beginMemberRoleManagement(player, session, rawSlot);
            return;
        } else if (session.menuType() == MenuType.MEMBER_ROLE_MANAGEMENT && roleSlot(rawSlot)) {
            assignSelectedRole(player, rawSlot);
            return;
        } else if (session.menuType() == MenuType.MEMBER_ROLE_MANAGEMENT && rawSlot == 33) {
            beginKickFromRoleManagement(player);
            return;
        } else if (session.menuType() == MenuType.MEMBER_KICK_CONFIRM && rawSlot == 29) {
            confirmMemberKick(player);
            return;
        } else if (session.menuType() == MenuType.MEMBER_KICK_CONFIRM && rawSlot == 33) {
            pendingMemberKicks.remove(player.getUniqueId());
            open(player, MenuType.RESIDENTS);
            return;
        }
        if (target != null) {
            if (session.preview()) openPreview(player, target); else open(player, target);
        } else if (session.menuType() == MenuType.FOUNDING && rawSlot == 31) {
            beginFounding(player);
        } else if (session.menuType() == MenuType.FOUNDING && rawSlot == 40 && player.isOp()) {
            open(player, MenuType.ADMIN_TOOLS);
        } else if (session.menuType() == MenuType.FOUNDING_CONFIRM && rawSlot == 29) {
            confirmFounding(player);
        } else if (session.menuType() == MenuType.FOUNDING_CONFIRM && rawSlot == 33) {
            pendingFounding.remove(player.getUniqueId());
            showFounding(player, MenuType.FOUNDING, null);
        } else if (session.menuType() == MenuType.PROGRESSION && rawSlot == 31) {
            beginPromotion(player, session);
        } else if (session.menuType() == MenuType.UPGRADES && rawSlot == 20) {
            beginUpgrade(player, session, CityUpgradeId.CITY_WARDING);
        } else if (session.menuType() == MenuType.UPGRADES && rawSlot == 24) {
            beginUpgrade(player, session, CityUpgradeId.CITY_GUARDS);
        } else if (session.menuType() == MenuType.UPGRADE_CONFIRM && rawSlot == 29) {
            confirmUpgrade(player);
        } else if (session.menuType() == MenuType.UPGRADE_CONFIRM && rawSlot == 33) {
            pendingUpgrades.remove(player.getUniqueId());
            open(player, MenuType.UPGRADES);
        } else if (session.menuType() == MenuType.PROMOTION_CONFIRM && rawSlot == 29) {
            confirmPromotion(player);
        } else if (session.menuType() == MenuType.PROMOTION_CONFIRM && rawSlot == 33) {
            pendingPromotions.remove(player.getUniqueId());
            open(player, MenuType.PROGRESSION);
        } else if (session.menuType() == MenuType.PURCHASE && (rawSlot == 48 || rawSlot == 50)) {
            int page = purchasePages.getOrDefault(player.getUniqueId(), 0);
            int maximum = Math.max(0, (purchaseTargets.getOrDefault(player.getUniqueId(), List.of()).size() - 1)
                    / purchaseSlots().length);
            purchasePages.put(player.getUniqueId(), Math.max(0, Math.min(maximum, page + (rawSlot == 50 ? 1 : -1))));
            open(player, MenuType.PURCHASE);
        } else if (session.menuType() == MenuType.PURCHASE && candidateSlot(rawSlot)) {
            beginClaim(player, session, rawSlot);
        } else if (session.menuType() == MenuType.PURCHASE_CONFIRM && rawSlot == 29) {
            confirmClaim(player);
        } else if (session.menuType() == MenuType.PURCHASE_CONFIRM && rawSlot == 33) {
            pendingClaims.remove(player.getUniqueId());
            open(player, MenuType.PURCHASE);
        } else if (session.menuType() == MenuType.RESIDENTS && rawSlot == 40) {
            player.sendMessage(Component.text("Пригласить онлайн-игрока: /smcities invite <игрок>", NamedTextColor.YELLOW));
        } else if (session.menuType() == MenuType.RESIDENTS && rawSlot == 41) {
            player.sendMessage(Component.text("Принять приглашение: /smcities accept", NamedTextColor.YELLOW));
        } else if (session.menuType() == MenuType.TREASURY && rawSlot == 29) {
            player.sendMessage(Component.text("Пополнить казну: /smcities treasury deposit <сумма>", NamedTextColor.YELLOW));
        } else if (session.menuType() == MenuType.TREASURY && rawSlot == 31) {
            openTreasuryTransferTargets(player);
        } else if (session.menuType() == MenuType.TREASURY && rawSlot == 38) {
            CitySnapshot city = cityViews.get(player.getUniqueId());
            boolean canViewHistory = city != null && city.residents().stream()
                    .filter(resident -> resident.playerId().equals(player.getUniqueId()))
                    .map(resident -> CityRole.fromStorage(resident.role()).allows(ru.servermine.cities.api.CityPermission.VIEW_TREASURY))
                    .findFirst().orElse(false);
            if (!canViewHistory) {
                player.sendMessage(Component.text("Историю казны могут смотреть Казначей, Заместитель и Правитель.", NamedTextColor.RED));
                return;
            }
            List<CityTreasuryEntry> entries = treasuryHistory.getOrDefault(player.getUniqueId(), List.of());
            if (entries.isEmpty()) player.sendMessage(Component.text("Операций в казне пока нет.", NamedTextColor.YELLOW));
            else entries.forEach(entry -> player.sendMessage(Component.text(
                    (entry.amount() > 0 ? "+" : "−") + money(Math.abs(entry.amount()))
                            + " · " + treasuryReason(entry.reason()) + " · баланс " + money(entry.balanceAfter()),
                    entry.amount() > 0 ? NamedTextColor.GREEN : NamedTextColor.GOLD)));
        } else {
            player.sendMessage(Component.text("Этот раздел пока доступен только для просмотра.", NamedTextColor.YELLOW));
        }
    }

    private boolean lawSlot(int slot) { return slot == 20 || slot == 22 || slot == 24 || slot == 31; }

    private void toggleCityLaw(Player player, MenuSession session, int slot) {
        CityLawsService service = cities.lawsService().orElse(null);
        UUID cityId = cityIds.get(player.getUniqueId());
        CitySnapshot current = cityViews.get(player.getUniqueId());
        if (service == null || !service.isReady() || cityId == null || current == null) {
            player.sendMessage(Component.text("Настройки законов временно недоступны.", NamedTextColor.RED)); return;
        }
        CityRole role = current.residents().stream().filter(r -> r.playerId().equals(player.getUniqueId()))
                .map(r -> CityRole.fromStorage(r.role())).findFirst().orElse(null);
        if (role == null || !role.allows(CityPermission.MANAGE_LAWS)) {
            player.sendMessage(Component.text("Изменять законы могут только Правитель и Заместитель.", NamedTextColor.RED)); return;
        }
        CityLaws old = current.laws();
        CityLaws updated = switch (slot) {
            case 20 -> new CityLaws(!old.pvpAllowed(), old.outsiderInteractionsAllowed(), old.fireAllowed(), old.explosionsAllowed());
            case 22 -> new CityLaws(old.pvpAllowed(), !old.outsiderInteractionsAllowed(), old.fireAllowed(), old.explosionsAllowed());
            case 24 -> new CityLaws(old.pvpAllowed(), old.outsiderInteractionsAllowed(), !old.fireAllowed(), old.explosionsAllowed());
            case 31 -> new CityLaws(old.pvpAllowed(), old.outsiderInteractionsAllowed(), old.fireAllowed(), !old.explosionsAllowed());
            default -> old;
        };
        service.update(new CityLawsRequest(cityId, player.getUniqueId(), session.cityRevision(), updated))
                .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null || result == null) {
                        player.sendMessage(Component.text("Не удалось сохранить закон города.", NamedTextColor.RED)); return;
                    }
                    if (result.code() == CityLawsCode.UPDATED) {
                        player.sendMessage(Component.text("Закон города сохранён.", NamedTextColor.GREEN));
                        open(player, MenuType.CITY_LAWS);
                    } else if (result.code() == CityLawsCode.STALE_CITY) {
                        player.sendMessage(Component.text("Город изменился. Обновите раздел и повторите действие.", NamedTextColor.YELLOW));
                        open(player, MenuType.CITY_LAWS);
                    } else player.sendMessage(Component.text("Нет прав или город недоступен.", NamedTextColor.RED));
                }));
    }

    private void updateCityTax(Player player, MenuSession session, int slot) {
        CityTaxService service = cities.taxService().orElse(null);
        UUID cityId = cityIds.get(player.getUniqueId());
        CitySnapshot current = cityViews.get(player.getUniqueId());
        if (service == null || !service.isReady() || cityId == null || current == null) {
            player.sendMessage(Component.text("Налоговая система временно недоступна.", NamedTextColor.RED)); return;
        }
        CityRole role = current.residents().stream().filter(r -> r.playerId().equals(player.getUniqueId()))
                .map(r -> CityRole.fromStorage(r.role())).findFirst().orElse(null);
        if (role == null || !role.allows(CityPermission.MANAGE_TAXES)) {
            player.sendMessage(Component.text("Настраивать налог могут только Правитель и Заместитель.", NamedTextColor.RED)); return;
        }
        CityTaxPolicy old = current.taxPolicy();
        long amount = old.dailyAmount();
        boolean enabled = old.enabled();
        if (slot == 20) amount = Math.max(0, amount - 25);
        else if (slot == 24) amount = Math.min(100_000, amount + 25);
        else if (slot == 31) {
            if (!enabled && amount == 0) {
                player.sendMessage(Component.text("Сначала установите ставку больше нуля.", NamedTextColor.YELLOW)); return;
            }
            enabled = !enabled;
        }
        boolean requestedEnabled = enabled;
        long requestedAmount = amount;
        service.update(new CityTaxRequest(cityId, player.getUniqueId(), session.cityRevision(), requestedEnabled, requestedAmount))
                .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null || result == null) {
                        player.sendMessage(Component.text("Не удалось сохранить налоговую политику.", NamedTextColor.RED)); return;
                    }
                    if (result.code() == CityTaxCode.UPDATED) {
                        player.sendMessage(Component.text(requestedEnabled
                                ? "Налог сохранён. Первый сбор будет через 24 часа."
                                : (requestedAmount == 0 ? "Ставка установлена в 0; налог выключен." : "Настройки налога сохранены."),
                                NamedTextColor.GREEN));
                        open(player, MenuType.CITY_TAX);
                    } else if (result.code() == CityTaxCode.STALE_CITY) {
                        player.sendMessage(Component.text("Город изменился. Настройки обновлены.", NamedTextColor.YELLOW));
                        open(player, MenuType.CITY_TAX);
                    } else if (result.code() == CityTaxCode.COLLECTION_IN_PROGRESS) {
                        player.sendMessage(Component.text("Подождите завершения текущего сбора налога.", NamedTextColor.YELLOW));
                    } else player.sendMessage(Component.text("Налог не изменён: проверьте права и ставку.", NamedTextColor.RED));
                }));
    }

    private void handleAdminClick(Player admin, MenuType type, int slot) {
        if (!admin.isOp()) {
            admin.closeInventory();
            admin.sendMessage(Component.text("Админ-раздел доступен только OP.", NamedTextColor.RED));
            return;
        }
        if (type == MenuType.ADMIN_TOOLS) {
            if (slot == 45) { open(admin, MenuType.MAIN); return; }
            if (slot == 49) { showAdmin(admin, MenuType.ADMIN_TOOLS); return; }
            if (slot == 40) { openAdminRecovery(admin); return; }
            int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
            List<? extends Player> online = Bukkit.getOnlinePlayers().stream().sorted(java.util.Comparator.comparing(Player::getName,
                    String.CASE_INSENSITIVE_ORDER)).limit(slots.length).toList();
            for (int i = 0; i < Math.min(slots.length, online.size()); i++) {
                if (slots[i] == slot) {
                    adminTargets.put(admin.getUniqueId(), online.get(i).getUniqueId());
                    showAdmin(admin, MenuType.ADMIN_TARGET);
                    return;
                }
            }
            return;
        }
        if (type == MenuType.ADMIN_RECOVERY) {
            if (slot == 45) { showAdmin(admin, MenuType.ADMIN_TOOLS); return; }
            if (slot == 49) { openAdminRecovery(admin); return; }
            List<CityTreasuryRecoveryView> rows = adminRecoveryRows.getOrDefault(admin.getUniqueId(), List.of());
            int pages = Math.max(1, (rows.size() + 20) / 21);
            int page = adminRecoveryPages.getOrDefault(admin.getUniqueId(), 0);
            if (slot == 48 && page > 0) { adminRecoveryPages.put(admin.getUniqueId(), page - 1); showAdmin(admin, type); return; }
            if (slot == 50 && page + 1 < pages) { adminRecoveryPages.put(admin.getUniqueId(), page + 1); showAdmin(admin, type); return; }
            int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
            int start = page * slots.length;
            for (int i = start; i < Math.min(start + slots.length, rows.size()); i++) if (slot == slots[i - start]) {
                adminRecoverySelection.put(admin.getUniqueId(), rows.get(i).operationId());
                showAdmin(admin, MenuType.ADMIN_RECOVERY_DETAIL);
                return;
            }
            return;
        }
        if (type == MenuType.ADMIN_RECOVERY_DETAIL) {
            if (slot == 45) { openAdminRecovery(admin); return; }
            if (slot == 33) { openAdminRecovery(admin, MenuType.ADMIN_RECOVERY_DETAIL); return; }
            if (slot == 29) { reconcileAdminOperation(admin); return; }
            return;
        }
        if (slot == 45) { showAdmin(admin, MenuType.ADMIN_TOOLS); return; }
        UUID targetId = adminTargets.get(admin.getUniqueId());
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        if (target == null) {
            admin.sendMessage(Component.text("Игрок вышел. Обновите список игроков.", NamedTextColor.YELLOW));
            showAdmin(admin, MenuType.ADMIN_TOOLS);
            return;
        }
        long amount = switch (slot) { case 20 -> 10_000L; case 22 -> 100_000L; case 24 -> 1_000_000L; default -> 0L; };
        if (amount > 0) {
            repository.adminTestGrantTreasury(UUID.randomUUID(), admin.getUniqueId(), targetId, amount)
                    .whenComplete((city, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!admin.isOnline()) return;
                        if (error != null) {
                            plugin.getLogger().warning("Admin GUI test treasury grant failed: " + error);
                            admin.sendMessage(Component.text("Не удалось изменить казну.", NamedTextColor.RED));
                        } else if (city.isEmpty()) admin.sendMessage(Component.text("Игрок не состоит в городе.", NamedTextColor.RED));
                        else {
                            admin.sendMessage(Component.text("Казна «" + city.get().name() + "»: +" + money(amount)
                                    + ", баланс " + money(city.get().treasury()) + ".", NamedTextColor.GREEN));
                            plugin.getLogger().info("Admin GUI test treasury grant: actor=" + admin.getName() + ", target=" + target.getName()
                                    + ", city=" + city.get().name() + ", amount=" + amount);
                        }
                        showAdmin(admin, MenuType.ADMIN_TARGET);
                    }));
            return;
        }
        int[] stageSlots = {29,30,31,32,33};
        CityStage[] stages = CityStage.values();
        for (int i = 0; i < stageSlots.length; i++) {
            if (slot != stageSlots[i]) continue;
            CityStage stage = stages[i];
            repository.adminTestSetStage(UUID.randomUUID(), admin.getUniqueId(), targetId, stage)
                    .whenComplete((city, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!admin.isOnline()) return;
                        if (error != null) {
                            plugin.getLogger().warning("Admin GUI test stage update failed: " + error);
                            admin.sendMessage(Component.text("Не удалось изменить этап города.", NamedTextColor.RED));
                        } else if (city.isEmpty()) admin.sendMessage(Component.text("Игрок не состоит в городе.", NamedTextColor.RED));
                        else {
                            admin.sendMessage(Component.text("Этап «" + city.get().name() + "» установлен: " + stage.displayName() + ".", NamedTextColor.GREEN));
                            plugin.getLogger().info("Admin GUI test stage set: actor=" + admin.getName() + ", target=" + target.getName()
                                    + ", city=" + city.get().name() + ", stage=" + stage.name());
                        }
                        showAdmin(admin, MenuType.ADMIN_TARGET);
                    }));
            return;
        }
    }

    private void openAdminRecovery(Player admin) {
        openAdminRecovery(admin, MenuType.ADMIN_RECOVERY);
    }

    private void openAdminRecovery(Player admin, MenuType destination) {
        if (!admin.isOp()) return;
        var treasury = cities.treasuryService().orElse(null);
        if (treasury == null) {
            admin.sendMessage(Component.text("Сервис казны пока недоступен.", NamedTextColor.RED));
            return;
        }
        treasury.pendingRecovery().whenComplete((rows, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!admin.isOnline() || !admin.isOp()) return;
            if (error != null) {
                plugin.getLogger().warning("Admin treasury recovery status read failed: " + error);
                admin.sendMessage(Component.text("Не удалось прочитать состояния платежей.", NamedTextColor.RED));
                return;
            }
            adminRecoveryRows.put(admin.getUniqueId(), rows);
            if (destination == MenuType.ADMIN_RECOVERY_DETAIL
                    && rows.stream().noneMatch(row -> row.operationId().equals(adminRecoverySelection.get(admin.getUniqueId())))) {
                admin.sendMessage(Component.text("Операция уже закрыта или исчезла из списка.", NamedTextColor.GREEN));
                showAdmin(admin, MenuType.ADMIN_RECOVERY);
            } else showAdmin(admin, destination);
        }));
    }

    private void reconcileAdminOperation(Player admin) {
        UUID operationId = adminRecoverySelection.get(admin.getUniqueId());
        var treasury = cities.treasuryService().orElse(null);
        if (operationId == null || treasury == null || !admin.isOp()) return;
        admin.sendMessage(Component.text("Сверяю журналы операции " + operationId + "…", NamedTextColor.YELLOW));
        treasury.reconcile(operationId, admin.getUniqueId()).whenComplete((resolved, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!admin.isOnline() || !admin.isOp()) return;
            if (error != null) {
                plugin.getLogger().warning("Admin treasury recovery failed: actor=" + admin.getUniqueId()
                        + ", operation=" + operationId + ", error=" + error);
                admin.sendMessage(Component.text("Сверка не завершилась. Проверьте журналы сервера.", NamedTextColor.RED));
            } else if (Boolean.TRUE.equals(resolved)) {
                admin.sendMessage(Component.text("Операция согласована. Повторное списание не запускалось.", NamedTextColor.GREEN));
            } else {
                admin.sendMessage(Component.text("Операция осталась незавершённой; автоматического повтора списания не было.", NamedTextColor.YELLOW));
            }
            openAdminRecovery(admin, MenuType.ADMIN_RECOVERY_DETAIL);
        }));
    }

    private void handleMarketClick(Player player, MenuType type, int slot) {
        UUID playerId = player.getUniqueId();
        if (slot == 45) {
            selectedMarketListing.remove(playerId); pendingMarketListings.remove(playerId);
            MenuType destination = switch (type) {
                case MARKET -> MenuType.MAIN;
                case MARKET_MY_LISTINGS, MARKET_CANCEL_CONFIRM -> MenuType.MARKET;
                default -> MenuType.MARKET;
            };
            if (type == MenuType.MARKET_CANCEL_CONFIRM) destination = MenuType.MARKET_MY_LISTINGS;
            open(player, destination); return;
        }
        if ((type == MenuType.MARKET_CONFIRM || type == MenuType.MARKET_CREATE_CONFIRM
                || type == MenuType.MARKET_CANCEL_CONFIRM) && slot == 33) {
            selectedMarketListing.remove(playerId); pendingMarketListings.remove(playerId);
            open(player, type == MenuType.MARKET_CANCEL_CONFIRM ? MenuType.MARKET_MY_LISTINGS : MenuType.MARKET); return;
        }
        if (type == MenuType.MARKET) {
            if (slot == 49) { beginMarketListing(player); return; }
            if (slot == 50) { claimMarketDeliveries(player); return; }
            if (slot == 48) { open(player, MenuType.MARKET_MY_LISTINGS); return; }
            int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
            List<CityMarketListing> listings = marketListings.getOrDefault(playerId, List.of());
            for (int i = 0; i < Math.min(slots.length, listings.size()); i++) if (slots[i] == slot) {
                selectedMarketListing.put(playerId, listings.get(i)); open(player, MenuType.MARKET_CONFIRM); return;
            }
        } else if (type == MenuType.MARKET_MY_LISTINGS) {
            if (slot == 48) { open(player, MenuType.MARKET); return; }
            int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
            List<CityMarketListing> listings = myMarketListings.getOrDefault(playerId, List.of());
            for (int i = 0; i < Math.min(slots.length, listings.size()); i++) if (slots[i] == slot) {
                selectedMarketListing.put(playerId, listings.get(i)); open(player, MenuType.MARKET_CANCEL_CONFIRM); return;
            }
        } else if (type == MenuType.MARKET_CONFIRM && slot == 29) {
            confirmMarketPurchase(player);
        } else if (type == MenuType.MARKET_CREATE_CONFIRM && slot == 29) {
            confirmMarketListing(player);
        } else if (type == MenuType.MARKET_CANCEL_CONFIRM && slot == 29) {
            cancelMarketListing(player);
        }
    }

    private void cancelMarketListing(Player player) {
        UUID playerId = player.getUniqueId(); CityMarketListing listing = selectedMarketListing.get(playerId);
        var market = cities.marketService().orElse(null);
        if (listing == null || market == null || !market.isReady()) {
            player.sendMessage(Component.text("Рынок временно недоступен.", NamedTextColor.RED)); return;
        }
        player.closeInventory();
        market.cancel(listing.id(), playerId).whenComplete((result, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                selectedMarketListing.remove(playerId);
                if (!player.isOnline()) return;
                if (error == null && result != null && result.code() == CityMarketCode.CANCELLED) {
                    player.sendMessage(Component.text("Объявление снято; товар помещён в доставку.", NamedTextColor.GREEN));
                    open(player, MenuType.MARKET);
                } else {
                    player.sendMessage(Component.text("Не удалось снять объявление: " + (result == null ? "ошибка" : result.code()), NamedTextColor.RED));
                    open(player, MenuType.MARKET_MY_LISTINGS);
                }
            });
        });
    }

    private void beginMarketListing(Player player) {
        UUID playerId = player.getUniqueId();
        CitySnapshot city = cityViews.get(playerId);
        boolean allowed = city != null && city.residents().stream().filter(r -> r.playerId().equals(playerId))
                .map(r -> CityRole.fromStorage(r.role()).allows(CityPermission.MANAGE_MARKET)).findFirst().orElse(false);
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!allowed) { player.sendMessage(Component.text("Выставлять товары могут Правитель, Заместитель и Казначей.", NamedTextColor.RED)); return; }
        if (held.getType().isAir()) { player.sendMessage(Component.text("Возьмите предмет в основную руку.", NamedTextColor.YELLOW)); return; }
        pendingMarketListings.put(playerId, new PendingMarketListing(UUID.randomUUID(), held.clone(), 0));
        awaitingMarketPrice.add(playerId); player.closeInventory();
        player.sendMessage(Component.text("Введите цену за весь стек в чате (до "
                + plugin.getConfig().getLong("market.maximum-listing-price", 1_000_000)
                + " монет). Напишите «отмена», чтобы прервать.", NamedTextColor.YELLOW));
    }

    private void confirmMarketListing(Player player) {
        UUID playerId = player.getUniqueId(); PendingMarketListing pending = pendingMarketListings.get(playerId);
        var market = cities.marketService().orElse(null);
        if (pending == null || pending.price <= 0 || market == null || !market.isReady()) {
            player.sendMessage(Component.text("Рынок временно недоступен.", NamedTextColor.RED)); return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir() || !held.isSimilar(pending.item) || held.getAmount() < pending.item.getAmount()) {
            player.sendMessage(Component.text("Предмет в руке изменился. Начните создание объявления заново.", NamedTextColor.RED));
            pendingMarketListings.remove(playerId, pending); open(player, MenuType.MARKET); return;
        }
        ItemStack escrow = pending.item.clone();
        var escrowKey = marketEscrowKey();
        ItemMeta meta = escrow.getItemMeta();
        meta.getPersistentDataContainer().set(escrowKey, org.bukkit.persistence.PersistentDataType.STRING, pending.listingId.toString());
        escrow.setItemMeta(meta);
        byte[] data = escrow.serializeAsBytes();
        String itemName = pending.item.getItemMeta().hasDisplayName()
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(pending.item.getItemMeta().displayName())
                : pending.item.getType().translationKey();
        player.closeInventory();
        market.prepare(new CityMarketCreateRequest(pending.listingId, playerId, itemName, data, pending.price))
                .whenComplete((prepared, prepareError) -> {
                    if (!plugin.isEnabled()) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        if (prepareError != null || prepared == null || prepared.code() != CityMarketCode.PREPARED) {
                            pendingMarketListings.remove(playerId, pending);
                            player.sendMessage(Component.text("Не удалось подготовить объявление; предмет остался у вас. Код: "
                                    + (prepared == null ? "ошибка" : prepared.code()), NamedTextColor.RED));
                            open(player, MenuType.MARKET); return;
                        }
                        ItemStack current = player.getInventory().getItemInMainHand();
                        if (current.getType().isAir() || !current.isSimilar(pending.item) || current.getAmount() < pending.item.getAmount()) {
                            market.abort(pending.listingId, playerId);
                            pendingMarketListings.remove(playerId, pending);
                            player.sendMessage(Component.text("Предмет изменился; объявление отменено.", NamedTextColor.RED));
                            open(player, MenuType.MARKET); return;
                        }
                        player.getInventory().setItemInMainHand(escrow);
                        player.saveData();
                        market.markRemoving(pending.listingId, playerId).whenComplete((marked, markError) -> {
                            if (!plugin.isEnabled()) return;
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (!player.isOnline()) return;
                                if (markError != null || !Boolean.TRUE.equals(marked)) {
                                    player.sendMessage(Component.text("Объявление подготовлено и будет сверено при следующем входе.", NamedTextColor.YELLOW));
                                    reconcileMarketListings(player);
                                    return;
                                }
                                if (!removeEscrowItem(player, pending.listingId.toString())) {
                                    player.sendMessage(Component.text("Не удалось снять предмет; восстановление завершится при следующем входе.", NamedTextColor.YELLOW));
                                    reconcileMarketListings(player);
                                    return;
                                }
                                market.activate(pending.listingId, playerId).whenComplete((activated, activationError) -> {
                                    if (!plugin.isEnabled()) return;
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        pendingMarketListings.remove(playerId, pending);
                                        if (!player.isOnline()) return;
                                        if (activationError == null && Boolean.TRUE.equals(activated)) {
                                            player.sendMessage(Component.text("Объявление выставлено; товар находится в безопасном escrow.", NamedTextColor.GREEN));
                                            open(player, MenuType.MARKET);
                                        } else {
                                            player.sendMessage(Component.text("Товар сохранён в escrow; объявление будет восстановлено при следующем входе.", NamedTextColor.YELLOW));
                                            reconcileMarketListings(player);
                                        }
                                    });
                                });
                            });
                        });
                    });
                });
    }

    private void confirmMarketPurchase(Player player) {
        UUID playerId = player.getUniqueId(); CityMarketListing listing = selectedMarketListing.get(playerId);
        var market = cities.marketService().orElse(null);
        if (listing == null || market == null || !market.isReady()) {
            player.sendMessage(Component.text("Рынок временно недоступен.", NamedTextColor.RED)); return;
        }
        player.closeInventory(); player.sendMessage(Component.text("Провожу покупку из казны города…", NamedTextColor.YELLOW));
        CityMarketPurchaseRequest request = new CityMarketPurchaseRequest(UUID.randomUUID(), playerId, listing.id());
        market.purchase(request).whenComplete((result, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                selectedMarketListing.remove(playerId);
                if (!player.isOnline()) return;
                if (error != null || result == null) player.sendMessage(Component.text("Не удалось завершить покупку.", NamedTextColor.RED));
                else if (result.code() == CityMarketCode.PURCHASED || result.code() == CityMarketCode.REPLAYED) {
                    player.sendMessage(Component.text("Покупка завершена. Предмет находится в доставке; сбор уже учтён.", NamedTextColor.GREEN));
                } else player.sendMessage(Component.text(marketMessage(result.code()), NamedTextColor.RED));
                open(player, MenuType.MARKET);
            });
        });
    }

    private String marketMessage(CityMarketCode code) {
        return switch (code) {
            case NO_CITY, NOT_MEMBER -> "Для покупки вы должны состоять в городе.";
            case PERMISSION_DENIED -> "Покупать за городскую казну могут Правитель, Заместитель и Казначей.";
            case INVALID_ITEM -> "Этот предмет нельзя выставить на рынок.";
            case INVALID_PRICE, LIMIT_EXCEEDED -> "Цена или сбор превышают допустимый предел.";
            case LISTING_NOT_FOUND -> "Объявление уже недоступно.";
            case SELF_PURCHASE -> "Нельзя покупать товар собственного города.";
            case TRADE_REQUIRED -> "Для торговли нужен действующий торговый договор.";
            case INSUFFICIENT_TREASURY -> "В городской казне недостаточно средств для цены и сбора.";
            case INVENTORY_FULL -> "Освободите место в инвентаре.";
            case OPERATION_CONFLICT -> "Конфликт операции; обратитесь к администратору.";
            case SERVICE_UNAVAILABLE, INTERNAL_ERROR -> "Городской рынок временно недоступен.";
            case PREPARED, LISTED, PURCHASED, CANCELLED, REPLAYED -> "Операция выполнена.";
        };
    }

    private void claimMarketDeliveries(Player player) {
        UUID playerId = player.getUniqueId();
        List<CityMarketDelivery> deliveries = marketDeliveries.getOrDefault(playerId, List.of());
        if (deliveries.isEmpty()) { player.sendMessage(Component.text("Неполученных товаров нет.", NamedTextColor.YELLOW)); return; }
        CityMarketDelivery delivery = deliveries.getFirst();
        ItemStack item = marketItem(delivery.itemData());
        if (item == null) { player.sendMessage(Component.text("Не удалось прочитать товар доставки.", NamedTextColor.RED)); return; }
        var key = new org.bukkit.NamespacedKey(plugin, "market_delivery");
        if (hasDeliveryMarker(player, delivery.id().toString(), key)) {
            acknowledgeDelivery(player, delivery, key, true); return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            player.sendMessage(Component.text("Освободите ячейку в инвентаре, чтобы получить товар.", NamedTextColor.YELLOW)); return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.STRING, delivery.id().toString());
        item.setItemMeta(meta); player.getInventory().addItem(item); player.saveData();
        acknowledgeDelivery(player, delivery, key, true);
    }

    private boolean hasDeliveryMarker(Player player, String id, org.bukkit.NamespacedKey key) {
        for (ItemStack stack : player.getInventory().getContents()) if (stack != null && stack.hasItemMeta()
                && id.equals(stack.getItemMeta().getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING))) return true;
        return false;
    }

    private void acknowledgeDelivery(Player player, CityMarketDelivery delivery, org.bukkit.NamespacedKey key, boolean openMarketAfter) {
        var market = cities.marketService().orElse(null);
        if (market == null) return;
        market.acknowledgeDelivery(delivery.id(), player.getUniqueId()).whenComplete((acknowledged, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || error != null || !Boolean.TRUE.equals(acknowledged)) return;
                for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (stack == null || !stack.hasItemMeta() || !delivery.id().toString().equals(
                            stack.getItemMeta().getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING))) continue;
                    ItemMeta meta = stack.getItemMeta(); meta.getPersistentDataContainer().remove(key); stack.setItemMeta(meta);
                }
                player.saveData();
                if (openMarketAfter) {
                    player.sendMessage(Component.text("Товар доставлен в инвентарь.", NamedTextColor.GREEN));
                    open(player, MenuType.MARKET);
                }
            });
        });
    }

    public void reconcileMarketDeliveries(Player player) {
        var market = cities.marketService().orElse(null);
        if (market == null || !market.isReady()) return;
        UUID playerId = player.getUniqueId(); var key = new org.bukkit.NamespacedKey(plugin, "market_delivery");
        market.deliveries(playerId).whenComplete((deliveries, error) -> {
            if (error != null || deliveries == null || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                java.util.Set<String> pendingIds = deliveries.stream().map(delivery -> delivery.id().toString())
                        .collect(java.util.stream.Collectors.toSet());
                boolean cleaned = false;
                for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (stack == null || !stack.hasItemMeta()) continue;
                    String marker = stack.getItemMeta().getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING);
                    if (marker == null || pendingIds.contains(marker)) continue;
                    ItemMeta meta = stack.getItemMeta(); meta.getPersistentDataContainer().remove(key); stack.setItemMeta(meta); cleaned = true;
                }
                if (cleaned) player.saveData();
                for (CityMarketDelivery delivery : deliveries) if (hasDeliveryMarker(player, delivery.id().toString(), key))
                    acknowledgeDelivery(player, delivery, key, false);
            });
        });
    }

    private void openTreasuryTransferTargets(Player player) {
        UUID playerId = player.getUniqueId();
        CityDiplomacySnapshot snapshot = diplomacySnapshots.get(playerId);
        UUID ownCity = cityIds.get(playerId);
        CitySnapshot city = cityViews.get(playerId);
        boolean allowed = city != null && city.residents().stream().filter(r -> r.playerId().equals(playerId))
                .map(r -> CityRole.fromStorage(r.role()).allows(CityPermission.TRANSFER_TREASURY)).findFirst().orElse(false);
        if (!allowed || snapshot == null || ownCity == null) {
            player.sendMessage(Component.text("Перевод доступен Правителю, Заместителю и Казначею при активном торговом договоре.", NamedTextColor.RED));
            return;
        }
        java.util.Set<UUID> tradePartners = snapshot.treaties().stream()
                .filter(t -> t.type() == CityTreatyType.TRADE && t.state() == CityTreatyState.ACTIVE && t.expiresAt().isAfter(Instant.now()))
                .map(t -> t.proposingCityId().equals(ownCity) ? t.targetCityId() : t.proposingCityId())
                .collect(java.util.stream.Collectors.toSet());
        List<CityDiplomacySnapshot.CitySummary> targets = snapshot.otherCities().stream()
                .filter(c -> tradePartners.contains(c.id())).toList();
        treasuryTransferTargets.put(playerId, targets);
        if (targets.isEmpty()) {
            player.sendMessage(Component.text("Нет города с действующим торговым договором.", NamedTextColor.YELLOW));
            return;
        }
        show(player, MenuType.TREASURY_TRANSFER_TARGETS, city, false, ownCity);
    }

    private void handleTreasuryTransferClick(Player player, MenuType type, int slot) {
        UUID playerId = player.getUniqueId();
        if (slot == 45 || (type == MenuType.TREASURY_TRANSFER_CONFIRM && slot == 33)) {
            if (type == MenuType.TREASURY_TRANSFER_CONFIRM) pendingTreasuryTransfers.remove(playerId);
            open(player, MenuType.TREASURY); return;
        }
        if (type == MenuType.TREASURY_TRANSFER_TARGETS) {
            int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
            List<CityDiplomacySnapshot.CitySummary> targets = treasuryTransferTargets.getOrDefault(playerId, List.of());
            for (int i = 0; i < Math.min(slots.length, targets.size()); i++) if (slots[i] == slot) {
                awaitingTransferAmount.put(playerId, targets.get(i).id());
                player.closeInventory();
                player.sendMessage(Component.text("Введите сумму монет в чат (до "
                        + plugin.getConfig().getLong("diplomacy.maximum-transfer-amount", 100000)
                        + "). Напишите «отмена», чтобы прервать.", NamedTextColor.YELLOW));
                return;
            }
            return;
        }
        if (slot == 29) confirmTreasuryTransfer(player);
    }

    private void confirmTreasuryTransfer(Player player) {
        UUID playerId = player.getUniqueId();
        PendingTreasuryTransfer pending = pendingTreasuryTransfers.get(playerId);
        var service = cities.treasuryService().orElse(null);
        if (pending == null || service == null || !service.isReady()) {
            player.sendMessage(Component.text("Перевод казны сейчас недоступен.", NamedTextColor.RED)); return;
        }
        player.closeInventory();
        service.transfer(new CityTreasuryTransferRequest(pending.operationId, playerId, pending.targetCityId, pending.amount))
                .whenComplete((result, error) -> {
                    if (!plugin.isEnabled()) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        pendingTreasuryTransfers.remove(playerId, pending);
                        if (error != null || result == null) {
                            player.sendMessage(Component.text("Не удалось выполнить перевод. Проверьте казны перед повтором.", NamedTextColor.RED));
                            plugin.getLogger().warning("City treasury transfer failed: " + error);
                            return;
                        }
                        if (result.code() == CityTreasuryTransferCode.SENT || result.code() == CityTreasuryTransferCode.REPLAYED) {
                            String targetName = result.targetCity().map(CityView::name).orElse("город");
                            player.sendMessage(Component.text("Переведено " + money(pending.amount) + " в казну города «" + targetName + "».", NamedTextColor.GREEN));
                        } else player.sendMessage(Component.text(treasuryTransferMessage(result.code()), NamedTextColor.RED));
                        open(player, MenuType.TREASURY);
                    });
                });
    }

    private String treasuryTransferMessage(CityTreasuryTransferCode code) {
        return switch (code) {
            case NO_CITY, NOT_MEMBER -> "Вы не состоите в городе.";
            case PERMISSION_DENIED -> "Переводить средства могут Правитель, Заместитель и Казначей.";
            case INVALID_TARGET -> "Город-получатель недоступен.";
            case INVALID_AMOUNT -> "Укажите положительную сумму.";
            case LIMIT_EXCEEDED -> "Сумма превышает лимит одного перевода.";
            case TRADE_REQUIRED -> "Для перевода нужен действующий торговый договор.";
            case INSUFFICIENT_TREASURY -> "В казне вашего города недостаточно монет.";
            case OPERATION_CONFLICT -> "Не удалось сверить перевод. Обратитесь к администратору.";
            case SERVICE_UNAVAILABLE, INTERNAL_ERROR -> "Сервис казны временно недоступен.";
            case SENT, REPLAYED -> "Перевод выполнен.";
        };
    }

    private void handleDiplomacyClick(Player player, MenuSession session, int slot, boolean rightClick) {
        UUID playerId = player.getUniqueId();
        UUID ownCity = cityIds.get(playerId);
        CityDiplomacySnapshot snapshot = diplomacySnapshots.get(playerId);
        if (slot == 45) {
            if (session.menuType() == MenuType.DIPLOMACY) open(player, MenuType.MAIN);
            else openDiplomacy(player);
            return;
        }
        if (session.menuType() == MenuType.DIPLOMACY && slot == 49) {
            if (snapshot == null) { openDiplomacy(player); return; }
            diplomacyTargets.put(playerId, snapshot.otherCities());
            show(player, MenuType.DIPLOMACY_TARGETS, cityViews.get(playerId), false, ownCity);
            return;
        }
        if (session.menuType() == MenuType.DIPLOMACY && slot >= 10 && slot < 40 && snapshot != null && ownCity != null) {
            int index = slot - 10;
            if (index >= snapshot.treaties().size()) return;
            CityTreatyView treaty = snapshot.treaties().get(index);
            CityDiplomacyRequest.Action action;
            if (treaty.state() == CityTreatyState.PENDING) {
                boolean incoming = treaty.targetCityId().equals(ownCity);
                if (incoming && rightClick) action = CityDiplomacyRequest.Action.DECLINE;
                else if (incoming) action = CityDiplomacyRequest.Action.ACCEPT;
                else if (!rightClick) action = CityDiplomacyRequest.Action.CANCEL;
                else return;
            } else if (treaty.state() == CityTreatyState.ACTIVE && !rightClick) action = CityDiplomacyRequest.Action.TERMINATE;
            else return;
            submitDiplomacy(player, new CityDiplomacyRequest(treaty.id(), ownCity, playerId, action,
                    null, null, 30));
            return;
        }
        if (session.menuType() == MenuType.DIPLOMACY_TARGETS && slot >= 10 && slot < 44) {
            List<ru.servermine.cities.api.CityDiplomacySnapshot.CitySummary> targets = diplomacyTargets.getOrDefault(playerId, List.of());
            int index = slot - 10;
            if (index >= targets.size()) return;
            diplomacySelectedTarget.put(playerId, targets.get(index).id());
            diplomacySelectedType.put(playerId, CityTreatyType.ALLIANCE);
            diplomacyDuration.put(playerId, 30);
            show(player, MenuType.DIPLOMACY_CREATE, cityViews.get(playerId), false, ownCity);
            return;
        }
        if (session.menuType() == MenuType.DIPLOMACY_CREATE) {
            if (slot == 20 || slot == 22 || slot == 24) {
                diplomacySelectedType.put(playerId, slot == 20 ? CityTreatyType.ALLIANCE
                        : slot == 22 ? CityTreatyType.NON_AGGRESSION : CityTreatyType.TRADE);
                show(player, MenuType.DIPLOMACY_CREATE, cityViews.get(playerId), false, ownCity); return;
            }
            if (slot == 29 || slot == 31 || slot == 33) {
                diplomacyDuration.put(playerId, slot == 29 ? 7 : slot == 31 ? 30 : 90);
                show(player, MenuType.DIPLOMACY_CREATE, cityViews.get(playerId), false, ownCity); return;
            }
            if (slot == 40 && ownCity != null) {
                UUID target = diplomacySelectedTarget.get(playerId);
                CityTreatyType type = diplomacySelectedType.getOrDefault(playerId, CityTreatyType.ALLIANCE);
                if (target == null) { openDiplomacy(player); return; }
                submitDiplomacy(player, new CityDiplomacyRequest(UUID.randomUUID(), ownCity, playerId,
                        CityDiplomacyRequest.Action.PROPOSE, target, type, diplomacyDuration.getOrDefault(playerId, 30)));
            }
        }
    }

    private void submitDiplomacy(Player player, CityDiplomacyRequest request) {
        var service = cities.diplomacyService().orElse(null);
        if (service == null || !service.isReady()) { player.sendMessage(Component.text("Дипломатия недоступна.", NamedTextColor.RED)); return; }
        service.execute(request).whenComplete((result, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (error != null || result == null) {
                    plugin.getLogger().warning("City diplomacy action failed: " + error);
                    player.sendMessage(Component.text("Не удалось выполнить дипломатическое действие.", NamedTextColor.RED));
                } else {
                    String message = switch (result.code()) {
                        case CREATED -> "Предложение отправлено. Другой город должен его принять.";
                        case ACCEPTED -> "Договор заключён.";
                        case DECLINED -> "Предложение отклонено.";
                        case CANCELLED -> "Предложение отозвано или договор прекращён.";
                        case TERMINATED -> "Договор прекращён.";
                        case PERMISSION_DENIED -> "Дипломатией может управлять только правитель или заместитель.";
                        case ALREADY_EXISTS -> "Такое действующее соглашение или предложение уже есть.";
                        case NOT_MEMBER -> "Вы больше не состоите в этом городе.";
                        case SELF_TARGET -> "Нельзя заключить договор с собственным городом.";
                        case INVALID_TARGET -> "Выбранный город больше не существует.";
                        case WRONG_SIDE -> "Это действие доступно другой стороне договора.";
                        default -> "Состояние предложения изменилось. Обновите меню.";
                    };
                    player.sendMessage(Component.text(message, result.code() == CityDiplomacyCode.CREATED
                            || result.code() == CityDiplomacyCode.ACCEPTED ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                }
                openDiplomacy(player);
            });
        });
    }

    private boolean residentSlot(int rawSlot) {
        return rawSlot == 10 || rawSlot == 11 || rawSlot == 12 || rawSlot == 13 || rawSlot == 14
                || rawSlot == 15 || rawSlot == 16 || rawSlot == 19 || rawSlot == 20 || rawSlot == 21
                || rawSlot == 22 || rawSlot == 23 || rawSlot == 24 || rawSlot == 25;
    }

    private void beginMemberRoleManagement(Player player, MenuSession session, int rawSlot) {
        CitySnapshot current = cityViews.get(player.getUniqueId());
        if (session.preview() || current == null) {
            player.sendMessage(Component.text("Управление составом недоступно в макете.", NamedTextColor.YELLOW));
            return;
        }
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int index = -1;
        for (int i = 0; i < slots.length; i++) if (slots[i] == rawSlot) { index = i; break; }
        if (index < 0 || index >= current.residents().size()) return;
        CitySnapshot.ResidentEntry target = current.residents().get(index);
        if (target.playerId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Чтобы покинуть город, используйте /smcities leave.", NamedTextColor.YELLOW));
            return;
        }
        pendingRoleTargets.put(player.getUniqueId(), new PendingRoleTarget(target.playerId(), target.name(),
                target.role(), session.cityRevision()));
        open(player, MenuType.MEMBER_ROLE_MANAGEMENT);
    }

    private boolean roleSlot(int slot) { return slot == 20 || slot == 22 || slot == 24 || slot == 29; }

    private void assignSelectedRole(Player player, int slot) {
        PendingRoleTarget pending = pendingRoleTargets.get(player.getUniqueId());
        CityRole role = switch (slot) {
            case 20 -> CityRole.DEPUTY;
            case 22 -> CityRole.TREASURER;
            case 24 -> CityRole.BUILDER;
            case 29 -> CityRole.MEMBER;
            default -> null;
        };
        if (pending == null || role == null) return;
        if (pending.currentRole.equals(role.name())) {
            player.sendMessage(Component.text("У участника уже эта должность.", NamedTextColor.YELLOW));
            return;
        }
        var service = cities.roleService().orElse(null);
        if (service == null || !service.isReady()) {
            player.sendMessage(Component.text("Управление ролями временно недоступно.", NamedTextColor.RED));
            return;
        }
        player.closeInventory();
        service.assign(player.getUniqueId(), pending.targetId, role, pending.revision).whenComplete((result, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                pendingRoleTargets.remove(player.getUniqueId());
                if (error != null || result == null) {
                    player.sendMessage(Component.text("Не удалось изменить должность.", NamedTextColor.RED));
                    return;
                }
                switch (result.code()) {
                    case ALREADY_ASSIGNED -> player.sendMessage(Component.text("У участника уже эта должность.", NamedTextColor.YELLOW));
                    case ROLE_CHANGED -> {
                        player.sendMessage(Component.text(pending.targetName + " теперь: " + role.displayName() + ".", NamedTextColor.GREEN));
                        Player target = Bukkit.getPlayer(pending.targetId);
                        if (target != null) target.sendMessage(Component.text("Ваша должность в городе изменена: " + role.displayName() + ".", NamedTextColor.YELLOW));
                        open(player, MenuType.RESIDENTS);
                    }
                    case PERMISSION_DENIED -> player.sendMessage(Component.text("У вас нет права назначить эту должность.", NamedTextColor.RED));
                    case NO_CITY -> player.sendMessage(Component.text("Вы не состоите в городе.", NamedTextColor.RED));
                    case TARGET_NOT_IN_CITY -> player.sendMessage(Component.text("Участник больше не состоит в городе.", NamedTextColor.YELLOW));
                    case CANNOT_CHANGE_RULER -> player.sendMessage(Component.text("Нельзя изменить должность правителя.", NamedTextColor.RED));
                    case STALE_CITY -> player.sendMessage(Component.text("Состав города изменился. Откройте жителей заново.", NamedTextColor.YELLOW));
                    case SERVICE_UNAVAILABLE, INTERNAL_ERROR -> player.sendMessage(Component.text("Не удалось изменить должность.", NamedTextColor.RED));
                }
                if (result.code() != CityRoleCode.ROLE_CHANGED) open(player, MenuType.RESIDENTS);
            });
        });
    }

    private void beginKickFromRoleManagement(Player player) {
        PendingRoleTarget pending = pendingRoleTargets.get(player.getUniqueId());
        if (pending == null) return;
        if (CityRole.fromStorage(pending.currentRole) == CityRole.RULER) {
            player.sendMessage(Component.text("Нельзя исключить правителя города.", NamedTextColor.RED));
            return;
        }
        pendingMemberKicks.put(player.getUniqueId(), new PendingMemberKick(pending.targetId, pending.targetName, pending.revision));
        open(player, MenuType.MEMBER_KICK_CONFIRM);
    }

    private void confirmMemberKick(Player player) {
        PendingMemberKick pending = pendingMemberKicks.remove(player.getUniqueId());
        var membership = cities.membershipService().orElse(null);
        if (pending == null || membership == null || !membership.isReady()) {
            player.sendMessage(Component.text("Управление составом сейчас недоступно.", NamedTextColor.RED));
            return;
        }
        player.closeInventory();
        membership.kick(player.getUniqueId(), pending.targetId, pending.revision).whenComplete((result, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (error != null || result == null) {
                    player.sendMessage(Component.text("Не удалось исключить участника.", NamedTextColor.RED));
                    return;
                }
                switch (result.code()) {
                    case KICKED -> {
                        player.sendMessage(Component.text(pending.targetName + " исключён из города.", NamedTextColor.GREEN));
                        Player target = Bukkit.getPlayer(pending.targetId);
                        if (target != null) target.sendMessage(Component.text("Вас исключили из города "
                                + result.city().map(CityView::name).orElse("") + ".", NamedTextColor.RED));
                        open(player, MenuType.RESIDENTS);
                    }
                    case PERMISSION_DENIED -> player.sendMessage(Component.text("У вас нет права исключить этого участника.", NamedTextColor.RED));
                    case STALE_CITY -> player.sendMessage(Component.text("Состав изменился. Откройте список жителей и попробуйте снова.", NamedTextColor.YELLOW));
                    case TARGET_NOT_IN_CITY -> player.sendMessage(Component.text("Этот игрок больше не состоит в городе.", NamedTextColor.YELLOW));
                    case CANNOT_KICK_RULER -> player.sendMessage(Component.text("Нельзя исключить правителя города.", NamedTextColor.RED));
                    case NO_CITY -> player.sendMessage(Component.text("Вы не состоите в городе.", NamedTextColor.RED));
                    default -> player.sendMessage(Component.text("Не удалось исключить участника.", NamedTextColor.RED));
                }
            });
        });
    }

    private void openPurchaseTargets(Player player) {
        CityTerritoryService service = cities.territoryService().orElse(null);
        UUID cityId = cityIds.get(player.getUniqueId());
        if (service == null || !service.isReady() || cityId == null) {
            player.sendMessage(Component.text("Покупка территории сейчас недоступна.", NamedTextColor.RED));
            return;
        }
        UUID requestId = UUID.randomUUID();
        requests.put(player.getUniqueId(), requestId);
        player.sendMessage(Component.text("Ищу свободные соседние чанки…", NamedTextColor.YELLOW));
        service.availableTargets(cityId).whenComplete((targets, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !requestId.equals(requests.get(player.getUniqueId()))) return;
                requests.remove(player.getUniqueId());
                if (error != null) {
                    plugin.getLogger().warning("Cannot list city claim targets: " + error);
                    player.sendMessage(Component.text("Не удалось загрузить соседние чанки.", NamedTextColor.RED));
                    return;
                }
                purchaseTargets.put(player.getUniqueId(), targets);
                purchasePages.put(player.getUniqueId(), 0);
                open(player, MenuType.PURCHASE);
            });
        });
    }

    private boolean candidateSlot(int rawSlot) {
        for (int slot : purchaseSlots()) if (slot == rawSlot) return true;
        return false;
    }

    private void beginClaim(Player player, MenuSession session, int rawSlot) {
        List<ChunkPosition> targets = purchaseTargets.getOrDefault(player.getUniqueId(), List.of());
        int index = purchasePages.getOrDefault(player.getUniqueId(), 0) * purchaseSlots().length;
        int[] slots = purchaseSlots();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == rawSlot) { index += i; break; }
        }
        if (index >= targets.size()) return;
        beginClaim(player, session, targets.get(index));
    }

    private void beginClaim(Player player, MenuSession session, ChunkPosition target) {
        UUID cityId = cityIds.get(player.getUniqueId());
        if (cityId == null) {
            player.sendMessage(Component.text("Город не найден. Откройте книгу заново.", NamedTextColor.RED));
            return;
        }
        pendingClaims.put(player.getUniqueId(), new PendingClaim(UUID.randomUUID(), cityId, target, session.cityRevision()));
        open(player, MenuType.PURCHASE_CONFIRM);
    }

    private void confirmClaim(Player player) {
        PendingClaim pending = pendingClaims.remove(player.getUniqueId());
        CityTerritoryService service = cities.territoryService().orElse(null);
        if (pending == null || service == null || !service.isReady()) {
            player.sendMessage(Component.text("Покупка территории сейчас недоступна.", NamedTextColor.RED));
            return;
        }
        player.closeInventory();
        player.sendMessage(Component.text("Проверяю участок и резервирую оплату…", NamedTextColor.YELLOW));
        CityClaimRequest request = new CityClaimRequest(pending.operationId, pending.cityId,
                player.getUniqueId(), pending.revision, pending.target);
        service.purchase(request).whenComplete((result, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (error != null) {
                    player.sendMessage(Component.text("Не удалось подтвердить результат покупки. Не повторяйте оплату; обратитесь к администратору.", NamedTextColor.RED));
                    plugin.getLogger().warning("City chunk claim failed for operation " + pending.operationId + ": " + error);
                } else if (result.code() == CityClaimCode.CLAIMED || result.code() == CityClaimCode.REPLAYED) {
                    String paid = service.chunkPrice() == 0 ? "Бесплатно." : "Из казны списано: " + money(service.chunkPrice()) + ".";
                    player.sendMessage(Component.text("Чанк закреплён за городом. " + paid, NamedTextColor.GREEN));
                    open(player, MenuType.TERRITORY);
                } else {
                    player.sendMessage(Component.text(claimMessage(result.code()), NamedTextColor.RED));
                    open(player, MenuType.TERRITORY);
                }
            });
        });
    }

    private String claimMessage(CityClaimCode code) {
        return switch (code) {
            case NOT_FOUND -> "Город не найден.";
            case NOT_MEMBER -> "Вы не состоите в этом городе.";
            case PERMISSION_DENIED -> "У вашей должности нет права покупать территорию.";
            case PROMOTION_REQUIRED -> "Сначала повысьте этап города до Деревни.";
            case STALE_REVISION -> "Город изменился. Откройте книгу и выберите чанк заново.";
            case CAP_REACHED -> "Лимит территории этого этапа достигнут.";
            case ALREADY_OWNED -> "Этот чанк уже принадлежит вашему городу.";
            case TARGET_UNAVAILABLE -> "Чанк недоступен или уже занят другим городом.";
            case PROTECTED_ZONE -> "Этот чанк находится в защищённой зоне.";
            case DISCONNECTED -> "Выберите чанк рядом с существующей территорией города.";
            case INSUFFICIENT_FUNDS -> "Недостаточно монет.";
            case INSUFFICIENT_TREASURY -> "В казне города недостаточно монет. Пополните её через меню казны.";
            case ECONOMY_UNAVAILABLE -> "Экономика сейчас недоступна.";
            case OPERATION_CONFLICT -> "Операция конфликтует с предыдущим запросом.";
            case OPERATION_IN_PROGRESS, UNKNOWN_OUTCOME -> "Покупка ещё не подтверждена. Не повторяйте оплату; обратитесь к администратору.";
            case SERVICE_UNAVAILABLE -> "Сервис территории сейчас недоступен.";
            case INTERNAL_ERROR -> "Не удалось купить чанк из-за внутренней ошибки.";
            case CLAIMED, REPLAYED -> "Чанк куплен.";
        };
    }

    private long chunkPrice() {
        return Math.max(0, plugin.getConfig().getLong("territory.chunk-price", 750));
    }

    private static String money(long value) {
        return NUMBER.format(value) + " монет";
    }

    private boolean isSecurityError(Throwable error) {
        while (error != null) {
            if (error instanceof SecurityException) return true;
            error = error.getCause();
        }
        return false;
    }

    private ItemStack back() {
        return button(Material.ARROW, "Назад", "Вернуться в главное меню");
    }

    private ItemStack button(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (plugin.getConfig().getBoolean("visual.use-pixel-backgrounds", false)) {
            meta.setItemModel(NamespacedKey.fromString("gradostroy:gui/button/" + buttonModel(material, name)));
        }
        meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String buttonModel(Material material, String name) {
        String label = name.toLowerCase(Locale.ROOT);
        if (label.contains("отмена") || label.contains("исключить") || label.contains("удалить")
                || label.contains("отменить") || label.contains("отказать")) return "cancel";
        if (label.contains("назад") || label.startsWith("к платежам") || label.startsWith("к объявлениям")) return "back";
        if (label.contains("подтвердить") || label.contains("купить") || label.contains("основать")
                || label.contains("создать") || label.contains("сверить операцию")) return "confirm";
        if (label.contains("админ") || label.contains("сверка платеж") || label.contains("тестов")) return "admin";
        if (label.contains("рынок") || label.contains("объявлен") || label.contains("товар")) return "market";
        if (label.contains("дипломат") || label.contains("союз") || label.contains("договор")
                || label.contains("отношен") || label.contains("торгов")) return "diplomacy";
        if (label.contains("территор") || label.contains("чанк") || label.contains("земл")) return "territory";
        if (label.contains("жител") || label.contains("участник") || label.contains("игрок")
                || label.contains("пригласить") || label.contains("правител") || label.contains("заместител")) return "resident";
        if (label.contains("казн") || label.contains("баланс") || label.contains("монет")
                || label.contains("перевести") || label.contains("пополнить") || label.contains("расход")) return "treasury";
        if (label.contains("налог") || label.contains("сбор")) return "tax";
        if (label.contains("закон") || label.contains("правил") || label.contains("политик")) return "law";
        if (label.contains("улучш")) return "upgrade";
        if (label.contains("управлен") || label.contains("должност") || label.contains("право")) return "management";
        if (label.contains("развит") || label.contains("этап") || label.contains("переход")) return "progression";
        if (label.contains("защит") || label.contains("щит") || label.contains("страж")) return "protection";
        if (label.contains("обзор") || label.contains("карта") || label.contains("координат")) return "overview";
        return switch (material) {
            case GRASS_BLOCK, MAP -> "territory";
            case PLAYER_HEAD -> "resident";
            case COMPARATOR, ENCHANTED_BOOK -> "upgrade";
            case GOLD_BLOCK, GOLD_INGOT, GOLD_NUGGET, EMERALD, EMERALD_BLOCK -> "treasury";
            case BOOK, WRITABLE_BOOK, PAPER -> "management";
            case BLUE_BANNER, GREEN_BANNER, LIME_BANNER -> "diplomacy";
            case CHEST, CHEST_MINECART -> "market";
            case LIME_CONCRETE, LIME_DYE, GREEN_CONCRETE -> "confirm";
            case RED_CONCRETE, RED_DYE, TNT -> "cancel";
            case ARROW -> "back";
            case COMMAND_BLOCK, REDSTONE_BLOCK -> "admin";
            case EXPERIENCE_BOTTLE -> "progression";
            case BELL, SHIELD, IRON_CHESTPLATE, IRON_SWORD -> "protection";
            case CLOCK, RECOVERY_COMPASS -> "tax";
            case COMPASS, SPYGLASS -> "overview";
            case BARRIER -> "locked";
            case TRIPWIRE_HOOK, LEVER, IRON_DOOR, FLINT_AND_STEEL -> "law";
            default -> "info";
        };
    }

    private record PendingFounding(java.util.UUID operationId, ChunkPosition anchor, String cityName, Instant createdAt) {
        PendingFounding withName(String name) { return new PendingFounding(operationId, anchor, name, createdAt); }
    }

    private record PendingPromotion(UUID operationId, UUID cityId, CityStage expectedStage, long revision) { }

    private record PendingUpgrade(UUID operationId, UUID cityId, long revision, CityUpgradeId upgradeId) { }

    private record PendingClaim(UUID operationId, UUID cityId, ChunkPosition target, long revision) { }
    private record TerritoryOrigin(UUID worldId, int centerX, int centerZ) { }
    private record PendingMemberKick(UUID targetId, String targetName, long revision) { }
    private record PendingRoleTarget(UUID targetId, String targetName, String currentRole, long revision) { }
    private record PendingTreasuryTransfer(UUID operationId, UUID targetCityId, long amount) { }
    private record PendingMarketListing(UUID listingId, ItemStack item, long price) {
        PendingMarketListing withPrice(long value) { return new PendingMarketListing(listingId, item, value); }
    }
    private record MarketData(List<CityMarketListing> listings, List<CityMarketDelivery> deliveries) { }
    private record RoleOption(int slot, CityRole role, Material material, String description) { }
}
