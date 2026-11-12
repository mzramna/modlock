package dev.modlock.client;

import java.util.Set;

import dev.modlock.net.ModLockNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Client copy of the player's access, used only for visuals. The server is always authoritative. */
public final class ClientState {
    private static boolean lockAll;
    private static Set<String> locked = Set.of();
    private static Set<String> global = Set.of();
    private static Set<String> mods = Set.of();
    private static Set<String> items = Set.of();

    private static int lockedContainer = -1;
    private static int lockedSlot = -1;
    private static ItemStack lockedPreview = ItemStack.EMPTY;

    private ClientState() {}

    public static void apply(ModLockNetwork.SyncPayload p) {
        lockAll = p.lockAll();
        locked = Set.copyOf(p.lockedMods());
        global = Set.copyOf(p.globalAllowed());
        mods = Set.copyOf(p.unlockedMods());
        items = Set.copyOf(p.unlockedItems());
    }

    public static void reset() {
        lockAll = false;
        locked = global = mods = items = Set.of();
        setLockedResult(-1, -1, ItemStack.EMPTY);
    }

    public static boolean isModLocked(String ns) {
        if (global.contains(ns)) return false;
        if (lockAll) return !"minecraft".equals(ns);
        return locked.contains(ns);
    }

    /** True when the local player cannot hand-craft this item. */
    public static boolean isLockedForMe(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!isModLocked(id.getNamespace())) return false;
        return !mods.contains(id.getNamespace()) && !items.contains(id.toString());
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
