package dev.modlock.core;

import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
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

    public static boolean hasItem(MinecraftServer server, UUID player, ResourceLocation item) {
        return false;
    }

    public static boolean hasMachine(MinecraftServer server, UUID player, ResourceLocation block) {
        return false;
    }
}
