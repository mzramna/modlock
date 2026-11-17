package dev.modlock.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import dev.modlock.ModLock;
import dev.modlock.attach.ModLockAttachments;
import dev.modlock.config.ModLockConfig;
import dev.modlock.core.AccessRules;
import dev.modlock.core.ModBundles;
import dev.modlock.core.ModLockSavedData;
import dev.modlock.core.StartingChoice;
import dev.modlock.core.RecipeIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Runs with ./gradlew runGameTestServer. "minecraft" is locked during the tests so vanilla recipes
 * can be used as stand-ins for mod recipes.
 *
 * Since 1.21.5 game tests are registry entries: each test body is a {@code minecraft:test_function}
 * (registered here with a DeferredRegister) and a {@code minecraft:test_instance} that points to it,
 * registered in {@link RegisterGameTestsEvent} together with an empty test environment.
 * makeMockServerPlayerInLevel is deprecated for removal but is still the only way to get a ServerPlayer.
 */
@SuppressWarnings("removal")
public class ModLockGameTests {
    private static final Identifier STRUCTURE = Identifier.fromNamespaceAndPath(ModLock.MODID, "empty");
    private static final int DEFAULT_TIMEOUT = 100;

    private record Test(String name, int maxTicks, Consumer<GameTestHelper> body) {}

