package io.nightbeam.donutmail.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.OfflinePlayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Menu buttons. Names and lore are shown without Minecraft's default italics; lore defaults to gray. */
public final class ItemBuilder {
    /** ItemMeta#setEnchantmentGlintOverride (1.20.5+); older servers show selection in the lore only. */
    private static final Method GLINT = glintMethod();

    private final ItemStack itemStack;

    private ItemBuilder(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(new ItemStack(material == null ? Material.STONE : material));
    }

    public static ItemBuilder of(ItemStack itemStack) {
        return new ItemBuilder(itemStack.clone());
    }

    public ItemBuilder name(Component component) {
        return editMeta(meta -> meta.displayName(Text.noItalic(component)));
    }

    public ItemBuilder lore(List<Component> lore) {
        List<Component> lines = new ArrayList<>(lore.size());
        for (Component line : lore) lines.add(Text.loreLine(line));
        return editMeta(meta -> meta.lore(lines));
    }

    public ItemBuilder hideAll() {
        return editMeta(meta -> meta.addItemFlags(ItemFlag.values()));
    }

    public ItemBuilder glow(boolean glow) {
        if (!glow || GLINT == null) return this;
        return editMeta(meta -> {
            try {
                GLINT.invoke(meta, Boolean.TRUE);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Decoration only.
            }
        });
    }

    public ItemBuilder model(int customModelData) {
        if (customModelData <= 0) return this;
        try {
            editMeta(meta -> meta.setCustomModelData(customModelData));
        } catch (RuntimeException | LinkageError unsupported) {
            // Custom model data is optional decoration.
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        itemStack.setAmount(Math.max(1, Math.min(itemStack.getMaxStackSize(), amount)));
        return this;
    }

    public ItemBuilder owner(OfflinePlayer player) {
        if (player == null) return this;
        return editMeta(meta -> {
            if (meta instanceof SkullMeta skull) {
                try {
                    skull.setOwningPlayer(player);
                } catch (RuntimeException ignored) {
                    // A head without a skin is fine.
                }
            }
        });
    }

    public ItemBuilder editMeta(Consumer<ItemMeta> consumer) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return this;
        consumer.accept(meta);
        itemStack.setItemMeta(meta);
        return this;
    }

    public ItemStack build() {
        return itemStack;
    }

    private static Method glintMethod() {
        try {
            return ItemMeta.class.getMethod("setEnchantmentGlintOverride", Boolean.class);
        } catch (NoSuchMethodException | RuntimeException olderServer) {
            return null;
        }
    }
}
