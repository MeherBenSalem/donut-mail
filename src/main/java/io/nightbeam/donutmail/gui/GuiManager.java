package io.nightbeam.donutmail.gui;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.model.Campaign;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.service.ParcelService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Opens menus and routes inventory events to them. */
public final class GuiManager implements Listener {
    private final DonutMailPlugin plugin;
    private final ChatPrompts prompts;

    public GuiManager(DonutMailPlugin plugin) {
        this.plugin = plugin;
        this.prompts = new ChatPrompts(plugin);
    }

    public ChatPrompts prompts() {
        return prompts;
    }

    public void openMailbox(Player player, MailKind.Tab tab) {
        openMailbox(player, tab, 0);
    }

    public void openMailbox(Player player, MailKind.Tab tab, int page) {
        MailboxData.load(plugin, player, tab).thenAccept(data -> plugin.scheduler().atPlayer(player, () -> {
            MailboxGui gui = new MailboxGui(plugin, player, tab, page, data);
            player.openInventory(gui.getInventory());
        })).exceptionally(failure -> {
            plugin.getLogger().warning("Could not open the mailbox of " + player.getName() + ": " + failure.getMessage());
            plugin.scheduler().atPlayer(player, () -> plugin.messages().send(player, "general.storage-error"));
            return null;
        });
    }

    public void openPreview(Player player, MailRecord mail, PreviewGui.Mode mode, Runnable back) {
        plugin.scheduler().atPlayer(player, () -> {
            PreviewGui gui = new PreviewGui(plugin, player, mail, null, mode, back);
            player.openInventory(gui.getInventory());
            if (mode == PreviewGui.Mode.RECEIVE || mode == PreviewGui.Mode.COD) plugin.mail().markRead(mail);
        });
    }

    public void openCampaignPreview(Player player, Campaign campaign, Runnable back) {
        plugin.scheduler().atPlayer(player, () -> {
            PreviewGui gui = new PreviewGui(plugin, player, null, campaign, PreviewGui.Mode.CAMPAIGN, back);
            player.openInventory(gui.getInventory());
        });
    }

    /** The compose menu, optionally for a known recipient and with a cash-on-delivery price already set. */
    public void openCompose(Player player, ParcelService.Recipient recipient, double codPrice) {
        plugin.scheduler().atPlayer(player, () -> {
            ComposeGui gui = new ComposeGui(plugin, player, recipient, codPrice);
            player.openInventory(gui.getInventory());
        });
    }

    public void openAdmin(Player staff, UUID target, String targetName, int page) {
        plugin.scheduler().supply(() -> new AdminGui.Data(plugin.storage().active(target), plugin.storage().history(target, 90)))
                .thenAccept(data -> plugin.scheduler().atPlayer(staff, () -> {
                    AdminGui gui = new AdminGui(plugin, staff, target, targetName, page, data);
                    staff.openInventory(gui.getInventory());
                }));
    }

    private static BaseGui gui(InventoryHolder holder) {
        return holder instanceof BaseGui gui ? gui : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        BaseGui gui = gui(event.getInventory().getHolder(false));
        if (gui == null) return;
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(gui.player().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        try {
            gui.click(event);
        } catch (RuntimeException failure) {
            event.setCancelled(true);
            plugin.getLogger().warning("Menu click failed: " + failure);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        BaseGui gui = gui(event.getInventory().getHolder(false));
        if (gui != null) gui.drag(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        BaseGui gui = gui(event.getInventory().getHolder(false));
        if (gui != null) gui.close(event);
    }

    /** Plugin shutdown: close menus so compose items go back to their owners. */
    public void closeAll() {
        prompts.cancelAll();
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder;
            try {
                holder = player.getOpenInventory().getTopInventory().getHolder(false);
            } catch (RuntimeException unavailable) {
                continue;
            }
            if (!(holder instanceof BaseGui gui)) continue;
            if (!plugin.scheduler().isFolia()) {
                try {
                    player.closeInventory();
                } catch (RuntimeException ignored) {
                    // The rescue below still keeps the items.
                }
            }
            if (gui instanceof ComposeGui compose) compose.rescue();
        }
    }
}
