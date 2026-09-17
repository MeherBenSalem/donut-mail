package io.nightbeam.donutmail.command;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.gui.GuiManager;
import io.nightbeam.donutmail.service.MailService;
import io.nightbeam.donutmail.service.TargetResolver;
import io.nightbeam.donutmail.service.VoucherService;
import io.nightbeam.donutmail.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class MailCommand implements CommandExecutor, TabCompleter {
    private final DonutMailPlugin plugin;
    private final GuiManager guiManager;
    private final MailService mailService;
    private final VoucherService voucherService;
    private final TargetResolver targetResolver;

    public MailCommand(
            DonutMailPlugin plugin,
            GuiManager guiManager,
            MailService mailService,
            VoucherService voucherService,
            TargetResolver targetResolver
    ) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.mailService = mailService;
        this.voucherService = voucherService;
        this.targetResolver = targetResolver;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(msg("messages.player-only"));
                return true;
            }
            if (!hasPerm(sender, "donutmail.use")) {
                sender.sendMessage(msg("messages.no-permission"));
                return true;
            }
            guiManager.openMailbox(player, 0);
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("reload")) {
            if (!hasPerm(sender, "donutmail.admin")) {
                sender.sendMessage(msg("messages.no-permission"));
                return true;
            }
            plugin.reloadPluginRuntime();
            sender.sendMessage(msg("messages.reloaded"));
            return true;
        }

        if (sub.equals("send")) {
            if (!hasPerm(sender, "donutmail.admin")) {
                sender.sendMessage(msg("messages.no-permission"));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(msg("messages.usage-send"));
                return true;
            }

            String targetToken = args[1];
            String message = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            Set<UUID> targets = targetResolver.resolveTargets(targetToken);
            int sent = 0;
            for (UUID target : targets) {
                mailService.sendMail(target, sender.getName(), message, List.of(), null, List.of());
                sent++;
            }
            sender.sendMessage(msg("messages.sent").replace("%count%", String.valueOf(sent)));
            return true;
        }

        if (sub.equals("senditem")) {
            if (!hasPerm(sender, "donutmail.senditem") && !hasPerm(sender, "donutmail.admin")) {
                sender.sendMessage(msg("messages.no-permission"));
                return true;
            }
            if (!(sender instanceof Player playerSender)) {
                sender.sendMessage(msg("messages.player-only"));
                return true;
            }
            if (args.length != 2) {
                sender.sendMessage(Text.color("&e/mail senditem <player>"));
                return true;
            }

            String targetName = args[1];
            if (targetName.equalsIgnoreCase("all") || targetName.equalsIgnoreCase("allonline") || targetName.contains(";")) {
                sender.sendMessage(Text.color("&c/senditem now accepts one player only."));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if (target.getName() == null && !target.hasPlayedBefore()) {
                sender.sendMessage(Text.color("&cUnknown player: &f" + targetName));
                return true;
            }

            guiManager.openItemCompose(playerSender, target.getUniqueId(), target.getName() == null ? targetName : target.getName());
            sender.sendMessage(Text.color("&aCompose item mail for &f" + (target.getName() == null ? targetName : target.getName()) + "&a."));
            return true;
        }

        if (sub.equals("voucher")) {
            if (!hasPerm(sender, "donutmail.admin")) {
                sender.sendMessage(msg("messages.no-permission"));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("sendall")) {
                if (args.length < 3) {
                    sender.sendMessage(msg("messages.usage-voucher-sendall"));
                    return true;
                }
                return sendVoucher(sender, args[2], "all");
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("send")) {
                if (args.length < 4) {
                    sender.sendMessage(msg("messages.usage-voucher-send"));
                    return true;
                }
                return sendVoucher(sender, args[2], args[3]);
            }
        }

        if (sender instanceof Player player) {
            guiManager.openMailbox(player, 0);
            return true;
        }

        sender.sendMessage(msg("messages.player-only"));
        return true;
    }

    private boolean sendVoucher(CommandSender sender, String voucherId, String targetsToken) {
        if (!voucherService.exists(voucherId)) {
            sender.sendMessage(Text.color("&cUnknown voucher id: &f" + voucherId));
            return true;
        }

        Set<UUID> targets = targetResolver.resolveTargets(targetsToken);
        int sent = 0;
        for (UUID target : targets) {
            mailService.sendMail(
                    target,
                    sender.getName(),
                    "&6Voucher Delivery\n\n&fYou received a voucher: &e" + voucherId,
                    List.of(),
                    voucherId,
                    List.of()
            );
            sent++;
        }

        sender.sendMessage(msg("messages.sent").replace("%count%", String.valueOf(sent)));
        return true;
    }

    private String msg(String path) {
        return Text.color(plugin.getConfig().getString(path, ""));
    }

    private boolean hasPerm(CommandSender sender, String node) {
        if (sender.hasPermission(node)) {
            return true;
        }
        if (node.startsWith("donutmail.")) {
            return sender.hasPermission("postmaster." + node.substring("donutmail.".length()));
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return startsWith(args[0], List.of("send", "senditem", "voucher", "reload"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("voucher")) {
            return startsWith(args[1], List.of("send", "sendall"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("voucher") && (args[1].equalsIgnoreCase("send") || args[1].equalsIgnoreCase("sendall"))) {
            List<String> ids = new ArrayList<>();
            voucherService.all().forEach(voucher -> ids.add(voucher.id()));
            return startsWith(args[2], ids);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            List<String> names = new ArrayList<>();
            names.add("all");
            names.add("allonline");
            Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
            return startsWith(args[1], names);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("senditem")) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
            return startsWith(args[1], names);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("voucher") && args[1].equalsIgnoreCase("send")) {
            List<String> names = new ArrayList<>();
            names.add("all");
            names.add("allonline");
            Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
            return startsWith(args[3], names);
        }
        return Collections.emptyList();
    }

    private List<String> startsWith(String input, List<String> values) {
        String lower = input.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase().startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }
}
