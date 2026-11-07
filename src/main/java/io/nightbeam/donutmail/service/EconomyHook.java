package io.nightbeam.donutmail.service;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Locale;

/**
 * Vault's Economy service (Vault or VaultUnlocked plus any economy plugin), looked up when needed so the
 * load order of plugins does not matter. Without it, money features are turned off with a message.
 */
public final class EconomyHook {
    private final boolean vaultPresent;

    public EconomyHook() {
        boolean present;
        try {
            Class.forName("net.milkbowl.vault.economy.Economy");
            present = true;
        } catch (ClassNotFoundException | LinkageError missing) {
            present = false;
        }
        this.vaultPresent = present;
    }

    private Economy economy() {
        if (!vaultPresent) return null;
        try {
            RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
            return provider == null ? null : provider.getProvider();
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    public boolean available() {
        return economy() != null;
    }

    public String name() {
        Economy economy = economy();
        if (economy == null) return vaultPresent ? "Vault (no economy plugin)" : "none";
        return economy.getName();
    }

    public double balance(OfflinePlayer player) {
        Economy economy = economy();
        return economy == null ? 0 : economy.getBalance(player);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (amount <= 0) return true;
        Economy economy = economy();
        if (economy == null || !economy.has(player, amount)) return false;
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0) return true;
        Economy economy = economy();
        if (economy == null) return false;
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    public String format(double amount) {
        Economy economy = economy();
        if (economy != null) {
            try {
                return economy.format(amount);
            } catch (RuntimeException ignored) {
                // Fall through to the plain format.
            }
        }
        return plain(amount);
    }

    public static String plain(double amount) {
        if (amount == Math.rint(amount)) return String.format(Locale.US, "$%,.0f", amount);
        return String.format(Locale.US, "$%,.2f", amount);
    }
}
