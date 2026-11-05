package de.lobbysystem.lobby.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/** Holder des Gamemode-Menüs: merkt sich, welcher Gamemode in welchem Slot liegt. */
public final class GamemodeMenu implements InventoryHolder {

    private final Map<Integer, String> slots;
    private Inventory inventory;

    GamemodeMenu(Map<Integer, String> slots) {
        this.slots = slots;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Map<Integer, String> slots() {
        return slots;
    }

    public String gamemodeAt(int slot) {
        return slots.get(slot);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
