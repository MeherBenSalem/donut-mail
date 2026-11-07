package io.nightbeam.donutmail.gui;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.config.DailyConfig;
import io.nightbeam.donutmail.config.GuiConfig;
import io.nightbeam.donutmail.model.Campaign;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.MailState;
import io.nightbeam.donutmail.model.RedeemOutcome;
import io.nightbeam.donutmail.service.ClaimResult;
import io.nightbeam.donutmail.service.DailyService;
import io.nightbeam.donutmail.util.ItemBuilder;
import io.nightbeam.donutmail.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** The mailbox: Inbox, Parcels, Sent and Rewards tabs, with Claim All, paging and the daily calendar. */
public final class MailboxGui extends BaseGui {
    private static final MailKind.Tab[] TABS = {MailKind.Tab.INBOX, MailKind.Tab.PARCELS, MailKind.Tab.SENT, MailKind.Tab.REWARDS};

    private final MailKind.Tab tab;
    private int page;
    private MailboxData data;
    private final Map<Integer, Object> slotEntries = new HashMap<>();
    private final Map<Integer, String> buttons = new HashMap<>();

    public MailboxGui(DonutMailPlugin plugin, Player player, MailKind.Tab tab, int page, MailboxData data) {
        super(plugin, player);
        this.tab = tab;
        this.page = page;
        this.data = data;
        this.inventory = Bukkit.createInventory(this, 54, plugin.gui().title(tab.name().toLowerCase(Locale.ROOT)));
        render();
    }

    private List<Integer> contentSlots() {
        GuiConfig gui = plugin.gui();
        return tab == MailKind.Tab.REWARDS ? gui.slots("mailbox.rewards-content-slots", 54) : gui.slots("mailbox.content-slots", 54);
    }