    private static final List<Test> TESTS = new ArrayList<>();
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, ModLock.MODID);

    private static void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
        TESTS.add(new Test(name, maxTicks, body));
        FUNCTIONS.register(name, () -> body);
    }

    static {
        test("crafting_table_blocks_locked_result", DEFAULT_TIMEOUT, ModLockGameTests::craftingTableBlocksLockedResult);
        test("always_allowed_bypasses_lock", DEFAULT_TIMEOUT, ModLockGameTests::alwaysAllowedBypassesLock);
        test("stonecutter_blocks_locked_result", DEFAULT_TIMEOUT, ModLockGameTests::stonecutterBlocksLockedResult);
        test("furnace_menu_records_inserter", DEFAULT_TIMEOUT, ModLockGameTests::furnaceMenuRecordsInserter);
        test("furnace_does_not_cook_for_locked_inserter", 400, ModLockGameTests::furnaceDoesNotCookForLockedInserter);
        test("furnace_cooks_for_automation", 400, ModLockGameTests::furnaceCooksForAutomation);
        test("manual_insert_rule", DEFAULT_TIMEOUT, ModLockGameTests::manualInsertRule);
        test("bundle_grants_its_mods", DEFAULT_TIMEOUT, ModLockGameTests::bundleGrantsItsMods);
        test("bundle_detects_addons", DEFAULT_TIMEOUT, ModLockGameTests::bundleDetectsAddons);
        test("closed_bundle_is_only_for_its_class", DEFAULT_TIMEOUT, ModLockGameTests::closedBundleIsOnlyForItsClass);
        test("starting_choice_grants_and_is_exclusive", DEFAULT_TIMEOUT, ModLockGameTests::startingChoiceGrantsAndIsExclusive);
    }

    /** Called from the mod constructor. Does nothing outside dev / game test servers. */
    public static void register(IEventBus modBus) {
        if (!GameTestHooks.isGametestEnabled()) return;
        FUNCTIONS.register(modBus);
        modBus.addListener(ModLockGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(ModLock.MODID, "default"), new TestEnvironmentDefinition.AllOf(List.of()));
        for (Test t : TESTS) {
            Identifier id = Identifier.fromNamespaceAndPath(ModLock.MODID, t.name());
            event.registerTest(id, new FunctionGameTestInstance(
                    ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(env, STRUCTURE, t.maxTicks(), 0, true)));
        }
    }

    private static void lockVanilla() {
        ModBundles.apply(Map.of(), Map.of());
        ModLockConfig.lockBundleMembers = true;
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

    private static void craftingTableBlocksLockedResult(GameTestHelper h) {
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

    private static void alwaysAllowedBypassesLock(GameTestHelper h) {
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

    private static void stonecutterBlocksLockedResult(GameTestHelper h) {
        lockVanilla();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        StonecutterMenu menu = new StonecutterMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(BlockPos.ZERO)));
        menu.getSlot(0).set(new ItemStack(Items.STONE));
        menu.clickMenuButton(p, 0);
        h.assertTrue(menu.getSlot(1).getItem().isEmpty(), "stonecutter result should be hidden");
        grantVanilla(h, p, true);
        // 26.1: clicking the already selected recipe is a no-op, so select another one
        menu.clickMenuButton(p, 1);
        h.assertTrue(!menu.getSlot(1).getItem().isEmpty(), "stonecutter result should appear after grant");
        h.succeed();
    }

    private static void furnaceMenuRecordsInserter(GameTestHelper h) {
        lockVanilla();
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, Blocks.FURNACE);
        FurnaceBlockEntity furnace = h.getBlockEntity(pos, FurnaceBlockEntity.class);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        FurnaceMenu menu = new FurnaceMenu(1, p.getInventory(), furnace, new SimpleContainerData(4));
        menu.setCarried(new ItemStack(Items.RAW_IRON, 4));
        menu.clicked(0, 0, ContainerInput.PICKUP, p);
        h.assertTrue(furnace.getItem(0).is(Items.RAW_IRON), "item should be placed in the furnace");
        h.assertTrue(p.getUUID().toString().equals(furnace.getData(ModLockAttachments.FURNACE_INSERTER)),
                "furnace should remember the inserter");
        h.succeed();
    }

    private static void furnaceDoesNotCookForLockedInserter(GameTestHelper h) {
        lockVanilla();
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, Blocks.FURNACE);
        FurnaceBlockEntity furnace = h.getBlockEntity(pos, FurnaceBlockEntity.class);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON));
        furnace.setItem(1, new ItemStack(Items.COAL));
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        furnace.setData(ModLockAttachments.FURNACE_INSERTER, p.getUUID().toString());
        h.runAfterDelay(260, () -> {
            h.assertTrue(furnace.getItem(2).isEmpty(), "furnace must not cook for a player without access");
            h.succeed();
        });
    }

    private static void furnaceCooksForAutomation(GameTestHelper h) {
        lockVanilla();
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, Blocks.FURNACE);
        FurnaceBlockEntity furnace = h.getBlockEntity(pos, FurnaceBlockEntity.class);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON));
        furnace.setItem(1, new ItemStack(Items.COAL));
        h.succeedWhen(() -> h.assertTrue(furnace.getItem(2).is(Items.IRON_INGOT), "automation input should cook"));
    }

    private static void manualInsertRule(GameTestHelper h) {
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

    // ---------------- bundles ----------------

    private static ModBundles.Definition def(List<String> mods, boolean addons, List<String> exclude,
                                             List<String> classes, boolean closed) {
        return new ModBundles.Definition("Test", null, null, "", mods, addons, List.of(), exclude, classes, closed);
    }

    private static boolean craftsPlanks(GameTestHelper h, ServerPlayer p) {
        CraftingMenu menu = new CraftingMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(BlockPos.ZERO)));
        menu.getSlot(1).set(new ItemStack(Items.OAK_LOG));
        return menu.getSlot(0).getItem().is(Items.OAK_PLANKS);
    }

    private static void bundleGrantsItsMods(GameTestHelper h) {
        lockVanilla();
        ModBundles.apply(Map.of("vanilla", def(List.of("minecraft"), false, List.of(), List.of(), false)), Map.of());
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        h.assertTrue(!craftsPlanks(h, p), "locked before the bundle");
        var d = ModLockSavedData.get(h.getLevel().getServer()).player(p.getUUID());
        d.bundles.add("vanilla");
        h.assertTrue(craftsPlanks(h, p), "bundle should unlock its mods");
        d.bundles.remove("vanilla");
        h.assertTrue(!craftsPlanks(h, p), "revoked bundle should lock again");
        ModBundles.apply(Map.of(), Map.of());
        h.succeed();
    }

    private static void bundleDetectsAddons(GameTestHelper h) {
        lockVanilla();
        // modlock declares a REQUIRED dependency on neoforge, so it is an "addon" of a neoforge bundle.
        ModBundles.apply(Map.of(
                "nf", def(List.of("neoforge"), true, List.of(), List.of(), false),
                "nf_no_ml", def(List.of("neoforge"), true, List.of("modlock"), List.of(), false),
                "nf_plain", def(List.of("neoforge"), false, List.of(), List.of(), false)), Map.of());
        h.assertTrue(ModBundles.get("nf").members().contains("modlock"), "addon should be detected");
        h.assertTrue(!ModBundles.get("nf_no_ml").members().contains("modlock"), "exclude must win");
        h.assertTrue(!ModBundles.get("nf_plain").members().contains("modlock"), "include_addons=false");

        ModLockConfig.lockedMods = Set.of("neoforge");
        h.assertTrue(AccessRules.isModLocked("modlock"), "addon of a locked base is locked");
        ModLockConfig.lockBundleMembers = false;
        h.assertTrue(!AccessRules.isModLocked("modlock"), "lock_bundle_members=false leaves addons free");
        lockVanilla();
        h.succeed();
    }

    private static void closedBundleIsOnlyForItsClass(GameTestHelper h) {
        lockVanilla();
        ModBundles.apply(Map.of("mage", def(List.of("minecraft"), false, List.of(), List.of("mage"), true)), Map.of());
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        var d = ModLockSavedData.get(h.getLevel().getServer()).player(p.getUUID());
        d.mods.add("minecraft");
        h.assertTrue(!craftsPlanks(h, p), "closed bundle denies other classes even with a direct grant");
        d.bundles.add("mage");
        h.assertTrue(!craftsPlanks(h, p), "bundle without the class does nothing");
        d.classes.add("mage");
        h.assertTrue(craftsPlanks(h, p), "right class can use it");
        ModBundles.apply(Map.of(), Map.of());
        h.succeed();
    }

    private static void startingChoiceGrantsAndIsExclusive(GameTestHelper h) {
        lockVanilla();
        ModLockConfig.startingCount = 1;
        ModLockConfig.allowSingleMods = false;
        ModLockConfig.exclusive = true;
        ModLockConfig.announceChoice = false;
        try {
            ModBundles.apply(Map.of(
                    "a", def(List.of("minecraft"), false, List.of(), List.of(), false),
                    "b", def(List.of("neoforge"), false, List.of(), List.of(), false)), Map.of());
            var server = h.getLevel().getServer();
            ServerPlayer p1 = h.makeMockServerPlayerInLevel();
            ServerPlayer p2 = h.makeMockServerPlayerInLevel();
            h.assertTrue(StartingChoice.options(server, p1.getUUID()).size() == 2, "two bundles offered");

            StartingChoice.answer(p1, List.of("bundle:a", "bundle:b"));
            h.assertTrue(!ModLockSavedData.get(server).player(p1.getUUID()).madeInitialChoice, "too many picks rejected");

            StartingChoice.answer(p1, List.of("bundle:a"));
            var d1 = ModLockSavedData.get(server).player(p1.getUUID());
            h.assertTrue(d1.madeInitialChoice && d1.bundles.contains("a"), "pick granted");

            var opts = StartingChoice.options(server, p2.getUUID());
            h.assertTrue(opts.stream().anyMatch(o -> o.key().equals("bundle:a") && !o.takenBy().isEmpty()),
                    "taken bundle shown as taken");
            StartingChoice.answer(p2, List.of("bundle:a"));
            h.assertTrue(!ModLockSavedData.get(server).player(p2.getUUID()).bundles.contains("a"), "exclusive");
        } finally {
            ModLockConfig.exclusive = false;
            ModBundles.apply(Map.of(), Map.of());
        }
        h.succeed();
    }
}
