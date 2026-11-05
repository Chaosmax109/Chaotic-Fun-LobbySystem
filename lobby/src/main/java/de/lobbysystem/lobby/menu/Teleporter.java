package de.lobbysystem.lobby.menu;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.lobbysystem.lobby.LobbyPlugin;
import de.lobbysystem.lobby.config.MainConfig;
import de.lobbysystem.lobby.gamemode.Gamemode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Bringt Spieler zum Ziel eines Gamemodes. */
public final class Teleporter {

    public static final String PROXY_CHANNEL = "BungeeCord";

    private final LobbyPlugin plugin;

    public Teleporter(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public void send(Player player, Gamemode g) {
        MainConfig cfg = plugin.config();
        switch (g.type) {
            case MAIN_LOBBY -> toMainLobby(player);
            case SERVER -> {
                if (g.server == null || g.server.isBlank()) {
                    player.sendMessage(cfg.msg("world-not-found", "%name%", g.displayName));
                    return;
                }
                player.sendMessage(cfg.msg("connecting", "%name%", g.displayName));
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("Connect");
                out.writeUTF(g.server);
                player.sendPluginMessage(plugin, PROXY_CHANNEL, out.toByteArray());
            }
            case LOBBY_WORLD, LOCATION -> {
                Location loc = g.resolveLocation(cfg.mainWorld());
                if (loc == null) {
                    player.sendMessage(cfg.msg("world-not-found", "%name%", g.displayName));
                    return;
                }
                player.teleportAsync(loc).thenAccept(success -> {
                    if (success) player.sendMessage(cfg.msg("teleported", "%name%", g.displayName));
                });
            }
        }
    }

    public void toMainLobby(Player player) {
        MainConfig cfg = plugin.config();
        player.teleportAsync(cfg.mainSpawn()).thenAccept(success -> {
            if (success) player.sendMessage(cfg.msg("main-lobby"));
        });
    }
}
