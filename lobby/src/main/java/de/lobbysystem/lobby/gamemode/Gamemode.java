package de.lobbysystem.lobby.gamemode;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

/** Ein Eintrag im Gamemode-Switcher (eine Datei in gamemodes/). */
public final class Gamemode {

    public enum TargetType { LOBBY_WORLD, LOCATION, SERVER, MAIN_LOBBY }

    public final String id;
    public final File file;

    public final boolean enabled;
    public final String displayName;
    public final int order;
    public final int slot;
    public final String permission;

    public final Material material;
    public final int amount;
    public final boolean glow;
    public final String itemModel;
    public final List<String> lore;

    public final TargetType type;
    public final String worldName;
    public final boolean useWorldSpawn;
    public final double x, y, z;
    public final float yaw, pitch;
    public final String server;

    public final String apiId;

    // Nur bei LOBBY_WORLD
    public final long timeLock;
    public final boolean weatherLock;
    public final Difficulty difficulty;

    private Gamemode(String id, File file, YamlConfiguration c, Logger log) {
        this.id = id;
        this.file = file;
        enabled = c.getBoolean("enabled", true);
        displayName = c.getString("display-name", id);
        order = c.getInt("order", 100);
        slot = c.getInt("slot", -1);
        permission = c.getString("permission", "");

        Material mat = Material.matchMaterial(c.getString("icon.material", "STONE"));
        if (mat == null || !mat.isItem() || mat.isAir()) {
            log.warning("[" + id + "] icon.material ist ungültig - benutze BARRIER");
            mat = Material.BARRIER;
        }
        material = mat;
        amount = Math.max(1, Math.min(99, c.getInt("icon.amount", 1)));
        glow = c.getBoolean("icon.glow", false);
        itemModel = c.getString("icon.item-model", "");
        lore = c.getStringList("icon.lore");

        TargetType t;
        try {
            t = TargetType.valueOf(c.getString("target.type", "LOCATION").toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warning("[" + id + "] target.type ist ungültig - benutze LOCATION");
            t = TargetType.LOCATION;
        }
        type = t;
        worldName = c.getString("target.world", "");
        useWorldSpawn = c.getBoolean("target.use-world-spawn", true);
        x = c.getDouble("target.x");
        y = c.getDouble("target.y", 100);
        z = c.getDouble("target.z");
        yaw = (float) c.getDouble("target.yaw");
        pitch = (float) c.getDouble("target.pitch");
        server = c.getString("target.server", "");

        apiId = c.getString("api-id", "").trim().toLowerCase();

        timeLock = c.getLong("lobby-world.time-lock", -1);
        weatherLock = c.getBoolean("lobby-world.weather-lock", true);
        Difficulty diff = null;
        String d = c.getString("lobby-world.difficulty", "");
        if (!d.isBlank()) {
            try {
                diff = Difficulty.valueOf(d.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warning("[" + id + "] lobby-world.difficulty ist ungültig");
            }
        }
        difficulty = diff;
    }

    public static Gamemode load(String id, File file, Logger log) {
        return new Gamemode(id, file, YamlConfiguration.loadConfiguration(file), log);
    }

    public boolean hasOwnLobby() {
        return type == TargetType.LOBBY_WORLD;
    }

    public boolean hasApi() {
        return !apiId.isEmpty();
    }

    /** Welt der eigenen Lobby (nur LOBBY_WORLD), sonst null. */
    public World lobbyWorld() {
        return hasOwnLobby() && !worldName.isBlank() ? Bukkit.getWorld(worldName) : null;
    }

    /**
     * Zielposition für LOBBY_WORLD / LOCATION.
     * @param fallbackWorld Welt, wenn bei LOCATION keine angegeben ist (Main-Lobby)
     * @return null, wenn die Welt nicht geladen ist
     */
    public Location resolveLocation(World fallbackWorld) {
        World w;
        if (worldName == null || worldName.isBlank()) {
            if (type == TargetType.LOBBY_WORLD) return null;
            w = fallbackWorld;
        } else {
            w = Bukkit.getWorld(worldName);
        }
        if (w == null) return null;
        if (type == TargetType.LOBBY_WORLD && useWorldSpawn) return w.getSpawnLocation();
        return new Location(w, x, y, z, yaw, pitch);
    }
}
