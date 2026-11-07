package io.nightbeam.donutmail.gui;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.MailState;
import io.nightbeam.donutmail.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Staff view of one player's mail: active first, then recent history. Right-click to inspect, shift-right-click to remove. */
public final class AdminGui extends BaseGui {
    public record Data(List<MailRecord> active, List<MailRecord> history) {
    }

    private static final int PER_PAGE = 45;

    private final UUID target;
    private final String targetName;
    private final int page;
    private final List<MailRecord> entries = new ArrayList<>();
    private final Map<Integer, MailRecord> slots = new HashMap<>();

    public AdminGui(DonutMailPlugin plugin, Player staff, UUID target, String targetName, int page, Data data) {
        super(plugin, staff);
        this.target = target;
        this.targetName = targetName;
        entries.addAll(data.active());
        entries.addAll(data.history());
        int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.max(0, Math.min(page, pages - 1));
        this.inventory = Bukkit.createInventory(this, 54, plugin.gui().title("admin", "player", targetName));
        int from = this.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && from + i < entries.size(); i++) {
            MailRecord mail = entries.get(from + i);
            inventory.setItem(i, MailIcons.mail(plugin, mail, MailIcons.View.ADMIN));
            slots.put(i, mail);
        }
        String pageText = String.valueOf(this.page + 1);
        String pagesText = String.valueOf(pages);
        if (this.page > 0) {
            inventory.setItem(plugin.gui().slot("mailbox.buttons.previous", 45),
                    plugin.gui().button("mailbox.buttons.previous", "page", pageText, "pages", pagesText).build());
        }
        if (this.page < pages - 1) {
            inventory.setItem(plugin.gui().slot("mailbox.buttons.next", 53),
                    plugin.gui().button("mailbox.buttons.next", "page", pageText, "pages", pagesText).build());
        }
        fill();
    }

    @Override
    public void click(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == plugin.gui().slot("mailbox.buttons.previous", 45) && page > 0) {
            plugin.guiManager().openAdmin(player, target, targetName, page - 1);
            return;
        }
        if (slot == plugin.gui().slot("mailbox.buttons.next", 53)) {
            plugin.guiManager().openAdmin(player, target, targetName, page + 1);
            return;
        }
        MailRecord mail = slots.get(slot);
        if (mail == null) return;
        if (event.getClick().isShiftClick() && event.getClick().isRightClick()
                && (mail.state() == MailState.PENDING || mail.state() == MailState.COD)) {
            plugin.mail().adminRemove(player, mail.id()).thenAccept(result -> plugin.scheduler().atPlayer(player, () -> {
                if (result == null) {
                    Feedback.error(plugin, player, "admin.remove-failed");
                } else {
                    plugin.messages().send(player, "admin.removed", "what", Text.trusted(result.isEmpty() ? "" : plugin.messages().raw(result)));
                }
                plugin.guiManager().openAdmin(player, target, targetName, page);
            }));
            return;
        }
        plugin.guiManager().openPreview(player, mail, PreviewGui.Mode.ADMIN,
                () -> plugin.guiManager().openAdmin(player, target, targetName, page));
    }
}
