package de.lobbysystem.lobby.listener;

import de.lobbysystem.lobby.LobbyPlugin;
import de.lobbysystem.lobby.config.MainConfig;
import de.lobbysystem.lobby.gamemode.Gamemode;
import de.lobbysystem.lobby.menu.GamemodeMenu;
import de.lobbysystem.lobby.menu.MenuManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/** Join, Kompass-Benutzung, Kompass-Sperre und Menü-Klicks. */
public final class PlayerListener implements Listener {

    private final LobbyPlugin plugin;

    public PlayerListener(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    private CompassManager compass() {
        return plugin.compass();
    }

    /**
     * Kompass gesperrt? Gilt für ALLE Spieler - auch Admins/OPs mit "*"-Rechten.
     * (lobbysystem.bypass gilt nur noch für den Lobby-Schutz, z.B. Bauen.)
     */
    private boolean locked(Player p) {
        return plugin.config().compassLocked;
    }

    /**
     * Sicherheitsnetz (v.a. für den Kreativmodus, dort entscheidet der Client mit):
     * Im nächsten Tick prüfen, ob der Kompass noch im richtigen Slot liegt, und ihn sonst zurücksetzen.
     */
    private void restoreLater(Player p) {
        if (!locked(p)) return;
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (compass().isCompass(p.getItemOnCursor())) p.setItemOnCursor(null);
            if (!compass().shouldHave(p)) return;
            int slot = plugin.config().compassSlot;
            ItemStack[] contents = p.getInventory().getContents();
            boolean ok = compass().isCompass(contents[slot]);
            for (int i = 0; ok && i < contents.length; i++) {
                if (i != slot && compass().isCompass(contents[i])) ok = false;
            }
            if (!ok) compass().give(p);
        });
    }

    /** Falls ein Kompass doch auf dem Boden landet (z.B. Kreativ-Drop), sofort entfernen. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSpawn(org.bukkit.event.entity.ItemSpawnEvent event) {
        if (plugin.config().compassLocked && compass().isCompass(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- Join / Welt

    /**
     * Inventar so FRÜH wie möglich leeren (LOWEST), damit Items anderer Plugins
     * (z.B. ein Lobby-Switcher auf Slot 9), die danach gegeben werden, nicht gelöscht werden.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoinEarly(PlayerJoinEvent event) {
        if (plugin.config().clearInventoryOnJoin) event.getPlayer().getInventory().clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        MainConfig cfg = plugin.config();
        if (cfg.teleportOnJoin) {
            p.teleportAsync(cfg.mainSpawn()).thenRun(() -> compass().update(p));
        }
        compass().update(p);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        compass().update(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) compass().update(p);
        });
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(compass()::isCompass);
    }

    // ---------------------------------------------------------------- Kompass benutzen

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) return;
        if (!compass().isCompass(event.getItem())) return;
        event.setCancelled(true);
        plugin.menus().open(event.getPlayer());
    }

    // ---------------------------------------------------------------- Menü + Kompass-Sperre

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;

        // Gamemode-Menü
        if (event.getView().getTopInventory().getHolder(false) instanceof GamemodeMenu menu) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
            String id = menu.gamemodeAt(event.getSlot());
            if (id == null) return;
            Gamemode g = plugin.gamemodes().get(id);
            if (g == null) return;
            if (!MenuManager.hasPermission(p, g)) {
                p.sendMessage(plugin.config().msg("no-permission"));
                return;
            }
            if (plugin.config().closeOnClick) p.closeInventory();
            plugin.teleporter().send(p, g);
            return;
        }

        // Kompass darf nicht bewegt werden
        if (!locked(p)) return;
        ItemStack hotbarItem = null;
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            hotbarItem = p.getInventory().getItem(event.getHotbarButton());
        }
        if (compass().isCompass(event.getCurrentItem())
                || compass().isCompass(event.getCursor())
                || compass().isCompass(hotbarItem)
                || (event.getClick() == ClickType.SWAP_OFFHAND && compass().isCompass(p.getInventory().getItemInOffHand()))
                || (event.getClickedInventory() == p.getInventory() && event.getSlot() == plugin.config().compassSlot
                    && compass().shouldHave(p))) {
            event.setCancelled(true);
        }
        restoreLater(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof GamemodeMenu) {
            event.setCancelled(true);
            return;
        }
        if (event.getWhoClicked() instanceof Player p && locked(p) && compass().isCompass(event.getOldCursor())) {
            event.setCancelled(true);
        }
        if (event.getWhoClicked() instanceof Player p) restoreLater(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (locked(event.getPlayer()) && compass().isCompass(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
        restoreLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (locked(event.getPlayer())
                && (compass().isCompass(event.getMainHandItem()) || compass().isCompass(event.getOffHandItem()))) {
            event.setCancelled(true);
        }
        restoreLater(event.getPlayer());
    }
}
