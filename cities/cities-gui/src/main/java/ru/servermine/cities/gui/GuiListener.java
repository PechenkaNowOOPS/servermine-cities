package ru.servermine.cities.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public final class GuiListener implements Listener {
    private final JavaPlugin plugin;
    private final CityBookService books;
    private final MenuManager menus;
    public GuiListener(JavaPlugin plugin, CityBookService books, MenuManager menus) {
        this.plugin = plugin; this.books = books; this.menus = menus;
    }
    @EventHandler public void onBookUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!books.isCityBook(event.getItem())) return;
        event.setCancelled(true);
        menus.open(event.getPlayer(), MenuType.MAIN);
    }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (menus.isMarketEscrowItem(event.getCurrentItem()) || menus.isMarketEscrowItem(event.getCursor())) {
            event.setCancelled(true); return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Right click is used only for declining incoming city treaty proposals.
        if (event.getClick() != ClickType.LEFT
                && !(event.getClick() == ClickType.RIGHT && holder.session().menuType() == MenuType.DIPLOMACY)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        boolean rightClick = event.getClick() == ClickType.RIGHT;
        // Bukkit requires opening/closing a different inventory after the click event.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) menus.handleClick(player, holder, slot, rightClick);
        });
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (menus.isMarketEscrowItem(event.getOldCursor())) { event.setCancelled(true); return; }
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) menus.forget(player);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { menus.clearPlayer(event.getPlayer()); }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        menus.reconcileMarketListings(event.getPlayer());
        menus.reconcileMarketDeliveries(event.getPlayer());
    }
    @EventHandler public void onCityNameChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        boolean cityName = menus.isAwaitingCityName(player.getUniqueId());
        boolean transferAmount = menus.isAwaitingTransferAmount(player.getUniqueId());
        boolean marketPrice = menus.isAwaitingMarketPrice(player.getUniqueId());
        if (!cityName && !transferAmount && !marketPrice) return;
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (cityName) menus.acceptCityName(player, input);
            else if (transferAmount) menus.acceptTransferAmount(player, input);
            else menus.acceptMarketPrice(player, input);
        });
    }
    @EventHandler public void onDrop(PlayerDropItemEvent event) {
        if (menus.isMarketEscrowItem(event.getItemDrop().getItemStack())) { event.setCancelled(true); return; }
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent event) {
        if (menus.isMarketEscrowItem(event.getMainHandItem()) || menus.isMarketEscrowItem(event.getOffHandItem())) { event.setCancelled(true); return; }
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }
}
