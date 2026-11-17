package dev.modlock.core;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * RP classes. A player has a class when it was given with /modlock class add, or when the player
 * (online) carries the entity tag {@code modlock.class.<class>} — handy for command blocks and quest mods.
 */
public final class PlayerClasses {
    public static final String TAG_PREFIX = "modlock.class.";

    private PlayerClasses() {}

    public static String normalize(String cls) {
        return cls.toLowerCase(Locale.ROOT);
    }

    public static Set<String> of(MinecraftServer server, UUID player) {
        Set<String> out = new HashSet<>();
        ModLockSavedData.PlayerData d = ModLockSavedData.get(server).peek(player);
        if (d != null) out.addAll(d.classes);
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            for (String tag : online.entityTags()) {
                if (tag.startsWith(TAG_PREFIX)) out.add(normalize(tag.substring(TAG_PREFIX.length())));
            }
        }
        return out;
    }

    /** Can the player use this bundle? Unrestricted bundles are usable by everyone. */
    public static boolean canUse(Set<String> classes, ModBundles.Bundle bundle) {
        if (!bundle.restricted()) return true;
        for (String c : bundle.classes()) if (classes.contains(c)) return true;
        return false;
    }
}
