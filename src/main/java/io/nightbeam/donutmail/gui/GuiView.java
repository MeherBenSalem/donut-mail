package io.nightbeam.donutmail.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public interface GuiView extends InventoryHolder {
    Inventory inventory();

    void onClick(Player player, InventoryClickEvent event);

    @Override
    default Inventory getInventory() {
        return inventory();
    }
}
