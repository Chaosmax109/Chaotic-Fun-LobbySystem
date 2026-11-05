package de.lobbysystem.lobby.config;

import de.lobbysystem.lobby.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Liest die config.yml in typisierte Felder. */
public final class MainConfig {

    public enum Layout { CENTERED, FILL }

    // Main-Lobby
    public final String mainWorldName;
    public final boolean useCustomSpawn;
    public final double spawnX, spawnY, spawnZ;
    public final float spawnYaw, spawnPitch;
    public final boolean teleportOnJoin;
    public final boolean clearInventoryOnJoin;
    public final long mainTimeLock;
    public final boolean mainWeatherLock;

    // Kompass
    public final int compassSlot;
    public final Material compassMaterial;
    public final String compassName;
    public final List<String> compassLore;
    public final boolean compassGlow;
    public final boolean compassLocked;
    public final boolean compassEverywhere;

    // Menü
    public final String menuTitle;
    public final int menuRows; // 0 = auto
    public final Layout menuLayout;
    public final int maxPerRow;
    public final boolean spacing;
    public final boolean padding;
    public final boolean hideWithoutPermission;
    public final boolean closeOnClick;
    public final int updateIntervalTicks;

    // Schutz
    public final boolean protection;
    public final boolean noDamage, noHunger, noBuild, noItemDrop, voidTeleport;

    // Spielerzahlen
    public final boolean countsEnabled;
    public final String bindAddress;
    public final int port;
    public final int portRange;
    public final String token;
    public final long timeoutMillis;

    private final Map<String, String> messages = new HashMap<>();
    private final String prefix;

    public MainConfig(FileConfiguration c, Logger log) {
        mainWorldName = c.getString("main-lobby.world", "");
        useCustomSpawn = c.getBoolean("main-lobby.spawn.use-custom", false);
        spawnX = c.getDouble("main-lobby.spawn.x");
        spawnY = c.getDouble("main-lobby.spawn.y", 100);
        spawnZ = c.getDouble("main-lobby.spawn.z");
        spawnYaw = (float) c.getDouble("main-lobby.spawn.yaw");
        spawnPitch = (float) c.getDouble("main-lobby.spawn.pitch");
        teleportOnJoin = c.getBoolean("main-lobby.teleport-on-join", true);
        clearInventoryOnJoin = c.getBoolean("main-lobby.clear-inventory-on-join", true);
        mainTimeLock = c.getLong("main-lobby.time-lock", -1);
        mainWeatherLock = c.getBoolean("main-lobby.weather-lock", true);

        compassSlot = clamp(c.getInt("compass.slot", 5), 1, 9) - 1;
        Material mat = Material.matchMaterial(c.getString("compass.material", "COMPASS"));
        if (mat == null || !mat.isItem() || mat.isAir()) {
            log.warning("compass.material ist ungültig - benutze COMPASS");
            mat = Material.COMPASS;
        }
        compassMaterial = mat;
        compassName = c.getString("compass.name", "<gold>Spielmodi");
        compassLore = c.getStringList("compass.lore");
        compassGlow = c.getBoolean("compass.glow", false);
        compassLocked = c.getBoolean("compass.locked", true);
        compassEverywhere = c.getBoolean("compass.everywhere", false);

        menuTitle = c.getString("menu.title", "Spielmodi");
        String rows = String.valueOf(c.get("menu.rows", "auto")).trim();
        int parsedRows = 0;
        if (!rows.equalsIgnoreCase("auto")) {
            try {
                parsedRows = clamp(Integer.parseInt(rows), 1, 6);
            } catch (NumberFormatException e) {
                log.warning("menu.rows muss 'auto' oder 1-6 sein - benutze auto");
            }
        }
        menuRows = parsedRows;
        Layout layout;
        try {
            layout = Layout.valueOf(c.getString("menu.layout", "centered").toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warning("menu.layout muss 'centered' oder 'fill' sein - benutze centered");
            layout = Layout.CENTERED;
        }
        menuLayout = layout;
        maxPerRow = clamp(c.getInt("menu.max-per-row", 7), 1, 9);
        spacing = c.getBoolean("menu.spacing", true);
        padding = c.getBoolean("menu.padding", true);
        hideWithoutPermission = c.getBoolean("menu.hide-without-permission", true);
        closeOnClick = c.getBoolean("menu.close-on-click", true);
        updateIntervalTicks = Math.max(1, (int) Math.round(c.getDouble("menu.update-interval", 2) * 20));

        protection = c.getBoolean("protection.enabled", true);
        noDamage = c.getBoolean("protection.no-damage", true);
        noHunger = c.getBoolean("protection.no-hunger", true);
        noBuild = c.getBoolean("protection.no-build", true);
        noItemDrop = c.getBoolean("protection.no-item-drop", true);
        voidTeleport = c.getBoolean("protection.void-teleport", true);

        countsEnabled = c.getBoolean("player-counts.enabled", true);
        bindAddress = c.getString("player-counts.bind-address", "0.0.0.0");
        port = c.getInt("player-counts.port", 8765);
        portRange = clamp(c.getInt("player-counts.port-range", 5), 1, 50);
        token = c.getString("player-counts.token", "");
        timeoutMillis = Math.max(1, c.getLong("player-counts.timeout-seconds", 15)) * 1000L;
        if (countsEnabled && (token.isBlank() || token.equals("BITTE-AENDERN"))) {
            log.warning("player-counts.token ist nicht gesetzt! Bitte ein eigenes Passwort eintragen.");
        }

        ConfigurationSection msg = c.getConfigurationSection("messages");
        if (msg != null) {
            for (String key : msg.getKeys(false)) {
                messages.put(key, msg.getString(key, ""));
            }
        }
        prefix = messages.getOrDefault("prefix", "");
    }

    public World mainWorld() {
        if (mainWorldName != null && !mainWorldName.isBlank()) {
            World w = Bukkit.getWorld(mainWorldName);
            if (w != null) return w;
        }
        return Bukkit.getWorlds().getFirst();
    }

    public Location mainSpawn() {
        World w = mainWorld();
        if (!useCustomSpawn) return w.getSpawnLocation();
        return new Location(w, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
    }

    /** Roher Nachrichtentext (für Platzhalter-Werte im Menü). */
    public String raw(String key) {
        return messages.getOrDefault(key, "");
    }

    /** Nachricht mit Prefix und Platzhaltern. */
    public Component msg(String key, String... placeholders) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            map.put(placeholders[i], placeholders[i + 1]);
        }
        return Text.parse(prefix + Text.replace(raw(key), map));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
