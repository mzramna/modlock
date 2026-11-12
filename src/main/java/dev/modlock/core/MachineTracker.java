package dev.modlock.core;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import dev.modlock.ModLock;
import dev.modlock.config.ModLockConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.block.Block;

/**
 * Remembers which block a player right-clicked to open a menu, so that the manual-insert rule
 * knows which machine the open GUI belongs to. Menus of storage and vanilla stations are never tracked.
 */
public final class MachineTracker {
    public static final TagKey<Block> INSERT_EXEMPT =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ModLock.MODID, "insert_exempt"));

    private record Click(BlockPos pos, Block block, long tick) {}

    private static final Map<UUID, Click> lastClick = new ConcurrentHashMap<>();
    private static final Map<AbstractContainerMenu, Block> bound = new WeakHashMap<>();

    private MachineTracker() {}

    public static void onRightClickBlock(UUID player, BlockPos pos, Block block, long tick) {
        lastClick.put(player, new Click(pos.immutable(), block, tick));
    }

    public static synchronized void onMenuOpen(UUID player, AbstractContainerMenu menu, long tick) {
        Click c = lastClick.remove(player);
        if (c == null || tick - c.tick() > 2) return;
        if (isExemptMenu(menu) || isExemptBlock(c.block())) return;
        bound.put(menu, c.block());
    }

    public static synchronized void onMenuClose(AbstractContainerMenu menu) {
        bound.remove(menu);
    }

    @Nullable
    public static synchronized Block machineOf(AbstractContainerMenu menu) {
        return bound.get(menu);
    }

    public static void forget(UUID player) {
        lastClick.remove(player);
    }

    private static boolean isExemptMenu(AbstractContainerMenu m) {
        return m instanceof InventoryMenu || m instanceof CraftingMenu || m instanceof ChestMenu
                || m instanceof ShulkerBoxMenu || m instanceof HopperMenu || m instanceof DispenserMenu
                || m instanceof AbstractFurnaceMenu || m instanceof ItemCombinerMenu || m instanceof StonecutterMenu
                || m instanceof EnchantmentMenu || m instanceof BeaconMenu || m instanceof GrindstoneMenu
                || m instanceof LoomMenu || m instanceof CartographyTableMenu || m instanceof BrewingStandMenu
                || m instanceof CrafterMenu || m instanceof MerchantMenu || m instanceof LecternMenu;
    }

    public static boolean isExemptBlock(Block block) {
        if (block.builtInRegistryHolder().is(INSERT_EXEMPT)) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        var list = ModLockConfig.insertExemptBlocks;
        return list.contains(id.toString()) || list.contains(id.getNamespace() + ":*");
    }
}
