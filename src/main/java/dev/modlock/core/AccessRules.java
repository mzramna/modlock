package dev.modlock.core;

import java.util.UUID;

import javax.annotation.Nullable;

import dev.modlock.config.ModLockConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Single place that decides what a player may craft or use.
 *
 * Order for crafting: always_allowed recipe/item -> global_allowed mod -> mod not locked
 * -> mod unlocked -> item unlocked -> temporary knowledge (stage 4).
 */
public final class AccessRules {
    private AccessRules() {}

    /** The logical server of this player, or null on the client side. */
    @Nullable
    public static MinecraftServer server(Player player) {
        return player.level().getServer();
    }

    // ---------- mod level ----------

    public static boolean isModLocked(String namespace) {
        if (ModLockConfig.globalAllowedMods.contains(namespace)) return false;
        if (ModLockConfig.lockAll) return !"minecraft".equals(namespace);
        return ModLockConfig.lockedMods.contains(namespace);
    }

    public static boolean hasModAccess(MinecraftServer server, UUID player, String namespace) {
        if (!isModLocked(namespace)) return true;
        ModLockSavedData.PlayerData d = ModLockSavedData.get(server).peek(player);
        if (d != null && d.mods.contains(namespace)) return true;
        return TemporaryKnowledge.hasMod(server, player, namespace);
    }

    // ---------- recipe matching helpers ----------

    /** Recipe ids are registry keys since 1.21.2; the config lists use their plain id. */
    @Nullable
    public static Identifier recipeId(@Nullable net.minecraft.world.item.crafting.RecipeHolder<?> holder) {
        return holder != null ? holder.id().identifier() : null;
    }

    private static boolean listMatches(java.util.Set<String> list, @Nullable Identifier recipeId, ItemStack result) {
        if (list.isEmpty()) return false;
        if (recipeId != null && list.contains(recipeId.toString())) return true;
        if (!result.isEmpty()) {
            return list.contains("item:" + BuiltInRegistries.ITEM.getKey(result.getItem()));
        }
        return false;
    }

    public static boolean isAlwaysAllowed(@Nullable Identifier recipeId, ItemStack result) {
        return listMatches(ModLockConfig.alwaysAllowed, recipeId, result);
    }

    public static boolean isManualOnly(@Nullable Identifier recipeId, ItemStack result) {
        return listMatches(ModLockConfig.manualOnly, recipeId, result);
    }

    // ---------- crafting ----------

    /**
     * Can this player craft the result by hand?
     * Fake players (automation) are always allowed, unless the recipe is manual_only.
     */
    public static boolean canCraft(Player player, @Nullable Identifier recipeId, ItemStack result) {
        if (result.isEmpty()) return true;
        if (player instanceof FakePlayer) return !isManualOnly(recipeId, result);
        if (server(player) == null) return true; // client side: server is authoritative
        return canCraft(server(player), player.getUUID(), recipeId, result);
    }

    /** Works for offline players too (used by furnaces). */
    public static boolean canCraft(MinecraftServer server, UUID player, @Nullable Identifier recipeId, ItemStack result) {
        if (result.isEmpty()) return true;
        if (isAlwaysAllowed(recipeId, result)) return true;
        return hasItemAccess(server, player, result.getItem());
    }

    public static boolean hasItemAccess(MinecraftServer server, UUID player, Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        String ns = id.getNamespace();
        if (!isModLocked(ns)) return true;
        ModLockSavedData.PlayerData d = ModLockSavedData.get(server).peek(player);
        if (d != null && (d.mods.contains(ns) || d.items.contains(id))) return true;
        return TemporaryKnowledge.hasMod(server, player, ns) || TemporaryKnowledge.hasItem(server, player, id);
    }

    // ---------- machines / usage ----------

    /** Machine License (stage 5) or access to the block's mod. */
    public static boolean canUseMachine(Player player, Block block) {
        if (player instanceof FakePlayer || server(player) == null) return true;
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (hasMachineLicense(player, id)) return true;
        return hasModAccess(server(player), player.getUUID(), id.getNamespace());
    }

    public static boolean hasMachineLicense(Player player, Identifier blockId) {
        if (server(player) == null) return false;
        ModLockSavedData.PlayerData d = ModLockSavedData.get(server(player)).peek(player.getUUID());
        if (d != null && d.machines.contains(blockId)) return true;
        return TemporaryKnowledge.hasMachine(server(player), player.getUUID(), blockId);
    }

    public static boolean canUseNamespace(Player player, String namespace) {
        if (player instanceof FakePlayer || server(player) == null) return true;
        return hasModAccess(server(player), player.getUUID(), namespace);
    }
}
