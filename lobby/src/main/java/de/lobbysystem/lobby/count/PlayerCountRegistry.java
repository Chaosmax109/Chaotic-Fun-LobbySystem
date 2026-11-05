package de.lobbysystem.lobby.count;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Speichert die von den Gamemode-Servern gemeldeten Spielerzahlen (thread-sicher). */
public final class PlayerCountRegistry {

    public record Entry(int players, int maxPlayers, long lastUpdate) {
    }

    /** Ergebnis für einen Gamemode: Summe über alle Server, die nicht abgelaufen sind. */
    public record Result(int players, int maxPlayers, int servers) {
        public boolean online() {
            return servers > 0;
        }
    }

    private final Map<String, Map<String, Entry>> data = new ConcurrentHashMap<>();
    private volatile long timeoutMillis = 15_000;

    public void setTimeout(long millis) {
        this.timeoutMillis = millis;
    }

    public void update(String gamemode, String server, int players, int max) {
        data.computeIfAbsent(gamemode.toLowerCase(), k -> new ConcurrentHashMap<>())
                .put(server, new Entry(Math.max(0, players), Math.max(0, max), System.currentTimeMillis()));
    }

    public void remove(String gamemode, String server) {
        Map<String, Entry> servers = data.get(gamemode.toLowerCase());
        if (servers != null) servers.remove(server);
    }

    public Result get(String gamemode) {
        Map<String, Entry> servers = data.get(gamemode.toLowerCase());
        if (servers == null) return new Result(0, 0, 0);
        long now = System.currentTimeMillis();
        int players = 0, max = 0, count = 0;
        for (var it = servers.values().iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (now - e.lastUpdate() > timeoutMillis) {
                it.remove();
                continue;
            }
            players += e.players();
            max += e.maxPlayers();
            count++;
        }
        return new Result(players, max, count);
    }

    public Map<String, Map<String, Entry>> snapshot() {
        return data;
    }
}
