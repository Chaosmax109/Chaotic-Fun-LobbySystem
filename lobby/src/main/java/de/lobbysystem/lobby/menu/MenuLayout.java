package de.lobbysystem.lobby.menu;

import de.lobbysystem.lobby.config.MainConfig;
import de.lobbysystem.lobby.gamemode.Gamemode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Berechnet Größe und Anordnung des Menüs (dynamisch oder fest). */
final class MenuLayout {

    record Result(int rows, Map<Integer, Gamemode> slots) {
    }

    private static final int MAX_ROWS = 6;

    private MenuLayout() {
    }

    static Result compute(List<Gamemode> visible, MainConfig cfg, Consumer<String> warn) {
        boolean fixed = cfg.menuRows > 0;
        int limitRows = fixed ? cfg.menuRows : MAX_ROWS;
        int limit = limitRows * 9;

        Gamemode[] grid = new Gamemode[MAX_ROWS * 9];
        boolean[] rowHasExplicit = new boolean[MAX_ROWS];
        List<Gamemode> autos = new ArrayList<>();

        // 1) Gamemodes mit festem Slot
        for (Gamemode g : visible) {
            if (g.slot < 0) {
                autos.add(g);
                continue;
            }
            if (g.slot >= limit) {
                warn.accept("[" + g.id + "] slot " + g.slot + " liegt außerhalb des Menüs (" + limit + " Slots) - wird automatisch platziert.");
                autos.add(g);
                continue;
            }
            if (grid[g.slot] != null) {
                warn.accept("[" + g.id + "] slot " + g.slot + " ist schon von '" + grid[g.slot].id + "' belegt - wird automatisch platziert.");
                autos.add(g);
                continue;
            }
            grid[g.slot] = g;
            rowHasExplicit[g.slot / 9] = true;
        }

        boolean anyExplicit = autos.size() != visible.size();

        // 2) Automatisch platzierte Gamemodes
        if (!autos.isEmpty()) {
            if (cfg.menuLayout == MainConfig.Layout.CENTERED) {
                placeCentered(autos, grid, rowHasExplicit, limitRows, fixed, anyExplicit, cfg, warn);
            } else {
                placeFill(autos, grid, limit, fixed, cfg, warn);
            }
        }

        // 3) Größe bestimmen
        int lastRow = -1;
        for (int i = 0; i < grid.length; i++) {
            if (grid[i] != null) lastRow = i / 9;
        }
        int rows;
        if (fixed) {
            rows = cfg.menuRows;
        } else {
            rows = lastRow + 1;
            if (cfg.padding && rows > 0 && rows < MAX_ROWS) rows++; // leere Reihe unten
            rows = Math.max(1, Math.min(MAX_ROWS, rows));
        }

        Map<Integer, Gamemode> result = new LinkedHashMap<>();
        for (int i = 0; i < rows * 9; i++) {
            if (grid[i] != null) result.put(i, grid[i]);
        }
        return new Result(rows, result);
    }

    private static void placeCentered(List<Gamemode> autos, Gamemode[] grid, boolean[] rowHasExplicit, int limitRows,
                                      boolean fixed, boolean anyExplicit, MainConfig cfg, Consumer<String> warn) {
        // In Zeilen aufteilen
        List<List<Gamemode>> lines = new ArrayList<>();
        for (int i = 0; i < autos.size(); i += cfg.maxPerRow) {
            lines.add(autos.subList(i, Math.min(autos.size(), i + cfg.maxPerRow)));
        }

        int freeRows = 0;
        for (int r = 0; r < limitRows; r++) if (!rowHasExplicit[r]) freeRows++;

        int startRow;
        if (fixed && !anyExplicit) {
            startRow = Math.max(0, (limitRows - lines.size()) / 2); // vertikal mittig
        } else if (!fixed && cfg.padding && lines.size() + 2 <= freeRows) {
            startRow = 1; // leere Reihe oben
        } else {
            startRow = 0;
        }

        int row = startRow;
        for (List<Gamemode> line : lines) {
            while (row < limitRows && rowHasExplicit[row]) row++;
            if (row >= limitRows) {
                overflow(line, warn);
                continue;
            }
            int k = line.size();
            int start, step;
            if (cfg.spacing && k <= 4) {
                step = 2;
                start = (9 - (2 * k - 1)) / 2;
            } else {
                step = 1;
                start = (9 - k) / 2;
            }
            for (int i = 0; i < k; i++) {
                grid[row * 9 + start + i * step] = line.get(i);
            }
            row++;
        }
    }

    private static void placeFill(List<Gamemode> autos, Gamemode[] grid, int limit, boolean fixed, MainConfig cfg, Consumer<String> warn) {
        int slot = (!fixed && cfg.padding) ? 9 : 0;
        // Ohne Platz für Padding wieder bei 0 anfangen
        int free = 0;
        for (int i = slot; i < limit; i++) if (grid[i] == null) free++;
        if (free < autos.size()) slot = 0;

        for (Gamemode g : autos) {
            while (slot < limit && grid[slot] != null) slot++;
            if (slot >= limit) {
                overflow(List.of(g), warn);
                continue;
            }
            grid[slot++] = g;
        }
    }

    private static void overflow(List<Gamemode> gamemodes, Consumer<String> warn) {
        for (Gamemode g : gamemodes) {
            warn.accept("[" + g.id + "] Kein Platz mehr im Menü - erhöhe menu.rows oder max-per-row.");
        }
    }
}
