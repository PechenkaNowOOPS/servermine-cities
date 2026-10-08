package ru.servermine.cities.protection;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.block.BlockFace;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.servermine.cities.api.ChunkPosition;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Denies outsider block changes and block use inside cached city claims. */
public final class CityProtectionListener implements Listener {
    private final JavaPlugin plugin;
    private final CityProtectionIndex index;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public CityProtectionListener(JavaPlugin plugin, CityProtectionIndex index) {
        this.plugin = plugin;
        this.index = index;
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        lastNotice.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (deny(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (deny(event.getPlayer(), event.getBlockPlaced().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (deny(event.getPlayer(), event.getClickedBlock().getLocation(), true)) {
            event.setCancelled(true);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onContainerOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Location location = event.getInventory().getLocation();
        if (location != null && deny(player, location, true)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> !index.explosionsAllowed(position(block)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> !index.explosionsAllowed(position(block)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonTouchesClaim(event.getBlock(), event.getBlocks(), event.getDirection(), false)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonTouchesClaim(event.getBlock(), event.getBlocks(), event.getDirection(), true)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (!index.allowsTransfer(position(event.getBlock()), position(event.getToBlock()))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireIgnite(BlockIgniteEvent event) {
        Block source = event.getIgnitingBlock();
        if (!index.fireAllowed(position(event.getBlock()))
                || (source != null && !index.allowsTransfer(position(source), position(event.getBlock())))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!index.fireAllowed(position(event.getBlock()))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCityUpgradeCombatProtection(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Entity attacker = event.getDamager();
        if (attacker instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            attacker = shooter;
        }
        ChunkPosition chunk = position(victim.getLocation());
        if (attacker instanceof Player attackerPlayer && (!index.pvpAllowed(chunk)
                || index.preventsTreatyCombat(attackerPlayer.getUniqueId(), victim.getUniqueId(), chunk)
                || index.wardsResidentFromPlayers(victim.getUniqueId(), chunk))) {
            event.setCancelled(true);
            attackerPlayer.sendMessage(Component.text("Дипломатический договор или городской щит запрещает эту атаку.", NamedTextColor.YELLOW));
        } else if (attacker instanceof Monster && index.guardsResidentFromMobs(victim.getUniqueId(), chunk)) {
            event.setCancelled(true);
        }
    }

    private boolean pistonTouchesClaim(Block piston, java.util.List<Block> moved, BlockFace direction, boolean retract) {
        if (index.isClaimed(position(piston))) return true;
        BlockFace opposite = direction.getOppositeFace();
        for (Block block : moved) {
            if (index.isClaimed(position(block))) return true;
            if (index.isClaimed(position(block.getRelative(direction)))) return true;
            if (retract && index.isClaimed(position(block.getRelative(opposite)))) return true;
        }
        return false;
    }

    private boolean deny(Player player, Location location) { return deny(player, location, false); }

    private boolean deny(Player player, Location location, boolean interaction) {
        if (player.hasPermission("servermine.cities.admin")) return false;
        ChunkPosition position = position(location);
        if (interaction ? index.canInteract(player.getUniqueId(), position)
                : index.canModify(player.getUniqueId(), position)) return false;
        long now = System.currentTimeMillis();
        Long previous = lastNotice.get(player.getUniqueId());
        if (previous == null || now - previous > 2000) {
            lastNotice.put(player.getUniqueId(), now);
            player.sendMessage(Component.text("Эта территория принадлежит городу.", NamedTextColor.RED));
        }
        return true;
    }

    private ChunkPosition position(Block block) {
        return new ChunkPosition(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4);
    }

    private ChunkPosition position(Location location) {
        return new ChunkPosition(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
}
