# LobbySystem (Paper 26.2+)

Zwei Plugins:

| Plugin | Wohin? | Aufgabe |
|---|---|---|
| `LobbySystem.jar` | Lobby-Server | Kompass, Gamemode-Menü, Lobby-Welten, Schutz, Empfang der Spielerzahlen |
| `LobbyBridge.jar` | jeder Gamemode-Server | meldet die Spielerzahl an die Lobby (HTTP) |

## Bauen

Voraussetzung: JDK 25 und Gradle 9.

```
gradle build
```

Ergebnis: `lobby/build/libs/LobbySystem-1.0.0.jar` und `bridge/build/libs/LobbyBridge-1.0.0.jar`

## Einrichtung

1. `LobbySystem.jar` auf den Lobby-Server und einmal starten.
2. In `plugins/LobbySystem/config.yml` bei `player-counts.token` ein eigenes Passwort eintragen.
3. Den Port aus `player-counts.port` (Standard: 8765) für die Gamemode-Server erreichbar machen.
4. `LobbyBridge.jar` auf jeden Gamemode-Server. In dessen `config.yml` folgendes eintragen:
   `gamemode-id` (gleich der `api-id` in der Lobby), `server-name`, `lobby-urls` und denselben `token`.

## Ordner auf dem Lobby-Server

```
plugins/LobbySystem/
├── config.yml          Main-Lobby, Kompass, Menü (Größe/Anordnung), Schutz, Nachrichten
├── gamemodes/          eine .yml pro Gamemode (Icon, Name, Slot, Ziel, api-id)
│   ├── bedwars.yml
│   └── skywars.yml
└── lobbies/            hier eigene Lobby-Welten hochladen
    └── BedWars/level.dat ...
```

### Eigene Gamemode-Lobby

1. Den Welt-Ordner nach `plugins/LobbySystem/lobbies/<Name>/` hochladen.
2. Den Server neu starten. Das Plugin kopiert die Welt ins Server-Verzeichnis und lädt sie.
   Es legt außerdem `gamemodes/<name>.yml` an (`type: LOBBY_WORLD`).
3. Die Datei anpassen und `/ls reload` ausführen. Den Spawn in der Lobby setzt du mit `/ls setspawn <name>`.

### Zieltypen (`target.type`)

* `LOBBY_WORLD`: eigene Lobby-Welt. Das Menü zeigt `%lobby_players%`.
* `LOCATION`: eine feste Position (Welt + x/y/z/yaw/pitch).
* `SERVER`: Verbindung über BungeeCord/Velocity (`target.server`).
* `MAIN_LOBBY`: zurück in die Main-Lobby.

### Menü

* `menu.rows: auto` passt die Größe dynamisch an. `1`–`6` gibt eine feste Größe vor.
* `slot: -1` platziert ein Icon automatisch: mittig mit `layout: centered` oder von links mit `layout: fill`.
  `slot: 0-53` legt einen festen Slot fest.
* Reihenfolge über `order`. Es gibt keine Füll-Items.
* Platzhalter in der Lore: `%lobby_players%`, `%playing%`, `%servers%`, `%total%`, `%status%`, `%name%`.
  Zeilen, die für einen Gamemode nicht passen, werden automatisch ausgeblendet.

## Befehle

| Befehl | Permission |
|---|---|
| `/lobby` (`/hub`, `/l`, `/spawn`) | `lobbysystem.lobby` (Standard: alle) |
| `/ls reload`, `/ls setspawn <id>`, `/ls setmainspawn`, `/ls list`, `/ls menu` | `lobbysystem.admin` |
| Lobby-Schutz umgehen (nicht den Kompass) | `lobbysystem.bypass` |
| `/lobbybridge [status\|reload]` (Gamemode-Server) | `lobbybridge.admin` |

## API für Gamemode-Plugins (LobbyBridge)

```java
// plugin.yml: depend: [LobbyBridge]
LobbyBridgeAPI.setPlayerCountProvider(() -> game.getPlayingCount()); // statt Online-Spielern
LobbyBridgeAPI.setMaxPlayersProvider(() -> game.getMaxPlayers());
LobbyBridgeAPI.pushNow();
```
