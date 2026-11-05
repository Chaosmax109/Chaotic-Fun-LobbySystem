package de.lobbysystem.lobby.menu;

import de.lobbysystem.lobby.LobbyPlugin;
import de.lobbysystem.lobby.config.MainConfig;
import de.lobbysystem.lobby.count.PlayerCountRegistry;
import de.lobbysystem.lobby.gamemode.Gamemode;
import de.lobbysystem.lobby.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MenuManager {

    private final LobbyPlugin plugin;
    private final Set<String> warned = new HashSet<>();
    private BukkitTask refreshTask;

    public MenuManager(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    /** Nach (Re-)Load: Refresh-Task neu starten, offene Menüs schließen. */
    public void reload() {
        warned.clear();
        if (refreshTask != null) refreshTask.cancel();
        closeAll();
        int interval = plugin.config().updateIntervalTicks;
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOpen, interval, interval);
    }

    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof GamemodeMenu) p.closeInventory();
        }
    }

    public void open(Player player) {
        MainConfig cfg = plugin.config();
        List<Gamemode> visible = new ArrayList<>();
        for (Gamemode g : plugin.gamemodes().all()) {
            if (!g.enabled) continue;
            if (cfg.hideWithoutPermission && !hasPermission(player, g)) continue;
            visible.add(g);
        }

        MenuLayout.Result layout = MenuLayout.compute(visible, cfg, msg -> {
            if (warned.add(msg)) plugin.getLogger().warning(msg);
        });

        Map<Integer, String> slots = new HashMap<>();
        layout.slots().forEach((slot, g) -> slots.put(slot, g.id));

        GamemodeMenu menu = new GamemodeMenu(slots);
        Inventory inv = Bukkit.createInventory(menu, layout.rows() * 9, Text.parse(cfg.menuTitle));
        menu.setInventory(inv);
        render(menu);
        player.openInventory(inv);
    }

    /** Setzt / aktualisiert alle Icons (keine Füll-Items). */
    public void render(GamemodeMenu menu) {
        Inventory inv = menu.getInventory();
        for (Map.Entry<Integer, String> e : menu.slots().entrySet()) {
            Gamemode g = plugin.gamemodes().get(e.getValue());
            inv.setItem(e.getKey(), g == null ? null : buildIcon(g));
        }
    }

    private void refreshOpen() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof GamemodeMenu menu) {
                render(menu);
            }
        }
    }

    public static boolean hasPermission(Player player, Gamemode g) {
        return g.permission == null || g.permission.isBlank() || player.hasPermission(g.permission);
    }

    /** Spieler in der eigenen Lobby des Gamemodes, oder null wenn er keine hat. */
    public Integer lobbyPlayers(Gamemode g) {
        if (g.type == Gamemode.TargetType.MAIN_LOBBY) return plugin.config().mainWorld().getPlayers().size();
        if (!g.hasOwnLobby()) return null;
        World w = g.lobbyWorld();
        return w == null ? 0 : w.getPlayers().size();
    }

    public ItemStack buildIcon(Gamemode g) {
        MainConfig cfg = plugin.config();
        Integer lobby = lobbyPlayers(g);
        PlayerCountRegistry.Result api = g.hasApi() ? plugin.counts().get(g.apiId) : null;

        String none = cfg.raw("value-none");
        Map<String, String> ph = new HashMap<>();
        ph.put("%name%", g.displayName);
        ph.put("%lobby_players%", lobby == null ? none : String.valueOf(lobby));
        if (api != null) {
            ph.put("%playing%", api.online() ? String.valueOf(api.players()) : cfg.raw("value-offline"));
            ph.put("%servers%", String.valueOf(api.servers()));
            ph.put("%status%", api.online() ? cfg.raw("status-online") : cfg.raw("status-offline"));
        } else {
            ph.put("%playing%", none);
            ph.put("%servers%", none);
            ph.put("%status%", none);
        }
        if (lobby == null && api == null) {
            ph.put("%total%", none);
        } else {
            int total = (lobby == null ? 0 : lobby) + (api == null ? 0 : api.players());
            ph.put("%total%", String.valueOf(total));
        }

        List<Component> lore = new ArrayList<>();
        for (String line : g.lore) {
            // Zeilen ausblenden, deren Platzhalter für diesen Gamemode keinen Sinn ergeben
            if (lobby == null && line.contains("%lobby_players%")) continue;
            if (api == null && (line.contains("%playing%") || line.contains("%servers%") || line.contains("%status%"))) continue;
            if (lobby == null && api == null && line.contains("%total%")) continue;
            lore.add(Text.item(Text.replace(line, ph)));
        }
        // Doppelte/abschließende Leerzeilen nach dem Ausblenden entfernen
        cleanupEmptyLines(lore);

        ItemStack item = new ItemStack(g.material, g.amount);
        item.editMeta(meta -> {
            meta.displayName(Text.item(g.displayName));
            meta.lore(lore);
            if (g.glow) meta.setEnchantmentGlintOverride(true);
            if (g.itemModel != null && !g.itemModel.isBlank()) {
                NamespacedKey key = NamespacedKey.fromString(g.itemModel.toLowerCase());
                if (key != null) meta.setItemModel(key);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        return item;
    }

    private static void cleanupEmptyLines(List<Component> lore) {
        for (int i = lore.size() - 1; i >= 0; i--) {
            boolean empty = isEmpty(lore.get(i));
            boolean prevEmpty = i > 0 && isEmpty(lore.get(i - 1));
            if (empty && (prevEmpty || i == lore.size() - 1)) lore.remove(i);
        }
    }

    private static boolean isEmpty(Component c) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c).isBlank();
    }
}
