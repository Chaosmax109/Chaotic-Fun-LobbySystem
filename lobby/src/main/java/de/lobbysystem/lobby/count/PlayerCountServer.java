package de.lobbysystem.lobby.count;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Kleiner HTTP-Server, an den die LobbyBridge-Plugins ihre Spielerzahlen schicken.
 *
 * POST /update  {"gamemode":"bedwars","server":"bw-1","players":5,"max":16}
 * POST /remove  {"gamemode":"bedwars","server":"bw-1"}
 * Header: Authorization: Bearer &lt;token&gt;
 */
public final class PlayerCountServer {

    private static final int MAX_BODY = 4096;

    private final PlayerCountRegistry registry;
    private final Logger log;
    private final byte[] token;
    private HttpServer server;
    private ExecutorService executor;

    public PlayerCountServer(PlayerCountRegistry registry, Logger log, String token) {
        this.registry = registry;
        this.log = log;
        this.token = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
    }

    public void start(String bind, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "LobbySystem-PlayerCounts");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.createContext("/update", ex -> handle(ex, false));
        server.createContext("/remove", ex -> handle(ex, true));
        server.start();
        log.info("Spielerzahl-Empfang läuft auf " + bind + ":" + port);
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
        server = null;
        executor = null;
    }

    private void handle(HttpExchange ex, boolean remove) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                respond(ex, 405, "method not allowed");
                return;
            }
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            if (auth == null || !MessageDigest.isEqual(token, auth.getBytes(StandardCharsets.UTF_8))) {
                respond(ex, 401, "unauthorized");
                return;
            }
            byte[] body = ex.getRequestBody().readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) {
                respond(ex, 413, "too large");
                return;
            }
            JsonObject json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            String gamemode = json.get("gamemode").getAsString().trim();
            String serverName = json.get("server").getAsString().trim();
            if (gamemode.isEmpty() || serverName.isEmpty()) {
                respond(ex, 400, "missing gamemode/server");
                return;
            }
            if (remove) {
                registry.remove(gamemode, serverName);
            } else {
                int players = json.has("players") ? json.get("players").getAsInt() : 0;
                int max = json.has("max") ? json.get("max").getAsInt() : 0;
                registry.update(gamemode, serverName, players, max);
            }
            respond(ex, 200, "ok");
        } catch (RuntimeException e) {
            try {
                respond(ex, 400, "bad request");
            } catch (IOException ignored) {
            }
        } finally {
            ex.close();
        }
    }

    private static void respond(HttpExchange ex, int code, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
