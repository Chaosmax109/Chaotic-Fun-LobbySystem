package de.lobbysystem.bridge;

import java.util.function.IntSupplier;

/**
 * Öffentliche API für Gamemode-Plugins.
 *
 * <p>Standardmäßig wird die Anzahl der Online-Spieler gemeldet. Ein Gamemode-Plugin kann
 * stattdessen eine eigene Zählung liefern, z.B. nur Spieler, die gerade in einer Runde sind:</p>
 *
 * <pre>{@code
 * LobbyBridgeAPI.setPlayerCountProvider(() -> arenaManager.getPlayingCount());
 * LobbyBridgeAPI.setMaxPlayersProvider(() -> arenaManager.getMaxPlayers());
 * LobbyBridgeAPI.pushNow(); // sofort senden, z.B. bei Rundenstart
 * }</pre>
 *
 * <p>In der plugin.yml des eigenen Plugins: {@code depend: [LobbyBridge]} (oder softdepend).
 * Die Provider werden auf dem Main-Thread aufgerufen.</p>
 */
public final class LobbyBridgeAPI {

    private static volatile IntSupplier playerCountProvider;
    private static volatile IntSupplier maxPlayersProvider;

    private LobbyBridgeAPI() {
    }

    /** Eigene Spielerzahl liefern. {@code null} = Standard (Online-Spieler). */
    public static void setPlayerCountProvider(IntSupplier provider) {
        playerCountProvider = provider;
    }

    /** Eigene Max-Spieler liefern. {@code null} = Standard (max-players des Servers). */
    public static void setMaxPlayersProvider(IntSupplier provider) {
        maxPlayersProvider = provider;
    }

    /** Spielerzahl sofort (im nächsten Tick) an die Lobby senden. */
    public static void pushNow() {
        LobbyBridge bridge = LobbyBridge.instance();
        if (bridge != null) bridge.schedulePush();
    }

    static IntSupplier playerCountProvider() {
        return playerCountProvider;
    }

    static IntSupplier maxPlayersProvider() {
        return maxPlayersProvider;
    }
}
