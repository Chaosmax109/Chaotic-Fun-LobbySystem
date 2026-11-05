package de.lobbysystem.lobby.listener;

import de.lobbysystem.lobby.LobbyPlugin;
import de.lobbysystem.lobby.config.MainConfig;
import de.lobbysystem.lobby.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Erstellt, verteilt und erkennt den Gamemode-Kompass. */
public final class CompassManager {

    private final LobbyPlugin plugin;
    private final NamespacedKey key;

    public CompassManager(LobbyPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "gamemode_compass");
    }

    public ItemStack create() {
        MainConfig cfg = plugin.config();
        ItemStack item = new ItemStack(cfg.compassMaterial);
        item.editMeta(meta -> {
            meta.displayName(Text.item(cfg.compassName));
            List<Component> lore = new ArrayList<>();
            for (String line : cfg.compassLore) lore.add(Text.item(line));
            meta.lore(lore);
            if (cfg.compassGlow) meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
        });
        return item;
    }

    public boolean isCompass(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }

    public boolean shouldHave(Player player) {
        return plugin.config().compassEverywhere || plugin.gamemodes().isLobbyWorld(player.getWorld());
    }

    /** Gibt/entfernt den Kompass, je nachdem in welcher Welt der Spieler ist. */
    public void update(Player player) {
        if (shouldHave(player)) give(player);
        else remove(player);
    }

    public void give(Player player) {
        PlayerInventory inv = player.getInventory();
        int slot = plugin.config().compassSlot;
        // Alte Kompasse an anderen Stellen entfernen (z.B. nach Slot-Änderung in der Config)
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (i != slot && isCompass(contents[i])) inv.setItem(i, null);
        }
        inv.setItem(slot, create());
    }

    public void remove(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isCompass(contents[i])) inv.setItem(i, null);
        }
    }
}
