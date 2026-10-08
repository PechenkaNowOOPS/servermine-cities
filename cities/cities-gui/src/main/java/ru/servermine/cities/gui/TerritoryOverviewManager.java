package ru.servermine.cities.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scheduler.BukkitTask;
import ru.servermine.cities.api.ChunkPosition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Temporary, read-only flight view used to choose a city chunk. */
public final class TerritoryOverviewManager implements Listener {
    private static final float LOCKED_PITCH = 90.0f;
    private static final float LOCKED_YAW = 180.0f;
    private static final int OUTLINE_RADIUS = 3;
    private static final double CAMERA_SPEED_PER_TICK = 0.28;
    private static final Material OWNED_LINE = Material.LIME_STAINED_GLASS_PANE;
    private static final Material AVAILABLE_LINE = Material.YELLOW_STAINED_GLASS_PANE;
    private static final Material UNAVAILABLE_LINE = Material.RED_STAINED_GLASS_PANE;
    private static final Material SELECTED_LINE = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
    private final JavaPlugin plugin;
    private final BiConsumer<Player, Selection> onSelect;
    private final Consumer<Player> onExit;
    private final Map<UUID, State> sessions = new HashMap<>();
    private final Map<UUID, Map<MarkerPosition, BlockData>> clientLines = new HashMap<>();
    private final Map<PaneState, BlockData> paneStates = new HashMap<>();
    private final BukkitTask movementTask;
    private final BukkitTask refreshTask;