    private void render() {
        GuiConfig gui = plugin.gui();
        inventory.clear();
        slotEntries.clear();
        buttons.clear();
        List<Object> entries = data.entries(tab);
        List<Integer> slots = contentSlots();
        int perPage = Math.max(1, slots.size());
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));

        renderTabs(gui);

        int from = page * perPage;
        for (int i = 0; i < perPage && from + i < entries.size(); i++) {
            Object entry = entries.get(from + i);
            int slot = slots.get(i);
            if (entry instanceof MailRecord mail) {
                inventory.setItem(slot, MailIcons.mail(plugin, mail, tab == MailKind.Tab.SENT ? MailIcons.View.SENT : MailIcons.View.RECEIVER));
            } else if (entry instanceof Campaign campaign) {
                inventory.setItem(slot, MailIcons.campaign(plugin, campaign));
            }
            slotEntries.put(slot, entry);
        }
        if (entries.isEmpty()) {
            String key = tab == MailKind.Tab.SENT ? "mailbox.empty-sent" : "mailbox.empty";
            int slot = gui.number("mailbox.empty-slot", 22);
            if (tab == MailKind.Tab.REWARDS && slot < 18) slot = 31;
            inventory.setItem(slot, gui.button(key).build());
        }
        if (tab == MailKind.Tab.REWARDS) renderCalendar(gui);

        String pageText = String.valueOf(page + 1);
        String pagesText = String.valueOf(pages);
        if (page > 0) button("previous", "page", pageText, "pages", pagesText);
        if (page < pages - 1) button("next", "page", pageText, "pages", pagesText);
        if (tab != MailKind.Tab.SENT) button("claim-all");
        if (plugin.settings().parcelsEnabled && player.hasPermission("donutmail.senditem")) button("compose");
        if (tab == MailKind.Tab.REWARDS) {
            if (plugin.settings().codesEnabled && player.hasPermission("donutmail.redeem")) button("redeem");
        } else {
            button("settings", "state", Text.trusted(gui.text(data.acceptsParcels() ? "mailbox.state-on" : "mailbox.state-off")),
                    "blocked", String.valueOf(data.blocked()));
        }
        fill();
    }

    private void button(String name, String... keyValues) {
        String path = "mailbox.buttons." + name;
        int slot = plugin.gui().slot(path, -1);
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, plugin.gui().button(path, keyValues).build());
        buttons.put(slot, name);
    }

    private void renderTabs(GuiConfig gui) {
        for (MailKind.Tab each : TABS) {
            String path = "mailbox.tabs." + each.name().toLowerCase(Locale.ROOT);
            int slot = gui.slot(path, -1);
            if (slot < 0 || slot >= 54) continue;
            ItemBuilder builder = gui.button(path, "count", String.valueOf(data.count(each)));
            List<Component> lore = new ArrayList<>(loreOf(builder));
            lore.add(Component.empty());
            lore.add(gui.line(each == tab ? "mailbox.selected-line" : "mailbox.click-line"));
            builder.lore(lore).glow(each == tab);
            inventory.setItem(slot, builder.build());
            buttons.put(slot, "tab:" + each.name());
        }
        int profileSlot = gui.slot("mailbox.tabs.profile", -1);
        if (profileSlot >= 0 && profileSlot < 54) {
            inventory.setItem(profileSlot, gui.button("mailbox.tabs.profile",
                    "player", player.getName(),
                    "unread", String.valueOf(data.summary().unread()),
                    "parcels", String.valueOf(data.summary().parcels()),
                    "cod", String.valueOf(data.summary().cod()),
                    "rewards", String.valueOf(data.summary().rewards()),
                    "streak", String.valueOf(data.record() == null ? 0 : data.record().dailyStreak()))
                    .owner(player).build());
        }
    }

    private static List<Component> loreOf(ItemBuilder builder) {
        List<Component> lore = builder.build().lore();
        return lore == null ? List.of() : lore;
    }

    private void renderCalendar(GuiConfig gui) {
        DailyConfig daily = plugin.daily();
        List<Integer> slots = gui.slots("calendar.slots", 54);
        if (slots.isEmpty()) return;
        if (!daily.active()) {
            inventory.setItem(slots.get(slots.size() / 2), gui.button("calendar.disabled").build());
            return;
        }
        DailyService.Position position = plugin.dailyRewards().position(data.record());
        int count = daily.days.size();
        int window = Math.min(slots.size(), count);
        int start = count <= slots.size() ? 0 : Math.max(0, Math.min(position.index() - window / 2, count - window));
        for (int i = 0; i < window; i++) {
            int index = start + i;
            DailyConfig.Day day = daily.days.get(index);
            List<Component> lore = new ArrayList<>();
            for (String line : day.display()) lore.add(Text.parse(line));
            lore.add(Component.empty());
            boolean today = index == position.index();
            if (index < position.index()) {
                lore.add(gui.line("calendar.claimed"));
            } else if (today) {
                lore.add(gui.line(position.deliveredToday() ? "calendar.today" : "calendar.today-waiting"));
                lore.add(gui.line("calendar.streak-line", "streak", String.valueOf(position.streak())));
            } else {
                int days = index - position.index();
                lore.add(gui.line("calendar.upcoming", "days", String.valueOf(days)));
            }
            ItemBuilder builder = ItemBuilder.of(day.icon() == null ? Material.CHEST : day.icon())
                    .name(gui.line("calendar.name", "day", String.valueOf(day.number())))
                    .lore(lore).hideAll().glow(today);
            inventory.setItem(slots.get(i), builder.build());
        }
    }

    // ── Clicks ──────────────────────────────────────────────────────────────

    @Override
    public void click(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;
        String button = buttons.get(slot);
        if (button != null) {
            clickButton(button);
            return;
        }
        Object entry = slotEntries.get(slot);
        if (entry instanceof Campaign campaign) {
            clickCampaign(campaign, event.getClick());
        } else if (entry instanceof MailRecord mail) {
            clickMail(mail, event.getClick());
        }
    }

    private void clickButton(String button) {
        if (button.startsWith("tab:")) {
            MailKind.Tab target = MailKind.Tab.valueOf(button.substring(4));
            if (target != tab) {
                sound("page");
                plugin.guiManager().openMailbox(player, target);
            }
            return;
        }
        switch (button) {
            case "previous" -> {
                page--;
                sound("page");
                render();
            }
            case "next" -> {
                page++;
                sound("page");
                render();
            }
            case "claim-all" -> claimAll();
            case "compose" -> {
                sound("click");
                plugin.guiManager().openCompose(player, null, 0);
            }
            case "settings" -> toggleParcels();
            case "redeem" -> redeemPrompt();
            default -> {
            }
        }
    }

    private void clickMail(MailRecord mail, ClickType click) {
        if (tab == MailKind.Tab.SENT) {
            sound("click");
            plugin.guiManager().openPreview(player, mail, PreviewGui.Mode.SENT, this::reopen);
            return;
        }
        if (mail.state() == MailState.CLAIMING) return;
        if (mail.state() == MailState.COD) {
            sound("click");
            plugin.guiManager().openPreview(player, mail, PreviewGui.Mode.COD, this::reopen);
            return;
        }
        if (click.isRightClick() && mail.hasRewards()) {
            sound("click");
            plugin.guiManager().openPreview(player, mail, PreviewGui.Mode.RECEIVE, this::reopen);
            return;
        }
        plugin.mail().guarded(player.getUniqueId(), () -> plugin.mail().claim(player, mail.id()), result ->
                plugin.scheduler().atPlayer(player, () -> {
                    if (result != null) Feedback.claim(plugin, player, result);
                    refresh();
                }));
    }

    private void clickCampaign(Campaign campaign, ClickType click) {
        if (click.isRightClick() && campaign.hasRewards()) {
            sound("click");
            plugin.guiManager().openCampaignPreview(player, campaign, this::reopen);
            return;
        }
        plugin.mail().guarded(player.getUniqueId(), () -> plugin.campaigns().claim(player, campaign), result ->
                plugin.scheduler().atPlayer(player, () -> {
                    if (result != null) Feedback.claim(plugin, player, result);
                    refresh();
                }));
    }

    private void claimAll() {
        List<Object> entries = data.entries(tab);
        boolean started = plugin.mail().guarded(player.getUniqueId(), () -> claimSequentially(entries, 0, 0, false), claimed ->
                plugin.scheduler().atPlayer(player, () -> {
                    int count = claimed == null ? 0 : claimed[0];
                    boolean full = claimed != null && claimed[1] == 1;
                    if (count == 0 && !full) {
                        plugin.messages().send(player, "mailbox.claim-all-none");
                    } else if (count == 0) {
                        Feedback.error(plugin, player, "mailbox.inventory-full");
                    } else {
                        plugin.messages().send(player, full ? "mailbox.claim-all-partial" : "mailbox.claim-all", "count", String.valueOf(count));
                        sound("claim");
                    }
                    refresh();
                }));
        if (!started) plugin.messages().send(player, "general.busy");
    }

    /** Claims one entry after the other; stops when the inventory is full. Returns {claimed, inventoryFull}. */
    private CompletableFuture<int[]> claimSequentially(List<Object> entries, int index, int claimed, boolean full) {
        if (index >= entries.size() || full) {
            return CompletableFuture.completedFuture(new int[]{claimed, full ? 1 : 0});
        }
        Object entry = entries.get(index);
        CompletableFuture<ClaimResult> step;
        if (entry instanceof MailRecord mail && mail.state() == MailState.PENDING) {
            step = plugin.mail().claim(player, mail.id());
        } else if (entry instanceof Campaign campaign) {
            step = plugin.campaigns().claim(player, campaign);
        } else {
            return claimSequentially(entries, index + 1, claimed, false);
        }
        return step.thenCompose(result -> {
            boolean success = result.success();
            boolean stop = result.status() == ClaimResult.Status.PARTIAL || result.status() == ClaimResult.Status.INVENTORY_FULL
                    || result.status() == ClaimResult.Status.OFFLINE || result.status() == ClaimResult.Status.ERROR;
            return claimSequentially(entries, index + 1, claimed + (success ? 1 : 0), stop);
        });
    }

    private void toggleParcels() {
        boolean accept = !data.acceptsParcels();
        plugin.scheduler().run(() -> plugin.storage().setAcceptParcels(player.getUniqueId(), accept)).thenRun(() ->
                plugin.scheduler().atPlayer(player, () -> {
                    plugin.messages().send(player, accept ? "block.accepting" : "block.refusing");
                    sound("click");
                    refresh();
                }));
    }

    private void redeemPrompt() {
        plugin.guiManager().prompts().ask(player, "codes.prompt", code -> plugin.codes().redeem(player, code).thenAccept(outcome ->
                plugin.scheduler().atPlayer(player, () -> {
                    redeemFeedback(plugin, player, outcome, code);
                    plugin.guiManager().openMailbox(player, MailKind.Tab.REWARDS);
                })), end -> {
            if (end != ChatPrompts.End.LEFT && end != ChatPrompts.End.SHUTDOWN) plugin.guiManager().openMailbox(player, MailKind.Tab.REWARDS);
        });
    }

    public static void redeemFeedback(DonutMailPlugin plugin, Player player, RedeemOutcome outcome, String code) {
        if (outcome == null) {
            Feedback.error(plugin, player, "codes.cooldown");
            return;
        }
        switch (outcome) {
            case OK -> {
                plugin.messages().send(player, "codes.redeemed", "code", code.toUpperCase(Locale.ROOT));
                io.nightbeam.donutmail.util.Sounds.play(player, plugin.settings().raw, "claim");
            }
            case UNKNOWN -> Feedback.error(plugin, player, "codes.unknown");
            case DISABLED -> Feedback.error(plugin, player, "codes.disabled");
            case EXPIRED -> Feedback.error(plugin, player, "codes.expired");
            case USED_UP -> Feedback.error(plugin, player, "codes.used-up");
            case ALREADY_REDEEMED -> Feedback.error(plugin, player, "codes.already");
        }
    }

    /** Reloads the mailbox and redraws it in place if it is still open. */
    private void refresh() {
        MailboxData.load(plugin, player, tab).thenAccept(fresh -> plugin.scheduler().atPlayer(player, () -> {
            if (!isOpen()) return;
            this.data = fresh;
            render();
        }));
    }

    private void reopen() {
        plugin.guiManager().openMailbox(player, tab, page);
    }
}
