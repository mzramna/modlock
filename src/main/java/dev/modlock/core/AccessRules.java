package dev.modlock.core;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.modlock.config.ModLockConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Single place that decides what a player may craft or use.
 *
 * Order for crafting: always_allowed recipe/item -> closed bundle denial -> global_allowed mod
 * -> mod not locked -> mod unlocked -> usable bundle containing the mod -> item unlocked
 * -> temporary knowledge (stage 4).
 */
public final class AccessRules {
    private AccessRules() {}

    // ---------- mod level ----------

    private static boolean isModLockedDirect(String namespace) {
        if (ModLockConfig.globalAllowedMods.contains(namespace)) return false;
        if (ModLockConfig.lockAll) return !"minecraft".equals(namespace);
        return ModLockConfig.lockedMods.contains(namespace);
    }

    /** Locked for players who have not unlocked it (config, bundle membership or closed bundle). */
    public static boolean isModLocked(String namespace) {
        if (ModBundles.isClosedMember(namespace)) return true;
        if (isModLockedDirect(namespace)) return true;
        if (ModLockConfig.globalAllowedMods.contains(namespace)) return false;
        return ModLockConfig.lockBundleMembers
                && ModBundles.isMemberOfLockedBundle(namespace, AccessRules::isModLockedDirect);
    }

    /**
     * A closed bundle keeps its mods for its classes only: anyone outside every closed bundle
     * that contains the mod is denied, whatever else they unlocked.
     */
    public static boolean isClosedDenied(MinecraftServer server, UUID player, String namespace) {
        Set<String> classes = null;
        boolean anyClosed = false;
        for (ModBundles.Bundle b : ModBundles.bundlesOf(namespace)) {
            if (!b.closed()) continue;
            anyClosed = true;
            if (classes == null) classes = PlayerClasses.of(server, player);
            if (PlayerClasses.canUse(classes, b)) return false;
        }
        return anyClosed;
    }

    /** True if one of the player's unlocked bundles, usable by the player's classes, contains the mod. */
    public static boolean hasBundleAccess(MinecraftServer server, UUID player, String namespace) {
        ModLockSavedData.PlayerData d = ModLockSavedData.get(server).peek(player);
        if (d == null || d.bundles.isEmpty()) return false;
        Set<String> classes = null;
        for (ModBundles.Bundle b : ModBundles.bundlesOf(namespace)) {
            if (!d.bundles.contains(b.id())) continue;
            if (classes == null) classes = PlayerClasses.of(server, player);
            if (PlayerClasses.canUse(classes, b)) return true;
        }
        return false;
    }

    public static boolean hasModAccess(MinecraftServer server, UUID player, String namespace) {
        if (isClosedDenied(server, player, namespace)) return false;
        if (!isModLocked(namespace)) return true;
        ModLockSavedData.PlayerData d = ModLockSavedData.get(server).peek(player);
        if (d != null && d.mods.contains(namespace)) return true;
        if (hasBundleAccess(server, player, namespace)) return true;
        return TemporaryKnowledge.hasMod(server, player, namespace);
    }

    /**
     * Permanent ownership of a mod: unlocked directly or through a usable bundle (not temporary knowledge).
     * Mods that are not locked are not "owned".
     */
    public static boolean ownsModPermanently(MinecraftServer server, UUID player, String namespace) {
        if (!isModLocked(namespace) || isClosedDenied(server, player, namespace)) return false;
        ModLockSavedData.PlayerData d = ModLockSavedData.get(server).peek(player);
        if (d != null && d.mods.contains(namespace)) return true;
        return hasBundleAccess(server, player, namespace);
    }

    /** Can craft this item without any temporary knowledge. */
    public static boolean hasPermanentItemAccess(MinecraftServer server, UUID player, Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String ns = id.getNamespace();
        if (isClosedDenied(server, player, ns)) return false;
        if (!isModLocked(ns)) return true;
        if (ownsModPermanently(server, player, ns)) return true;
        ModLockSavedData.PlayerData d = ModLockSavedData.get(server).peek(player);
        return d != null && d.items.contains(id);
    }

    // ---------- recipe matching helpers ----------

    private static boolean listMatches(java.util.Set<String> list, @Nullable ResourceLocation recipeId, ItemStack result) {
        if (list.isEmpty()) return false;
        if (recipeId != null && list.contains(recipeId.toString())) return true;
        if (!result.isEmpty()) {
            return list.contains("item:" + BuiltInRegistries.ITEM.getKey(result.getItem()));
        }
        return false;
    }

    public static boolean isAlwaysAllowed(@Nullable ResourceLocation recipeId, ItemStack result) {
        return listMatches(ModLockConfig.alwaysAllowed, recipeId, result);
    }

    public static boolean isManualOnly(@Nullable ResourceLocation recipeId, ItemStack result) {
        return listMatches(ModLockConfig.manualOnly, recipeId, result) || isModlockItemBlocked(result);
    }

    /** block_modlock_crafting: no ModLock item can be crafted by anyone, automation included. */
    public static boolean isModlockItemBlocked(ItemStack result) {
        return ModLockConfig.blockModlockCrafting && !result.isEmpty()
                && "modlock".equals(BuiltInRegistries.ITEM.getKey(result.getItem()).getNamespace());
    }

    // ---------- crafting ----------

    /**
     * Can this player craft the result by hand?
     * Fake players (automation) are always allowed, unless the recipe is manual_only.
     */
    public static boolean canCraft(Player player, @Nullable ResourceLocation recipeId, ItemStack result) {
        if (result.isEmpty()) return true;
        if (isModlockItemBlocked(result)) return false;
        if (player instanceof FakePlayer) return !isManualOnly(recipeId, result);
        if (player.getServer() == null) return true; // client side: server is authoritative
        return canCraft(player.getServer(), player.getUUID(), recipeId, result);
    }

    /** Works for offline players too (used by furnaces). */
    public static boolean canCraft(MinecraftServer server, UUID player, @Nullable ResourceLocation recipeId, ItemStack result) {
        if (result.isEmpty()) return true;
        if (isModlockItemBlocked(result)) return false;
        if (isAlwaysAllowed(recipeId, result)) return true;
        return hasItemAccess(server, player, result.getItem());
    }

    public static boolean hasItemAccess(MinecraftServer server, UUID player, Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String ns = id.getNamespace();
        if (hasModAccess(server, player, ns)) return true;
        if (isClosedDenied(server, player, ns)) return false;
        ModLockSavedData.PlayerData d = ModLockSavedData.get(server).peek(player);
        if (d != null && d.items.contains(id)) return true;
        return TemporaryKnowledge.hasItem(server, player, id);
    }

    // ---------- machines / usage ----------

    /** Machine License (stage 5) or access to the block's mod. */
    public static boolean canUseMachine(Player player, Block block) {
        if (player instanceof FakePlayer || player.getServer() == null) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (hasMachineLicense(player, id)) return true;
        return hasModAccess(player.getServer(), player.getUUID(), id.getNamespace());
    }

    public static boolean hasMachineLicense(Player player, ResourceLocation blockId) {
        if (player.getServer() == null) return false;
        ModLockSavedData.PlayerData d = ModLockSavedData.get(player.getServer()).peek(player.getUUID());
        if (d != null && d.machines.contains(blockId)) return true;
        return TemporaryKnowledge.hasMachine(player.getServer(), player.getUUID(), blockId);
    }

    public static boolean canUseNamespace(Player player, String namespace) {
        if (player instanceof FakePlayer || player.getServer() == null) return true;
        return hasModAccess(player.getServer(), player.getUUID(), namespace);
    }
}
