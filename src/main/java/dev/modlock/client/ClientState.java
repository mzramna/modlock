package dev.modlock.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import dev.modlock.net.ModLockNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Client copy of the player's access, used only for visuals. The server is always authoritative. */
public final class ClientState {
    private static Set<String> locked = Set.of();
    private static Set<String> mods = Set.of();
    private static Set<String> items = Set.of();
    private static Map<String, String> bundleHint = Map.of();

    private static int lockedContainer = -1;
    private static int lockedSlot = -1;
    private static ItemStack lockedPreview = ItemStack.EMPTY;

    private ClientState() {}

    public static void apply(ModLockNetwork.SyncPayload p) {
        locked = Set.copyOf(p.lockedMods());
        mods = Set.copyOf(p.unlockedMods());
        items = Set.copyOf(p.unlockedItems());
        Map<String, String> hints = new HashMap<>();
        for (String h : p.bundleHints()) {
            int i = h.indexOf('=');
            if (i > 0) hints.merge(h.substring(0, i), h.substring(i + 1), (a, b) -> a + ", " + b);
        }
        bundleHint = Map.copyOf(hints);
    }

    public static void reset() {
        locked = mods = items = Set.of();
        bundleHint = Map.of();
        setLockedResult(-1, -1, ItemStack.EMPTY);
    }

    /** True when the local player cannot hand-craft this item. */
    public static boolean isLockedForMe(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!locked.contains(id.getNamespace())) return false;
        return !mods.contains(id.getNamespace()) && !items.contains(id.toString());
    }

    /** Names of the bundles that contain this mod, or null. */
    public static String bundleHint(String ns) {
        return bundleHint.get(ns);
    }

    public static void setLockedResult(int containerId, int slot, ItemStack stack) {
        lockedContainer = containerId;
        lockedSlot = slot;
        lockedPreview = stack;
    }

    public static int lockedContainer() {
        return lockedContainer;
    }

    public static int lockedSlot() {
        return lockedSlot;
    }

    public static ItemStack lockedPreview() {
        return lockedPreview;
    }
}
