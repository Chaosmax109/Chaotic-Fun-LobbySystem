package de.lobbysystem.lobby.listener;

import de.lobbysystem.lobby.LobbyPlugin;
import de.lobbysystem.lobby.config.MainConfig;
import de.lobbysystem.lobby.gamemode.Gamemode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/** Schutz für die Main-Lobby und alle Gamemode-Lobbys. */
public final class ProtectionListener implements Listener {

    private final LobbyPlugin plugin;

    public ProtectionListener(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean protectedFor(Player p) {
        return plugin.config().protection
                && !p.hasPermission("lobbysystem.bypass")
                && plugin.gamemodes().isLobbyWorld(p.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        MainConfig cfg = plugin.config();
        if (!cfg.protection || !plugin.gamemodes().isLobbyWorld(p.getWorld())) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID && cfg.voidTeleport) {
            event.setCancelled(true);
            p.setFallDistance(0);
            p.teleportAsync(spawnOf(p.getWorld()));
            return;
        }
        if (cfg.noDamage && !p.hasPermission("lobbysystem.bypass")) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && plugin.config().noHunger && protectedFor(p)) {
            event.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.config().noBuild && protectedFor(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.config().noBuild && protectedFor(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.config().noItemDrop && protectedFor(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (!event.toWeatherState()) return;
        World w = event.getWorld();
        if (w.equals(plugin.config().mainWorld())) {
            if (plugin.config().mainWeatherLock) event.setCancelled(true);
            return;
        }
        Gamemode g = plugin.gamemodes().byLobbyWorld(w);
        if (g != null && g.weatherLock) event.setCancelled(true);
    }

    private Location spawnOf(World world) {
        if (world.equals(plugin.config().mainWorld())) return plugin.config().mainSpawn();
        Gamemode g = plugin.gamemodes().byLobbyWorld(world);
        if (g != null) {
            Location loc = g.resolveLocation(world);
            if (loc != null) return loc;
        }
        return world.getSpawnLocation();
    }
}
