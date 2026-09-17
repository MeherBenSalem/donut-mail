package io.nightbeam.donutmail.model;

import org.bukkit.Material;

import java.util.List;

public record VoucherDefinition(
        String id,
        String name,
        Material icon,
        List<String> lore,
        List<String> commands
) {
}
