package io.nightbeam.donutmail.gui;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.util.Sounds;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** A DonutMail menu. Clicks are cancelled unless a menu allows them (only the compose menu does). */
public abstract class BaseGui implements InventoryHolder {
    protected final DonutMailPlugin plugin;
    protected final Player player;
    protected Inventory inventory;

    protected BaseGui(DonutMailPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player player() {
        return player;
    }

    public abstract void click(InventoryClickEvent event);

    public void drag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    public void close(InventoryCloseEvent event) {
    }

    /** Puts the filler pane in every empty slot. */
    protected void fill() {
        ItemStack pane = plugin.gui().filler();
        if (pane == null) return;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) inventory.setItem(slot, pane);
        }
    }

    protected void sound(String key) {
        Sounds.play(player, plugin.settings().raw, key);
    }

    /** True while this menu is the one the player is looking at. */
    protected boolean isOpen() {
        return player.isOnline() && player.getOpenInventory().getTopInventory().getHolder(false) == this;
    }
}
