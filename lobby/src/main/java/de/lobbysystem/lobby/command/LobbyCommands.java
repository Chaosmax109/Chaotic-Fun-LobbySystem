package de.lobbysystem.lobby.command;

import de.lobbysystem.lobby.LobbyPlugin;
import de.lobbysystem.lobby.config.MainConfig;
import de.lobbysystem.lobby.count.PlayerCountRegistry;
import de.lobbysystem.lobby.gamemode.Gamemode;
import de.lobbysystem.lobby.util.Text;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** /lobby und /lobbysystem */
public final class LobbyCommands implements TabExecutor {

    private static final List<String> SUB = List.of("reload", "setspawn", "setmainspawn", "list", "menu");

    private final LobbyPlugin plugin;

    public LobbyCommands(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        MainConfig cfg = plugin.config();

        if (command.getName().equalsIgnoreCase("lobby")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(cfg.msg("only-players"));
                return true;
            }
            plugin.teleporter().toMainLobby(p);
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Text.parse(cfg.raw("prefix") + "<gray>/ls <white>reload | setspawn <id> | setmainspawn | list | menu"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(plugin.config().msg("reloaded", "%count%", String.valueOf(plugin.gamemodes().all().size())));
            }
            case "setspawn" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(cfg.msg("only-players"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Text.parse(cfg.raw("prefix") + "<gray>/ls setspawn <gamemode-id>"));
                    return true;
                }
                Gamemode g = plugin.gamemodes().get(args[1]);
                if (g == null) {
                    sender.sendMessage(cfg.msg("unknown-gamemode", "%id%", args[1]));
                    return true;
                }
                try {
                    plugin.gamemodes().setSpawn(g, p.getLocation());
                    sender.sendMessage(cfg.msg("spawn-set", "%name%", g.displayName));
                } catch (IOException e) {
                    sender.sendMessage(Text.parse("<red>Fehler beim Speichern: " + e.getMessage()));
                }
            }
            case "setmainspawn" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(cfg.msg("only-players"));
                    return true;
                }
                Location l = p.getLocation();
                var c = plugin.getConfig();
                c.set("main-lobby.world", l.getWorld().getName());
                c.set("main-lobby.spawn.use-custom", true);
                c.set("main-lobby.spawn.x", round(l.getX()));
                c.set("main-lobby.spawn.y", round(l.getY()));
                c.set("main-lobby.spawn.z", round(l.getZ()));
                c.set("main-lobby.spawn.yaw", round(l.getYaw()));
                c.set("main-lobby.spawn.pitch", round(l.getPitch()));
                plugin.saveConfig();
                plugin.reloadAll();
                sender.sendMessage(plugin.config().msg("main-spawn-set"));
            }
            case "list" -> {
                sender.sendMessage(Text.parse(cfg.raw("prefix") + "<gold>Gamemodes:"));
                for (Gamemode g : plugin.gamemodes().all()) {
                    StringBuilder sb = new StringBuilder("<dark_gray> - <white>").append(g.id)
                            .append(" <dark_gray>(").append(g.enabled ? "<green>an" : "<red>aus").append("<dark_gray>) <gray>")
                            .append(g.type);
                    Integer lobby = plugin.menus().lobbyPlayers(g);
                    if (lobby != null) sb.append(" <gray>Lobby: <white>").append(lobby);
                    if (g.hasApi()) {
                        PlayerCountRegistry.Result r = plugin.counts().get(g.apiId);
                        sb.append(" <gray>Spielend: <white>").append(r.players())
                                .append(" <gray>Server: <white>").append(r.servers());
                    }
                    sender.sendMessage(Text.parse(sb.toString()));
                }
            }
            case "menu" -> {
                if (sender instanceof Player p) plugin.menus().open(p);
                else sender.sendMessage(cfg.msg("only-players"));
            }
            default -> sender.sendMessage(Text.parse(cfg.raw("prefix") + "<gray>/ls <white>reload | setspawn <id> | setmainspawn | list | menu"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("lobby")) return out;
        if (args.length == 1) {
            for (String s : SUB) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setspawn")) {
            for (Gamemode g : plugin.gamemodes().all()) if (g.id.startsWith(args[1].toLowerCase())) out.add(g.id);
        }
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
