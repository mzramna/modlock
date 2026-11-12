package dev.modlock.gametest;

import java.util.Set;

import dev.modlock.ModLock;
import dev.modlock.attach.ModLockAttachments;
import dev.modlock.config.ModLockConfig;
import dev.modlock.core.ModLockSavedData;
import dev.modlock.core.RecipeIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Runs with ./gradlew runGameTestServer. "minecraft" is locked during the tests so vanilla recipes
 * can be used as stand-ins for mod recipes.
 */
@GameTestHolder(ModLock.MODID)
@PrefixGameTestTemplate(false)
public class ModLockGameTests {

    private static void lockVanilla() {
        ModLockConfig.lockedMods = Set.of("minecraft");
        ModLockConfig.globalAllowedMods = Set.of();
        ModLockConfig.lockAll = false;
        ModLockConfig.smeltingEnabled = true;
        ModLockConfig.stationResults = true;
    }

    private static void grantVanilla(GameTestHelper h, ServerPlayer p, boolean grant) {
        var data = ModLockSavedData.get(h.getLevel().getServer()).player(p.getUUID());
        if (grant) data.mods.add("minecraft");
        else data.mods.remove("minecraft");
    }

    @GameTest(template = "empty")
    public static void craftingTableBlocksLockedResult(GameTestHelper h) {
        lockVanilla();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        CraftingMenu menu = new CraftingMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(BlockPos.ZERO)));
        menu.getSlot(1).set(new ItemStack(Items.OAK_LOG));
        h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "result should be hidden without access");

        grantVanilla(h, p, true);
        menu.getSlot(1).set(new ItemStack(Items.OAK_LOG));
        h.assertTrue(menu.getSlot(0).getItem().is(Items.OAK_PLANKS), "result should appear after grant");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void alwaysAllowedBypassesLock(GameTestHelper h) {
        lockVanilla();
        ModLockConfig.alwaysAllowed = Set.of("item:minecraft:oak_planks");
        try {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            CraftingMenu menu = new CraftingMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(BlockPos.ZERO)));
            menu.getSlot(1).set(new ItemStack(Items.OAK_LOG));
            h.assertTrue(menu.getSlot(0).getItem().is(Items.OAK_PLANKS), "always_allowed item must be craftable");
        } finally {
            ModLockConfig.alwaysAllowed = Set.of();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void stonecutterBlocksLockedResult(GameTestHelper h) {
        lockVanilla();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        StonecutterMenu menu = new StonecutterMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(BlockPos.ZERO)));
        menu.getSlot(0).set(new ItemStack(Items.STONE));
        menu.clickMenuButton(p, 0);
        h.assertTrue(menu.getSlot(1).getItem().isEmpty(), "stonecutter result should be hidden");
        grantVanilla(h, p, true);
        menu.clickMenuButton(p, 0);
        h.assertTrue(!menu.getSlot(1).getItem().isEmpty(), "stonecutter result should appear after grant");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void furnaceMenuRecordsInserter(GameTestHelper h) {
        lockVanilla();
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, Blocks.FURNACE);
        FurnaceBlockEntity furnace = h.getBlockEntity(pos);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        FurnaceMenu menu = new FurnaceMenu(1, p.getInventory(), furnace, new SimpleContainerData(4));
        menu.setCarried(new ItemStack(Items.RAW_IRON, 4));
        menu.clicked(0, 0, ClickType.PICKUP, p);
        h.assertTrue(furnace.getItem(0).is(Items.RAW_IRON), "item should be placed in the furnace");
        h.assertTrue(p.getUUID().toString().equals(furnace.getData(ModLockAttachments.FURNACE_INSERTER)),
                "furnace should remember the inserter");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void furnaceDoesNotCookForLockedInserter(GameTestHelper h) {
        lockVanilla();
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, Blocks.FURNACE);
        FurnaceBlockEntity furnace = h.getBlockEntity(pos);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON));
        furnace.setItem(1, new ItemStack(Items.COAL));
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        furnace.setData(ModLockAttachments.FURNACE_INSERTER, p.getUUID().toString());
        h.runAfterDelay(260, () -> {
            h.assertTrue(furnace.getItem(2).isEmpty(), "furnace must not cook for a player without access");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void furnaceCooksForAutomation(GameTestHelper h) {
        lockVanilla();
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, Blocks.FURNACE);
        FurnaceBlockEntity furnace = h.getBlockEntity(pos);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON));
        furnace.setItem(1, new ItemStack(Items.COAL));
        h.succeedWhen(() -> h.assertTrue(furnace.getItem(2).is(Items.IRON_INGOT), "automation input should cook"));
    }

    @GameTest(template = "empty")
    public static void manualInsertRule(GameTestHelper h) {
        lockVanilla();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        // raw iron only smelts/blasts into iron ingot (minecraft, locked) -> blocked
        h.assertTrue(RecipeIndex.manualInsertBlocked(p, Blocks.FURNACE, new ItemStack(Items.RAW_IRON)),
                "raw iron should be refused when every result is locked");
        // an item with no single-ingredient recipe is never blocked
        h.assertTrue(!RecipeIndex.manualInsertBlocked(p, Blocks.FURNACE, new ItemStack(Items.DIAMOND_SWORD)),
                "items without recipes must be accepted");
        grantVanilla(h, p, true);
        h.assertTrue(!RecipeIndex.manualInsertBlocked(p, Blocks.FURNACE, new ItemStack(Items.RAW_IRON)),
                "accepted after grant");
        h.succeed();
    }
}
