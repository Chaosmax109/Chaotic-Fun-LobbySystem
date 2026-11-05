package de.lobbysystem.lobby.gamemode;

import de.lobbysystem.lobby.LobbyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Set;

/**
 * Importiert Welten aus plugins/LobbySystem/lobbies/.
 * Für jede neue Welt wird automatisch eine Gamemode-Datei (gamemodes/&lt;id&gt;.yml) erstellt
 * und die Welt ins Server-Verzeichnis kopiert, damit sie geladen werden kann.
 */
public final class LobbyImporter {

    private static final Set<String> SKIP_FILES = Set.of("uid.dat", "session.lock");

    private final LobbyPlugin plugin;
    private final File lobbiesFolder;

    public LobbyImporter(LobbyPlugin plugin) {
        this.plugin = plugin;
        this.lobbiesFolder = new File(plugin.getDataFolder(), "lobbies");
    }

    public void run(GamemodeManager manager) {
        if (!lobbiesFolder.exists()) {
            lobbiesFolder.mkdirs();
            writeReadme();
        }
        File[] dirs = lobbiesFolder.listFiles(File::isDirectory);
        if (dirs == null) return;

        int nextOrder = 100;
        for (File dir : dirs) {
            if (!new File(dir, "level.dat").exists()) {
                plugin.getLogger().warning("lobbies/" + dir.getName() + " enthält keine level.dat - übersprungen.");
                continue;
            }
            String id = sanitize(dir.getName());
            if (id.isEmpty()) continue;

            File gmFile = new File(manager.folder(), id + ".yml");
            if (!gmFile.exists()) {
                try {
                    createGamemodeFile(gmFile, id, dir.getName(), nextOrder++);
                    plugin.getLogger().info("Neue Lobby gefunden: " + dir.getName() + " -> gamemodes/" + id + ".yml erstellt.");
                } catch (IOException e) {
                    plugin.getLogger().severe("Konnte gamemodes/" + id + ".yml nicht erstellen: " + e.getMessage());
                    continue;
                }
            }

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(gmFile);
            String worldName = cfg.getString("target.world", id);
            if (worldName == null || worldName.isBlank()) worldName = id;
            boolean reimport = cfg.getBoolean("lobby-world.reimport-on-next-start", false);
            File target = new File(Bukkit.getWorldContainer(), worldName);

            if (Bukkit.getWorld(worldName) != null) {
                if (reimport) {
                    plugin.getLogger().warning("Welt '" + worldName + "' ist bereits geladen und kann nicht neu importiert werden.");
                }
                continue;
            }

            boolean exists = worldExistsOnDisk(worldName);
            if (exists && !reimport) continue;

            try {
                if (target.exists()) deleteRecursive(target.toPath());
                copyWorld(dir.toPath(), target.toPath());
                plugin.getLogger().info("Lobby-Welt '" + dir.getName() + "' wurde als '" + worldName + "' importiert.");
                if (reimport) {
                    cfg.set("lobby-world.reimport-on-next-start", false);
                    cfg.save(gmFile);
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Import von lobbies/" + dir.getName() + " fehlgeschlagen: " + e.getMessage());
            }
        }
    }

    /**
     * Prüft, ob die Welt im Server-Verzeichnis liegt - klassisch (./name) oder
     * im neuen 26.x-Layout (./&lt;hauptwelt&gt;/dimensions/minecraft/name).
     */
    public static boolean worldExistsOnDisk(String worldName) {
        File container = Bukkit.getWorldContainer();
        if (new File(container, worldName).isDirectory()) return true;
        String main = Bukkit.getWorlds().isEmpty() ? "world" : Bukkit.getWorlds().getFirst().getName();
        File dims = new File(new File(container, main), "dimensions");
        File[] namespaces = dims.listFiles(File::isDirectory);
        if (namespaces == null) return false;
        String lower = worldName.toLowerCase(Locale.ROOT);
        for (File ns : namespaces) {
            if (new File(ns, worldName).isDirectory() || new File(ns, lower).isDirectory()) return true;
        }
        return false;
    }

    private void createGamemodeFile(File gmFile, String id, String folderName, int order) throws IOException {
        String template;
        try (InputStream in = plugin.getResource("lobby-template.yml")) {
            if (in == null) throw new IOException("lobby-template.yml fehlt im Plugin");
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String name = folderName.replace('_', ' ').replace('-', ' ');
        template = template
                .replace("%NAME%", name)
                .replace("%FOLDER%", folderName)
                .replace("%WORLD%", id)
                .replace("%ID%", id)
                .replace("%ORDER%", String.valueOf(order));
        gmFile.getParentFile().mkdirs();
        Files.writeString(gmFile.toPath(), template, StandardCharsets.UTF_8);
    }

    private void writeReadme() {
        String text = """
                Eigene Gamemode-Lobbys
                ======================
                1. Lade den Welt-Ordner deiner Lobby (mit level.dat darin) hier hoch,
                   z.B.  plugins/LobbySystem/lobbies/BedWars/level.dat
                2. Starte den Server neu.
                3. Das Plugin kopiert die Welt ins Server-Verzeichnis, lädt sie und erstellt
                   automatisch  plugins/LobbySystem/gamemodes/bedwars.yml
                4. Passe dort Icon, Name, Slot usw. an und mache /ls reload.
                   Den Spawn in der Lobby setzt du im Spiel mit  /ls setspawn bedwars

                Welt aktualisieren: Neue Version hier hochladen und in der Gamemode-Datei
                lobby-world.reimport-on-next-start: true setzen, dann Neustart.
                """;
        try {
            Files.writeString(new File(lobbiesFolder, "LIES_MICH.txt").toPath(), text, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }

    private static void copyWorld(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!SKIP_FILES.contains(file.getFileName().toString())) {
                    Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursive(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