    public TerritoryOverviewManager(JavaPlugin plugin, BiConsumer<Player, Selection> onSelect, Consumer<Player> onExit) {
        this.plugin = plugin;
        this.onSelect = onSelect;
        this.onExit = onExit;
        this.movementTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickMovement, 1L, 1L);
        this.refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOverview, 20L, 20L);
    }

    public boolean start(Player player, UUID cityId, long revision, Set<ChunkPosition> owned,
                         List<ChunkPosition> available, UUID worldId, int centerChunkX, int centerChunkZ) {
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            player.sendMessage(Component.text("Мир города сейчас не загружен.", NamedTextColor.RED));
            return false;
        }
        Location origin = player.getLocation().clone();
        int blockX = centerChunkX * 16 + 8;
        int blockZ = centerChunkZ * 16 + 8;
        int surfaceY = world.getHighestBlockYAt(blockX, blockZ);
        double y = Math.min(world.getMaxHeight() - 8, Math.max(surfaceY + 32, world.getMinHeight() + 16));
        Location overview = new Location(world, blockX + 0.5, y, blockZ + 0.5, LOCKED_YAW, LOCKED_PITCH);
        ArmorStand camera = world.spawn(overview, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCollidable(false);
        });
        for (Player other : Bukkit.getOnlinePlayers()) if (!other.equals(player)) other.hideEntity(plugin, camera);
        Hud hud = createHud();
        State state = new State(origin, player.getGameMode(), player.getAllowFlight(), player.isFlying(),
                player.getFlySpeed(), player.getWalkSpeed(), worldId, centerChunkX, centerChunkZ,
                y, cityId, revision, Set.copyOf(owned), Set.copyOf(available), player.getScoreboard(), hud, camera,
                new MovementInput());

        sessions.put(player.getUniqueId(), state);
        player.closeInventory();
        player.setScoreboard(hud.scoreboard);
        player.setGameMode(GameMode.SPECTATOR);
        player.setFlying(true);
        if (!player.teleport(overview)) {
            sessions.remove(player.getUniqueId());
            camera.remove();
            restore(player, state, true);
            player.sendMessage(Component.text("Не удалось переместить вас к территории города.", NamedTextColor.RED));
            return false;
        }
        player.setSpectatorTarget(camera);
        player.sendMessage(Component.text("Обзор территории: WASD — плавное движение по карте; высота и направление камеры закреплены.", NamedTextColor.GREEN));
        player.sendMessage(Component.text("HUD справа: зелёный — город, жёлтый — покупка, красный — занято, бирюзовый — выбранный чанк.", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("ЛКМ или /smcities buy — купить участок; Shift или /smcities exit — выйти.", NamedTextColor.YELLOW));
        sendChunkStatus(player, state, overview.getBlockX() >> 4, overview.getBlockZ() >> 4);
        renderHud(state, player, overview.getBlockX() >> 4, overview.getBlockZ() >> 4);
        renderGroundLines(state, player);
        return true;
    }

    public boolean isViewing(UUID playerId) { return sessions.containsKey(playerId); }

    public boolean select(Player player) {
        State state = sessions.get(player.getUniqueId());
        if (state == null) return false;
        Location camera = state.camera.getLocation();
        ChunkPosition selected = new ChunkPosition(state.worldId, camera.getBlockX() >> 4, camera.getBlockZ() >> 4);
        if (state.owned.contains(selected)) {
            player.sendMessage(Component.text("Этот чанк уже принадлежит городу.", NamedTextColor.YELLOW));
        } else if (!state.available.contains(selected)) {
            player.sendMessage(Component.text("Этот чанк занят или недоступен для покупки.", NamedTextColor.RED));
        } else {
            onSelect.accept(player, new Selection(state.cityId, state.revision, selected));
        }
        return true;
    }

    public boolean exit(Player player, boolean reopenMenu) {
        State state = sessions.remove(player.getUniqueId());
        if (state == null) return false;
        restore(player, state, true);
        player.sendMessage(Component.text("Режим обзора завершён.", NamedTextColor.YELLOW));
        if (reopenMenu && player.isOnline()) Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) onExit.accept(player);
        });
        return true;
    }

    public void shutdown() {
        movementTask.cancel();
        refreshTask.cancel();
        Map<UUID, State> active = Map.copyOf(sessions);
        sessions.clear();
        for (Map.Entry<UUID, State> entry : active.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) restore(player, entry.getValue(), true);
        }
    }

    private void restore(Player player, State state, boolean returnToOrigin) {
        clearClientLines(player, state);
        if (player.getGameMode() == GameMode.SPECTATOR) player.setSpectatorTarget(null);
        state.camera.remove();
        player.setScoreboard(state.previousScoreboard);
        state.hud.objective.unregister();
        player.setGameMode(state.gameMode);
        player.setAllowFlight(state.allowFlight);
        player.setFlying(state.wasFlying && state.allowFlight);
        player.setFlySpeed(state.flySpeed);
        player.setWalkSpeed(state.walkSpeed);
        if (returnToOrigin && player.isOnline()) player.teleport(state.origin);
    }

    private void refreshOverview() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, State> entry : List.copyOf(sessions.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            State state = entry.getValue();
            Location camera = state.camera.getLocation();
            int x = camera.getBlockX() >> 4;
            int z = camera.getBlockZ() >> 4;
            if (x != state.renderedChunkX || z != state.renderedChunkZ) {
                sendChunkStatus(player, state, x, z);
                renderHud(state, player, x, z);
                renderGroundLines(state, player);
            } else if (now - state.lastGroundRefreshMillis >= 5_000) {
                renderGroundLines(state, player);
            }
        }
    }

    private void tickMovement() {
        for (Map.Entry<UUID, State> entry : List.copyOf(sessions.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            State state = entry.getValue();
            if (!state.input.moving()) {
                if (player.getSpectatorTarget() != state.camera) player.setSpectatorTarget(state.camera);
                continue;
            }
            if (player.getSpectatorTarget() != state.camera) player.setSpectatorTarget(state.camera);

            double dx = (state.input.right ? 1.0 : 0.0) - (state.input.left ? 1.0 : 0.0);
            double dz = (state.input.backward ? 1.0 : 0.0) - (state.input.forward ? 1.0 : 0.0);
            if (dx != 0 || dz != 0) {
                double length = Math.sqrt(dx * dx + dz * dz);
                dx = dx / length * CAMERA_SPEED_PER_TICK;
                dz = dz / length * CAMERA_SPEED_PER_TICK;
            }
            Location current = state.camera.getLocation();
            double minX = ((state.centerChunkX - OUTLINE_RADIUS) << 4) + 0.5;
            double maxX = ((state.centerChunkX + OUTLINE_RADIUS + 1) << 4) - 0.5;
            double minZ = ((state.centerChunkZ - OUTLINE_RADIUS) << 4) + 0.5;
            double maxZ = ((state.centerChunkZ + OUTLINE_RADIUS + 1) << 4) - 0.5;
            Location next = new Location(current.getWorld(),
                    Math.max(minX, Math.min(maxX, current.getX() + dx)), state.overviewY,
                    Math.max(minZ, Math.min(maxZ, current.getZ() + dz)), LOCKED_YAW, LOCKED_PITCH);
            if (next.getX() == current.getX() && next.getY() == current.getY()
                    && next.getZ() == current.getZ() && next.getYaw() == current.getYaw()
                    && next.getPitch() == current.getPitch()) continue;
            if (!state.camera.teleport(next)) continue;

            int oldChunkX = current.getBlockX() >> 4;
            int oldChunkZ = current.getBlockZ() >> 4;
            int newChunkX = next.getBlockX() >> 4;
            int newChunkZ = next.getBlockZ() >> 4;
            if (oldChunkX != newChunkX || oldChunkZ != newChunkZ) {
                sendChunkStatus(player, state, newChunkX, newChunkZ);
                renderHud(state, player, newChunkX, newChunkZ);
                renderGroundLines(state, player);
            }
        }
    }

    private Hud createHud() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("smcitymap", "dummy", "Карта чанков · север ↑");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        List<Team> rows = new java.util.ArrayList<>();
        List<String> entries = new java.util.ArrayList<>();
        for (int row = 0; row < 7; row++) {
            Team team = scoreboard.registerNewTeam("smmap" + row);
            String entry = ChatColor.values()[row].toString();
            team.addEntry(entry);
            objective.getScore(entry).setScore(7 - row);
            rows.add(team);
            entries.add(entry);
        }
        return new Hud(scoreboard, objective, List.copyOf(rows), List.copyOf(entries));
    }

    private void renderHud(State state, Player player, int selectedX, int selectedZ) {
        for (int row = 0; row < 7; row++) {
            int chunkZ = state.centerChunkZ + row - OUTLINE_RADIUS;
            StringBuilder line = new StringBuilder();
            for (int column = 0; column < 7; column++) {
                int chunkX = state.centerChunkX + column - OUTLINE_RADIUS;
                ChunkPosition chunk = new ChunkPosition(state.worldId, chunkX, chunkZ);
                String color = chunkX == selectedX && chunkZ == selectedZ ? ChatColor.AQUA.toString()
                        : state.owned.contains(chunk) ? ChatColor.GREEN.toString()
                        : state.available.contains(chunk) ? ChatColor.YELLOW.toString()
                        : ChatColor.RED.toString();
                line.append(color).append('■').append(ChatColor.DARK_GRAY).append(' ');
            }
            state.hud.rows.get(row).setPrefix(line.toString());
        }
    }

    private void renderGroundLines(State state, Player player) {
        World world = Bukkit.getWorld(state.worldId);
        if (world == null) return;
        Map<MarkerPosition, BlockData> desired = new HashMap<>();
        Location camera = state.camera.getLocation();
        int selectedX = camera.getBlockX() >> 4;
        int selectedZ = camera.getBlockZ() >> 4;
        for (int chunkX = state.centerChunkX - OUTLINE_RADIUS; chunkX <= state.centerChunkX + OUTLINE_RADIUS; chunkX++) {
            for (int chunkZ = state.centerChunkZ - OUTLINE_RADIUS; chunkZ <= state.centerChunkZ + OUTLINE_RADIUS; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
                ChunkPosition chunk = new ChunkPosition(state.worldId, chunkX, chunkZ);
                Material color = chunkX == selectedX && chunkZ == selectedZ ? SELECTED_LINE
                        : state.owned.contains(chunk) ? OWNED_LINE
                        : state.available.contains(chunk) ? AVAILABLE_LINE : UNAVAILABLE_LINE;
            addChunkLine(desired, world, chunkX, chunkZ, color, state.groundHeights);
            }
        }

        Map<MarkerPosition, BlockData> previous = clientLines.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        for (MarkerPosition old : List.copyOf(previous.keySet())) {
            if (desired.containsKey(old)) continue;
            restoreBlock(player, world, old);
            previous.remove(old);
        }
        for (Map.Entry<MarkerPosition, BlockData> line : desired.entrySet()) {
            if (line.getValue().equals(previous.get(line.getKey()))) continue;
            MarkerPosition position = line.getKey();
            player.sendBlockChange(new Location(world, position.x, position.y, position.z), line.getValue());
            previous.put(position, line.getValue());
        }
        state.renderedChunkX = camera.getBlockX() >> 4;
        state.renderedChunkZ = camera.getBlockZ() >> 4;
        state.lastGroundRefreshMillis = System.currentTimeMillis();
    }

    private void addChunkLine(Map<MarkerPosition, BlockData> lines, World world, int chunkX, int chunkZ, Material color,
                              Map<Column, Integer> groundHeights) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        for (int offset = 0; offset < 16; offset++) {
            addLineBlock(lines, world, minX + offset, minZ, color, groundHeights,
                    false, offset < 15, offset == 0 || offset == 15, offset > 0);
            addLineBlock(lines, world, minX + offset, minZ + 15, color, groundHeights,
                    offset == 0 || offset == 15, offset < 15, false, offset > 0);
            addLineBlock(lines, world, minX, minZ + offset, color, groundHeights,
                    offset > 0, offset == 0 || offset == 15, offset < 15, false);
            addLineBlock(lines, world, minX + 15, minZ + offset, color, groundHeights,
                    offset > 0, false, offset < 15, offset == 0 || offset == 15);
        }
    }

    private void addLineBlock(Map<MarkerPosition, BlockData> lines, World world, int x, int z, Material color,
                              Map<Column, Integer> groundHeights,
                              boolean north, boolean east, boolean south, boolean west) {
        int y = groundHeights.computeIfAbsent(new Column(x, z), ignored ->
                world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1);
        PaneState state = new PaneState(color, north, east, south, west);
        BlockData data = paneStates.computeIfAbsent(state, key -> {
            MultipleFacing pane = (MultipleFacing) Bukkit.createBlockData(key.material);
            pane.setFace(BlockFace.NORTH, key.north);
            pane.setFace(BlockFace.EAST, key.east);
            pane.setFace(BlockFace.SOUTH, key.south);
            pane.setFace(BlockFace.WEST, key.west);
            return pane;
        });
        lines.put(new MarkerPosition(x, y, z), data);
    }

    private void clearClientLines(Player player, State state) {
        Map<MarkerPosition, BlockData> lines = clientLines.remove(player.getUniqueId());
        if (lines == null) return;
        World world = Bukkit.getWorld(state.worldId);
        if (world == null) return;
        for (MarkerPosition position : lines.keySet()) restoreBlock(player, world, position);
    }

    private void restoreBlock(Player player, World world, MarkerPosition position) {
        if (!world.isChunkLoaded(position.x >> 4, position.z >> 4)) return;
        player.sendBlockChange(new Location(world, position.x, position.y, position.z),
                world.getBlockAt(position.x, position.y, position.z).getBlockData());
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        for (State state : sessions.values()) event.getPlayer().hideEntity(plugin, state.camera);
    }

    private void sendChunkStatus(Player player, State state, int chunkX, int chunkZ) {
        ChunkPosition current = new ChunkPosition(state.worldId, chunkX, chunkZ);
        String status = state.owned.contains(current) ? "земля города"
                : state.available.contains(current) ? "можно купить · /smcities buy"
                : "занято или недоступно";
        NamedTextColor color = state.owned.contains(current) ? NamedTextColor.GREEN
                : state.available.contains(current) ? NamedTextColor.YELLOW : NamedTextColor.RED;
        player.sendActionBar(Component.text("Чанк X " + chunkX + ", Z " + chunkZ + " · " + status, color));
    }

    @EventHandler public void onInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        State state = sessions.get(player.getUniqueId());
        if (state == null) return;
        org.bukkit.Input input = event.getInput();
        if (event.isAsynchronous()) {
            Bukkit.getScheduler().runTask(plugin, () -> applyInput(player, state, input));
        } else {
            applyInput(player, state, input);
        }
    }

    private void applyInput(Player player, State state, org.bukkit.Input input) {
        if (sessions.get(player.getUniqueId()) != state) return;
        if (state.input.update(input)) exit(player, true);
    }

    @EventHandler public void onCommand(PlayerCommandPreprocessEvent event) {
        State state = sessions.get(event.getPlayer().getUniqueId());
        if (state == null) return;
        String command = event.getMessage().strip().toLowerCase(java.util.Locale.ROOT);
        if (command.equals("/smcities exit") || command.equals("/smcities select")
                || command.equals("/smcities buy")) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("В режиме обзора доступны /smcities buy и /smcities exit.", NamedTextColor.YELLOW));
    }

    @EventHandler public void onInteract(PlayerInteractEvent event) {
        if (!isViewing(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            select(event.getPlayer());
        }
    }

    @EventHandler public void onArmSwing(PlayerArmSwingEvent event) {
        if (!isViewing(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        select(event.getPlayer());
    }

    @EventHandler public void onDrop(PlayerDropItemEvent event) {
        if (isViewing(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler public void onHeld(PlayerItemHeldEvent event) {
        if (isViewing(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (isViewing(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler public void onEntityInteractAt(PlayerInteractAtEntityEvent event) {
        if (isViewing(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isViewing(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        State state = sessions.get(event.getPlayer().getUniqueId());
        if (state != null && !event.getPlayer().getWorld().getUID().equals(state.worldId)) exit(event.getPlayer(), false);
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        State state = sessions.remove(event.getPlayer().getUniqueId());
        if (state != null) {
            Bukkit.getScheduler().runTask(plugin, () -> restore(event.getPlayer(), state, true));
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        State state = sessions.remove(event.getPlayer().getUniqueId());
        if (state != null) {
            restore(event.getPlayer(), state, true);
        }
    }

    public record Selection(UUID cityId, long revision, ChunkPosition target) { }
    private record Hud(Scoreboard scoreboard, Objective objective, List<Team> rows, List<String> entries) { }
    private record MarkerPosition(int x, int y, int z) { }
    private record Column(int x, int z) { }
    private record PaneState(Material material, boolean north, boolean east, boolean south, boolean west) { }

    private static final class MovementInput {
        private boolean forward;
        private boolean backward;
        private boolean left;
        private boolean right;
        private boolean sneaking;

        private boolean update(org.bukkit.Input input) {
            boolean startedSneaking = input.isSneak() && !sneaking;
            forward = input.isForward();
            backward = input.isBackward();
            left = input.isLeft();
            right = input.isRight();
            sneaking = input.isSneak();
            return startedSneaking;
        }

        private boolean moving() { return forward || backward || left || right; }
    }

    private static final class State {
        private final Location origin;
        private final GameMode gameMode;
        private final boolean allowFlight;
        private final boolean wasFlying;
        private final float flySpeed;
        private final float walkSpeed;
        private final UUID worldId;
        private final int centerChunkX;
        private final int centerChunkZ;
        private final double overviewY;
        private final UUID cityId;
        private final long revision;
        private final Set<ChunkPosition> owned;
        private final Set<ChunkPosition> available;
        private final Scoreboard previousScoreboard;
        private final Hud hud;
        private final ArmorStand camera;
        private final MovementInput input;
        private final Map<Column, Integer> groundHeights = new HashMap<>();
        private int renderedChunkX = Integer.MIN_VALUE;
        private int renderedChunkZ = Integer.MIN_VALUE;
        private long lastGroundRefreshMillis;

        private State(Location origin, GameMode gameMode, boolean allowFlight, boolean wasFlying,
                      float flySpeed, float walkSpeed, UUID worldId, int centerChunkX, int centerChunkZ,
                      double overviewY, UUID cityId, long revision, Set<ChunkPosition> owned, Set<ChunkPosition> available,
                      Scoreboard previousScoreboard, Hud hud, ArmorStand camera, MovementInput input) {
            this.origin = origin;
            this.gameMode = gameMode;
            this.allowFlight = allowFlight;
            this.wasFlying = wasFlying;
            this.flySpeed = flySpeed;
            this.walkSpeed = walkSpeed;
            this.worldId = worldId;
            this.centerChunkX = centerChunkX;
            this.centerChunkZ = centerChunkZ;
            this.overviewY = overviewY;
            this.cityId = cityId;
            this.revision = revision;
            this.owned = owned;
            this.available = available;
            this.previousScoreboard = previousScoreboard;
            this.hud = hud;
            this.camera = camera;
            this.input = input;
        }
    }
}
