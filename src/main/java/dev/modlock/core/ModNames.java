package dev.modlock.core;

import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

/** Mod display names. Packs can translate them with the lang key modlock.mod_name.&lt;modid&gt;. */
public final class ModNames {
    private ModNames() {}

    public static String displayName(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(c -> c.getModInfo().getDisplayName())
                .orElse(modId);
    }

    public static Component component(String modId) {
        return Component.translatableWithFallback("modlock.mod_name." + modId, displayName(modId));
    }
}
