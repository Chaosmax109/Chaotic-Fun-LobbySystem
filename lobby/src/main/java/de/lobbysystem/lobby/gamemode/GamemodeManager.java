package de.lobbysystem.lobby.gamemode;

import de.lobbysystem.lobby.LobbyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GamemodeManager {

    private static final String[] EXAMPLES = {"bedwars.yml", "skywars.yml"};

    private final LobbyPlugin plugin;
    private final File folder;
    private final Map<String, Gamemode> gamemodes = new LinkedHashMap<>();

    public GamemodeManager(LobbyPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "gamemodes");
    }

    public File folder() {
        return folder;
    }

    /** Legt beim allerersten Start den Ordner mit Beispiel-Gamemodes an. */
    public void ensureFolder() {
        if (folder.exists()) return;
        folder.mkdirs();
        for (String example : EXAMPLES) {
            plugin.saveResource("gamemodes/" + example, false);
        }
    }

    public void load() {
        gamemodes.clear();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) return;
        List<Gamemode> list = new ArrayList<>();
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase();
            try {
                list.add(Gamemode.load(id, f, plugin.getLogger()));
            } catch (Exception e) {
                plugin.getLogger().severe("Konnte gamemodes/" + f.getName() + " nicht laden: " + e.getMessage());
            }
        }
        list.sort(Comparator.comparingInt((Gamemode g) -> g.order).thenComparing(g -> g.id));
        for (Gamemode g : list) gamemodes.put(g.id, g);
        plugin.getLogger().info(gamemodes.size() + " Gamemode(s) geladen.");
    }

    /** Lädt alle Lobby-Welten (type LOBBY_WORLD), die noch nicht geladen sind, und wendet deren Einstellungen an. */
    public void loadLobbyWorlds() {
        for (Gamemode g : gamemodes.values()) {
            if (!g.enabled || !g.hasOwnLobby() || g.worldName.isBlank()) continue;
            World world = Bukkit.getWorld(g.worldName);
            if (world == null) {
                if (!LobbyImporter.worldExistsOnDisk(g.worldName)) {
                    plugin.getLogger().warning("[" + g.id + "] Welt '" + g.worldName
                            + "' existiert nicht. Lade sie in den Ordner lobbies/ hoch.");
                    continue;
                }
                plugin.getLogger().info("Lade Lobby-Welt '" + g.worldName + "' ...");
                world = new WorldCreator(g.worldName).createWorld();
                if (world == null) {
                    plugin.getLogger().warning("[" + g.id + "] Welt '" + g.worldName + "' konnte nicht geladen werden.");
                    continue;
                }
            }
            if (g.difficulty != null) world.setDifficulty(g.difficulty);
            if (g.weatherLock) {
                world.setStorm(false);
                world.setThundering(false);
            }
            if (g.timeLock >= 0) world.setTime(g.timeLock);
        }
    }

    public Collection<Gamemode> all() {
        return gamemodes.values();
    }

    public Gamemode get(String id) {
        return id == null ? null : gamemodes.get(id.toLowerCase());
    }

    /** Gamemode, dessen eigene Lobby diese Welt ist (oder null). */
    public Gamemode byLobbyWorld(World world) {
        for (Gamemode g : gamemodes.values()) {
            if (g.enabled && g.hasOwnLobby() && g.worldName.equalsIgnoreCase(world.getName())) return g;
        }
        return null;
    }

    /** Main-Lobby oder eine Gamemode-Lobby? */
    public boolean isLobbyWorld(World world) {
        return world.equals(plugin.config().mainWorld()) || byLobbyWorld(world) != null;
    }

    /** Speichert die Position als Ziel des Gamemodes (Kommentare in der Datei bleiben erhalten). */
    public void setSpawn(Gamemode g, Location loc) throws IOException {
        YamlConfiguration c = YamlConfiguration.loadConfiguration(g.file);
        if (g.type == Gamemode.TargetType.LOBBY_WORLD) {
            c.set("target.use-world-spawn", false);
            loc.getWorld().setSpawnLocation(loc);
        } else if (g.type != Gamemode.TargetType.LOCATION) {
            c.set("target.type", "LOCATION");
        }
        c.set("target.world", loc.getWorld().getName());
        c.set("target.x", round(loc.getX()));
        c.set("target.y", round(loc.getY()));
        c.set("target.z", round(loc.getZ()));
        c.set("target.yaw", round(loc.getYaw()));
        c.set("target.pitch", round(loc.getPitch()));
        c.save(g.file);
        gamemodes.put(g.id, Gamemode.load(g.id, g.file, plugin.getLogger()));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
