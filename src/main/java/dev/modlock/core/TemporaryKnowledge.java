package dev.modlock.core;

import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/**
 * Temporary knowledge granted by effects (Tomes, temporary Recipe Scrolls, Machine Licenses).
 * Stub for stage 1: the dynamic effects are implemented in stage 4.
 */
public final class TemporaryKnowledge {
    private TemporaryKnowledge() {}

    public static boolean hasMod(MinecraftServer server, UUID player, String namespace) {
        return false;
    }

    public static boolean hasItem(MinecraftServer server, UUID player, Identifier item) {
        return false;
    }

    public static boolean hasMachine(MinecraftServer server, UUID player, Identifier block) {
        return false;
    }
}
