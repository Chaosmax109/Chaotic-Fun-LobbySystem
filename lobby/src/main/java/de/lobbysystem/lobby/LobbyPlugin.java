package de.lobbysystem.lobby;

import de.lobbysystem.lobby.command.LobbyCommands;
import de.lobbysystem.lobby.config.MainConfig;
import de.lobbysystem.lobby.count.PlayerCountRegistry;
import de.lobbysystem.lobby.count.PlayerCountServer;
import de.lobbysystem.lobby.gamemode.Gamemode;
import de.lobbysystem.lobby.gamemode.GamemodeManager;
import de.lobbysystem.lobby.gamemode.LobbyImporter;
import de.lobbysystem.lobby.listener.CompassManager;
import de.lobbysystem.lobby.listener.PlayerListener;
import de.lobbysystem.lobby.listener.ProtectionListener;
import de.lobbysystem.lobby.menu.MenuManager;
import de.lobbysystem.lobby.menu.Teleporter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;

public final class LobbyPlugin extends JavaPlugin {

    private MainConfig config;
    private GamemodeManager gamemodes;
    private PlayerCountRegistry counts;
    private PlayerCountServer countServer;
    private CompassManager compass;
    private MenuManager menus;
    private Teleporter teleporter;
    private BukkitTask worldTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        counts = new PlayerCountRegistry();
        gamemodes = new GamemodeManager(this);
        compass = new CompassManager(this);
        menus = new MenuManager(this);
        teleporter = new Teleporter(this);

        getServer().getMessenger().registerOutgoingPluginChannel(this, Teleporter.PROXY_CHANNEL);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);

        LobbyCommands commands = new LobbyCommands(this);
        for (String name : new String[]{"lobby", "lobbysystem"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(commands);
                cmd.setTabCompleter(commands);
            }
        }

        reloadAll();
    }

    @Override
    public void onDisable() {
        if (countServer != null) countServer.stop();
        if (menus != null) menus.closeAll();
    }

    /** Lädt Config, importiert neue Lobbys, lädt Gamemodes und startet alles neu. */
    public void reloadAll() {
        reloadConfig();
        config = new MainConfig(getConfig(), getLogger());

        gamemodes.ensureFolder();
        new LobbyImporter(this).run(gamemodes);
        gamemodes.load();
        gamemodes.loadLobbyWorlds();

        World main = config.mainWorld();
        if (config.mainWeatherLock) {
            main.setStorm(false);
            main.setThundering(false);
        }

        restartCountServer();
        menus.reload();
        restartWorldTask();

        for (Player p : Bukkit.getOnlinePlayers()) compass.update(p);
    }

    private void restartCountServer() {
        if (countServer != null) {
            countServer.stop();
            countServer = null;
        }
        counts.setTimeout(config.timeoutMillis);
        if (!config.countsEnabled) return;
        countServer = new PlayerCountServer(counts, getLogger(), config.token);
        // Laufen mehrere Lobbys auf demselben Host, nimmt jede den nächsten freien Port
        int last = config.port + config.portRange - 1;
        for (int port = config.port; port <= last; port++) {
            try {
                countServer.start(config.bindAddress, port);
                return;
            } catch (IOException e) {
                getLogger().info("Port " + port + " ist belegt (" + e.getMessage() + ") - versuche nächsten ...");
            }
        }
        getLogger().severe("Spielerzahl-Empfang konnte auf keinem Port von " + config.port + " bis " + last
                + " starten! Erhöhe player-counts.port-range.");
        countServer = null;
    }

    /** Hält Uhrzeit in Main-Lobby und Gamemode-Lobbys fest. */
    private void restartWorldTask() {
        if (worldTask != null) worldTask.cancel();
        worldTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (config.mainTimeLock >= 0) config.mainWorld().setTime(config.mainTimeLock);
            for (Gamemode g : gamemodes.all()) {
                if (!g.enabled || g.timeLock < 0) continue;
                World w = g.lobbyWorld();
                if (w != null) w.setTime(g.timeLock);
            }
        }, 20L, 100L);
    }

    public MainConfig config() {
        return config;
    }

    public GamemodeManager gamemodes() {
        return gamemodes;
    }

    public PlayerCountRegistry counts() {
        return counts;
    }

    public CompassManager compass() {
        return compass;
    }

    public MenuManager menus() {
        return menus;
    }

    public Teleporter teleporter() {
        return teleporter;
    }
}
