package io.nightbeam.donutmail.listener;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.model.MailKind;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Right-clicking a configured block (for example a lectern at spawn) opens the mailbox. */
public final class MailboxBlockListener implements Listener {
    private final DonutMailPlugin plugin;

    public MailboxBlockListener(DonutMailPlugin plugin) {
        this.plugin = plugin;
    }

    public static String key(Location location) {
        return location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.settings().mailboxBlocks || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || plugin.settings().mailboxLocations.isEmpty()) return;
        if (!plugin.settings().mailboxLocations.contains(key(block.getLocation()))) return;
        event.setCancelled(true);
        if (!event.getPlayer().hasPermission("donutmail.use")) {
            plugin.messages().send(event.getPlayer(), "general.no-permission");
            return;
        }
        plugin.guiManager().openMailbox(event.getPlayer(), MailKind.Tab.INBOX);
    }
}
