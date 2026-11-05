package de.lobbysystem.bridge;

import com.google.gson.JsonObject;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/** Meldet die Spielerzahl dieses Gamemode-Servers an die Lobby-Server. */
public final class LobbyBridge extends JavaPlugin implements Listener {

    private static volatile LobbyBridge instance;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<String, String> lastResult = new ConcurrentHashMap<>();

    private String gamemodeId;
    private String serverName;
    private List<String> lobbyUrls = new ArrayList<>();
    private String token;
    private boolean ignoreSpectators;
    private boolean logErrors;
    private BukkitTask task;
    private boolean pushScheduled;
    private volatile int lastPlayers = -1;
    private volatile String overallState;

    static LobbyBridge instance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
        // Lobby sofort informieren, dass dieser Server weg ist (kurz blockierend)
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (String url : lobbyUrls) futures.add(post(url, "/remove", baseJson()));
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        instance = null;
    }

    private void loadSettings() {
        reloadConfig();
        var c = getConfig();
        gamemodeId = c.getString("gamemode-id", "").trim().toLowerCase();
        String oldName = serverName;
        serverName = resolveServerName(c.getString("server-name", "").trim());
        if (oldName != null && !oldName.equals(serverName)) {
            // Alten Eintrag in der Lobby sofort entfernen, damit nicht doppelt gezählt wird
            for (String url : lobbyUrls) post(url, "/remove", baseJson(oldName));
        }
        getLogger().info("Melde als Server '" + serverName + "' (Gamemode '" + gamemodeId + "')");
        lobbyUrls = new ArrayList<>();
        for (String url : c.getStringList("lobby-urls")) {
            String u = url.trim();
            while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
            if (u.isEmpty()) continue;
            // Port-Bereich, z.B. http://192.168.1.10:8765-8769 -> 5 Adressen
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(https?://[^/]+):(\\d+)-(\\d+)$").matcher(u);
            if (m.matches()) {
                int from = Integer.parseInt(m.group(2));
                int to = Math.min(Integer.parseInt(m.group(3)), from + 49);
                for (int port = from; port <= to; port++) lobbyUrls.add(m.group(1) + ":" + port);
            } else {
                lobbyUrls.add(u);
            }
        }
        token = c.getString("token", "");
        ignoreSpectators = c.getBoolean("ignore-spectators", false);
        logErrors = c.getBoolean("log-errors", true);
        long interval = Math.max(1, c.getLong("interval-seconds", 5)) * 20L;

        if (gamemodeId.isEmpty()) getLogger().warning("gamemode-id ist leer - es wird nichts gesendet!");
        if (token.isBlank() || token.equals("BITTE-AENDERN")) getLogger().warning("token ist nicht gesetzt!");

        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(this, this::push, 20L, interval);
    }

    // ------------------------------------------------------------------ Events

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        schedulePush();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        schedulePush();
    }

    /** Sendet im nächsten Tick (mehrere Aufrufe im selben Tick werden zusammengefasst). */
    void schedulePush() {
        if (pushScheduled || !isEnabled()) return;
        pushScheduled = true;
        Bukkit.getScheduler().runTask(this, () -> {
            pushScheduled = false;
            push();
        });
    }

    // ------------------------------------------------------------------ Senden

    private void push() {
        if (gamemodeId.isEmpty() || lobbyUrls.isEmpty()) return;
        int players = countPlayers();
        int max = maxPlayers();
        lastPlayers = players;
        JsonObject json = baseJson();
        json.addProperty("players", players);
        json.addProperty("max", max);
        List<String> urls = List.copyOf(lobbyUrls);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (String url : urls) futures.add(post(url, "/update", json));

        // Es reicht, wenn EINE der Adressen erreichbar ist (Lobby kann auf verschiedenen Hosts laufen)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            List<String> reachable = new ArrayList<>();
            for (int i = 0; i < urls.size(); i++) {
                if (futures.get(i).join()) reachable.add(urls.get(i));
            }
            String state = reachable.isEmpty() ? "" : String.join(", ", reachable);
            String previous = overallState;
            overallState = state;
            if (state.equals(previous)) return;
            if (reachable.isEmpty()) {
                if (!logErrors) return;
                StringBuilder sb = new StringBuilder("Keine Lobby erreichbar! Versucht:");
                for (String url : urls) sb.append("\n  - ").append(url).append(" -> ").append(lastResult.getOrDefault(url, "?"));
                getLogger().warning(sb.toString());
            } else {
                getLogger().info("Lobby erreichbar unter: " + state);
            }
        });
    }

    private int countPlayers() {
        IntSupplier custom = LobbyBridgeAPI.playerCountProvider();
        if (custom != null) {
            try {
                return custom.getAsInt();
            } catch (Exception e) {
                getLogger().warning("Fehler im PlayerCountProvider: " + e.getMessage());
            }
        }
        if (!ignoreSpectators) return Bukkit.getOnlinePlayers().size();
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getGameMode() != GameMode.SPECTATOR) n++;
        return n;
    }

    private int maxPlayers() {
        IntSupplier custom = LobbyBridgeAPI.maxPlayersProvider();
        if (custom != null) {
            try {
                return custom.getAsInt();
            } catch (Exception e) {
                getLogger().warning("Fehler im MaxPlayersProvider: " + e.getMessage());
            }
        }
        return Bukkit.getMaxPlayers();
    }

    private JsonObject baseJson() {
        return baseJson(serverName);
    }

    private JsonObject baseJson(String name) {
        JsonObject json = new JsonObject();
        json.addProperty("gamemode", gamemodeId);
        json.addProperty("server", name);
        return json;
    }

    /** Pro Serverstart eindeutige Zufalls-ID (bleibt bei /lb reload gleich). */
    private final String randomId = java.util.UUID.randomUUID().toString().substring(0, 8);

    /**
     * Ersetzt Platzhalter im Servernamen. Leer = automatisch "%gamemode%-%hostname%-%port%".
     * So bekommt auch ein Server aus einem Template einen eindeutigen Namen.
     */
    private String resolveServerName(String pattern) {
        if (pattern.isEmpty()) pattern = "%gamemode%-%hostname%-%port%";
        String result = pattern
                .replace("%gamemode%", gamemodeId)
                .replace("%port%", String.valueOf(Bukkit.getPort()))
                .replace("%random%", randomId)
                .replace("%hostname%", hostname())
                .replace("%ip%", localIp());
        // %env:NAME%  -> Umgebungsvariable (z.B. von Pterodactyl/CloudNet/Docker gesetzt)
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("%env:([A-Za-z0-9_]+)%").matcher(result);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = System.getenv(m.group(1));
            if (value == null || value.isBlank()) {
                getLogger().warning("Umgebungsvariable " + m.group(1) + " ist nicht gesetzt - benutze Zufalls-ID.");
                value = randomId;
            }
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String hostname() {
        String env = System.getenv("HOSTNAME");
        if (env == null || env.isBlank()) env = System.getenv("COMPUTERNAME");
        if (env != null && !env.isBlank()) return env;
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String localIp() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** Sendet an eine Adresse. Ergebnis: true = Lobby hat angenommen. Status steht danach in lastResult. */
    private CompletableFuture<Boolean> post(String url, String path, JsonObject json) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url + path))
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();
        } catch (IllegalArgumentException e) {
            lastResult.put(url, "ungültige URL");
            return CompletableFuture.completedFuture(false);
        }
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    if (res.statusCode() == 200) {
                        lastResult.put(url, "ok");
                        return true;
                    }
                    if (res.statusCode() == 401) {
                        // Falscher Token ist immer ein Konfigurationsfehler -> sofort melden
                        String previous = lastResult.put(url, "falscher token");
                        if (!"falscher token".equals(previous)) {
                            getLogger().warning(url + " lehnt ab: token stimmt nicht mit der Lobby-Config überein!");
                        }
                    } else {
                        lastResult.put(url, "HTTP " + res.statusCode());
                    }
                    return false;
                })
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    lastResult.put(url, "nicht erreichbar (" + cause.getClass().getSimpleName() + ")");
                    return false;
                });
    }

    // ------------------------------------------------------------------ Befehl

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        MiniMessage mm = MiniMessage.miniMessage();
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            lastResult.clear();
            overallState = null;
            sender.sendMessage(mm.deserialize("<green>LobbyBridge neu geladen."));
            return true;
        }
        sender.sendMessage(mm.deserialize("<gold>LobbyBridge <gray>- gamemode-id: <white>" + gamemodeId
                + " <gray>server: <white>" + serverName + " <gray>zuletzt gemeldet: <white>" + lastPlayers));
        for (String url : lobbyUrls) {
            String state = lastResult.getOrDefault(url, "noch nichts gesendet");
            sender.sendMessage(mm.deserialize("<dark_gray> - <white>" + url + " <gray>» "
                    + ("ok".equals(state) ? "<green>verbunden" : "<red>" + mm.escapeTags(state))));
        }
        return true;
    }
}
