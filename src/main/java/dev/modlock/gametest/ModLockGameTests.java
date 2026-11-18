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
import dev.modlock.core.TemporaryKnowledge;
import dev.modlock.effect.KnowledgeEffects;
import dev.modlock.item.ItemConfigs;
import dev.modlock.item.KeyData;
import dev.modlock.item.ModLockItems;
import dev.modlock.item.Redemption;
import dev.modlock.item.TeachData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
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
        test("key_all_grants_mods_and_bundles", DEFAULT_TIMEOUT, ModLockGameTests::keyAllGrantsModsAndBundles);
        test("key_random_grants_amount", DEFAULT_TIMEOUT, ModLockGameTests::keyRandomGrantsAmount);
        test("key_already_owned_is_not_consumed", DEFAULT_TIMEOUT, ModLockGameTests::keyAlreadyOwnedIsNotConsumed);
        test("scroll_permanent_unlocks_craft", DEFAULT_TIMEOUT, ModLockGameTests::scrollPermanentUnlocksCraft);
        test("scroll_temporary_applies_effect", DEFAULT_TIMEOUT, ModLockGameTests::scrollTemporaryAppliesEffect);
        test("scroll_configuration_is_validated", DEFAULT_TIMEOUT, ModLockGameTests::scrollConfigurationIsValidated);
        test("mundane_tome_needs_ownership", DEFAULT_TIMEOUT, ModLockGameTests::mundaneTomeNeedsOwnership);
        test("pure_tome_respects_restrictions", DEFAULT_TIMEOUT, ModLockGameTests::pureTomeRespectsRestrictions);
        test("pure_tome_bound_to_bundle", DEFAULT_TIMEOUT, ModLockGameTests::pureTomeBoundToBundle);
        test("milk_cures_only_curable_knowledge", DEFAULT_TIMEOUT, ModLockGameTests::milkCuresOnlyCurableKnowledge);
        test("knowledge_stacking", DEFAULT_TIMEOUT, ModLockGameTests::knowledgeStacking);
        test("machine_license_allows_manual_insert", DEFAULT_TIMEOUT, ModLockGameTests::machineLicenseAllowsManualInsert);
        test("block_modlock_crafting_blocks_scroll", DEFAULT_TIMEOUT, ModLockGameTests::blockModlockCraftingBlocksScroll);
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
        resetItemConfig();
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
            // the test world is kept between runs: forget owners from previous runs
            ModLockSavedData.get(server).owners().clear();
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

    // ---------------- unlock items & temporary knowledge ----------------

    private static void resetItemConfig() {
        ModLockConfig.exclusive = false;
        ModLockConfig.keyAnnounceRedeem = false;
        ModLockConfig.blockModlockCrafting = false;
        ModLockConfig.scrollBlacklistItems = Set.of();
        ModLockConfig.scrollRestrictedMods = Set.of();
        ModLockConfig.scrollOpsBypass = true;
        ModLockConfig.scrollPermanentByNonOp = true;
        ModLockConfig.tomeRestrictionMode = ModLockConfig.RestrictionMode.BLACKLIST;
        ModLockConfig.tomeRestrictedMods = Set.of();
        ModLockConfig.tomeAllowSelfUse = false;
        ModLockConfig.pureRestrictionMode = ModLockConfig.RestrictionMode.BLACKLIST;
        ModLockConfig.pureRestrictedMods = Set.of();
        ModLockConfig.purePermanent = false;
        ModLockConfig.pureMinutes = 540;
        ModLockConfig.stackMode = ModLockConfig.StackMode.MAX;
        ModLockConfig.maxKnowledgeMinutes = 540;
        ModLockConfig.milkMode = ModLockConfig.MilkMode.PER_ITEM;
        ModLockConfig.milkUnconfiguredCurable = false;
    }

    private static ModLockSavedData.PlayerData data(GameTestHelper h, ServerPlayer p) {
        return ModLockSavedData.get(h.getLevel().getServer()).player(p.getUUID());
    }

    private static TeachData teach(String target, boolean permanent, int minutes) {
        return new TeachData(target, permanent, minutes, false, "Tester", "", false, "");
    }

    private static void keyAllGrantsModsAndBundles(GameTestHelper h) {
        lockVanilla();
        ModLockConfig.lockedMods = Set.of("minecraft", "neoforge");
        ModBundles.apply(Map.of("nf", def(List.of("neoforge"), false, List.of(), List.of(), false)), Map.of());
        try {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            KeyData key = new KeyData(List.of("minecraft", "bundle:nf"), 1, KeyData.Mode.ALL);
            h.assertTrue(Redemption.useKey(p, InteractionHand.MAIN_HAND, ItemStack.EMPTY, key), "ALL key should be used up");
            var d = data(h, p);
            h.assertTrue(d.mods.contains("minecraft") && d.bundles.contains("nf"), "ALL grants every entry (mod and bundle)");
            h.assertTrue(craftsPlanks(h, p), "granted mod is craftable");
            h.assertTrue(!Redemption.useKey(p, InteractionHand.MAIN_HAND, ItemStack.EMPTY, key),
                    "nothing left -> the key is not used up");
        } finally {
            lockVanilla();
        }
        h.succeed();
    }

    private static void keyRandomGrantsAmount(GameTestHelper h) {
        lockVanilla();
        ModLockConfig.lockedMods = Set.of("minecraft", "neoforge");
        try {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            KeyData key = new KeyData(List.of("minecraft", "neoforge"), 1, KeyData.Mode.RANDOM);
            h.assertTrue(Redemption.keyCandidates(p, key).size() == 2, "two candidates");
            h.assertTrue(Redemption.useKey(p, InteractionHand.MAIN_HAND, ItemStack.EMPTY, key), "used up");
            h.assertTrue(data(h, p).mods.size() == 1, "RANDOM grants exactly amount entries");
            h.assertTrue(Redemption.keyCandidates(p, key).size() == 1, "one left");
        } finally {
            lockVanilla();
        }
        h.succeed();
    }

    private static void keyAlreadyOwnedIsNotConsumed(GameTestHelper h) {
        lockVanilla();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        grantVanilla(h, p, true);
        KeyData key = new KeyData(List.of("minecraft", "neoforge"), 2, KeyData.Mode.ALL);
        // neoforge is not locked (free) and minecraft is owned
        h.assertTrue(Redemption.keyCandidates(p, key).isEmpty(), "owned and free mods are not candidates");
        h.assertTrue(!Redemption.useKey(p, InteractionHand.MAIN_HAND, ItemStack.EMPTY, key), "not used up");
        h.succeed();
    }

    private static void scrollPermanentUnlocksCraft(GameTestHelper h) {
        lockVanilla();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        h.assertTrue(!craftsPlanks(h, p), "locked before");
        h.assertTrue(Redemption.useScroll(p, teach("minecraft:oak_planks", true, 1)), "permanent scroll used up");
        h.assertTrue(data(h, p).items.contains(Identifier.withDefaultNamespace("oak_planks")), "item saved");
        h.assertTrue(craftsPlanks(h, p), "craftable after the scroll");
        h.assertTrue(!Redemption.useScroll(p, teach("minecraft:oak_planks", true, 1)), "already known -> not used up");
        h.succeed();
    }

    private static void scrollTemporaryAppliesEffect(GameTestHelper h) {
        lockVanilla();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        var effect = KnowledgeEffects.recipe(Identifier.withDefaultNamespace("oak_planks"));
        h.assertTrue(effect != null, "recipe effect registered for minecraft:oak_planks");
        h.assertTrue(Redemption.useScroll(p, teach("minecraft:oak_planks", false, 5)), "temporary scroll used up");
        h.assertTrue(p.hasEffect(effect), "effect applied");
        h.assertTrue(p.getEffect(effect).getDuration() == 5 * 1200, "5 minutes");
        h.assertTrue(craftsPlanks(h, p), "craftable while the effect lasts");
        p.removeEffect(effect);
        h.assertTrue(!craftsPlanks(h, p), "locked again once the effect is gone");
        h.succeed();
    }

    private static void scrollConfigurationIsValidated(GameTestHelper h) {
        lockVanilla();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        h.assertTrue(!ItemConfigs.canScroll(p, Items.OAK_PLANKS, false, false), "player cannot teach what they cannot craft");
        h.assertTrue(ItemConfigs.canScroll(p, Items.OAK_PLANKS, true, false), "OP can teach any item of a locked mod");
        grantVanilla(h, p, true);
        h.assertTrue(ItemConfigs.canScroll(p, Items.OAK_PLANKS, false, true), "owner can teach it, permanently too");
        ModLockConfig.scrollBlacklistItems = Set.of("minecraft:oak_planks");
        h.assertTrue(!ItemConfigs.canScroll(p, Items.OAK_PLANKS, false, false), "blacklisted item refused");
        h.assertTrue(ItemConfigs.canScroll(p, Items.OAK_PLANKS, true, false), "OPs bypass the blacklist");
        h.assertTrue(!Redemption.useScroll(h.makeMockServerPlayerInLevel(), teach("minecraft:oak_planks", true, 1)),
                "blacklisted scroll refused at use");
        ModLockConfig.scrollBlacklistItems = Set.of();
        h.succeed();
    }

    private static void mundaneTomeNeedsOwnership(GameTestHelper h) {
        lockVanilla();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        h.assertTrue(!ItemConfigs.canTome(p, "minecraft", false), "non-owner cannot write a tome");
        h.assertTrue(ItemConfigs.canTome(p, "minecraft", true), "OP can");
        grantVanilla(h, p, true);
        h.assertTrue(ItemConfigs.canTome(p, "minecraft", false), "owner can");
        ModLockConfig.tomeRestrictedMods = Set.of("minecraft");
        h.assertTrue(!ItemConfigs.canTome(p, "minecraft", false), "blacklisted mod refused");
        ModLockConfig.tomeRestrictedMods = Set.of();

        ServerPlayer reader = h.makeMockServerPlayerInLevel();
        TeachData tome = new TeachData("minecraft", false, 10, false, "Owner", p.getUUID().toString(), false, "");
        h.assertTrue(!Redemption.useTome(p, tome), "the writer cannot read their own tome (allow_self_use=false)");
        ModLockConfig.tomeRestrictedMods = Set.of("minecraft");
        h.assertTrue(!Redemption.useTome(reader, tome), "restriction re-checked at use");
        ModLockConfig.tomeRestrictedMods = Set.of();
        h.assertTrue(Redemption.useTome(reader, tome), "reader gets the knowledge");
        h.assertTrue(craftsPlanks(h, reader), "knowledge unlocks crafting");
        h.succeed();
    }

    private static void pureTomeRespectsRestrictions(GameTestHelper h) {
        lockVanilla();
        ModLockConfig.lockedMods = Set.of("minecraft", "neoforge");
        ModLockConfig.purePermanent = true;
        try {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            h.assertTrue(Redemption.pureCandidates(p).containsAll(List.of("minecraft", "neoforge")), "both offered");
            ModLockConfig.pureRestrictedMods = Set.of("neoforge");
            List<String> c = Redemption.pureCandidates(p);
            h.assertTrue(c.contains("minecraft") && !c.contains("neoforge"), "blacklisted mod not offered");
            ModLockConfig.pureRestrictionMode = ModLockConfig.RestrictionMode.WHITELIST;
            c = Redemption.pureCandidates(p);
            h.assertTrue(c.equals(List.of("neoforge")), "whitelist offers only listed mods");
            h.assertTrue(Redemption.grantPure(p, "neoforge") && data(h, p).mods.contains("neoforge"), "permanent grant");
        } finally {
            lockVanilla();
        }
        h.succeed();
    }

    private static void pureTomeBoundToBundle(GameTestHelper h) {
        lockVanilla();
        ModBundles.apply(Map.of("vb", def(List.of("minecraft"), false, List.of(), List.of(), false),
                "rp", def(List.of("minecraft"), false, List.of(), List.of("mage"), false)), Map.of());
        try {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            h.assertTrue(!Redemption.usePureBundle(p, "rp"), "class-restricted bundle refused");
            ItemStack tome = new ItemStack(ModLockItems.PURE_TOME.get());
            tome.set(ModLockItems.BUNDLE.get(), "vb");
            h.assertTrue(Redemption.usePureTome(p, InteractionHand.MAIN_HAND, tome), "bound tome used up");
            h.assertTrue(p.hasEffect(KnowledgeEffects.knowledge("minecraft")), "member knowledge applied");
            h.assertTrue(craftsPlanks(h, p), "member craftable");
            h.assertTrue(!Redemption.usePureBundle(p, "vb"), "same duration again -> no gain, not used up");
        } finally {
            lockVanilla();
        }
        h.succeed();
    }

    private static void milkCuresOnlyCurableKnowledge(GameTestHelper h) {
        lockVanilla();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        var mc = KnowledgeEffects.knowledge("minecraft");
        var planks = KnowledgeEffects.recipe(Identifier.withDefaultNamespace("oak_planks"));
        TemporaryKnowledge.apply(p, mc, 10, true, Component.literal("test"));
        TemporaryKnowledge.apply(p, planks, 10, false, Component.literal("test"));
        // 26.1: milk and totems are the clear_all_effects consume effect (no more effect cures)
        var clearAll = net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect.INSTANCE;
        clearAll.apply(h.getLevel(), new ItemStack(Items.TOTEM_OF_UNDYING), p);
        h.assertTrue(p.hasEffect(mc) && p.hasEffect(planks), "a totem keeps all knowledge");
        clearAll.apply(h.getLevel(), new ItemStack(Items.MILK_BUCKET), p);
        h.assertTrue(!p.hasEffect(mc), "curable knowledge removed by milk");
        h.assertTrue(p.hasEffect(planks), "milk-proof knowledge stays");
        ModLockConfig.milkMode = ModLockConfig.MilkMode.NEVER;
        h.assertTrue(!TemporaryKnowledge.resolveCurable(true), "global NEVER wins over the item");
        ModLockConfig.milkMode = ModLockConfig.MilkMode.ALWAYS;
        h.assertTrue(TemporaryKnowledge.resolveCurable(false), "global ALWAYS wins over the item");
        ModLockConfig.milkMode = ModLockConfig.MilkMode.PER_ITEM;
        h.succeed();
    }

    private static void knowledgeStacking(GameTestHelper h) {
        lockVanilla();
        var mc = KnowledgeEffects.knowledge("minecraft");
        Component src = Component.literal("test");
        try {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            ModLockConfig.stackMode = ModLockConfig.StackMode.MAX;
            h.assertTrue(TemporaryKnowledge.apply(p, mc, 10, true, src) == TemporaryKnowledge.ApplyResult.APPLIED, "applied");
            h.assertTrue(TemporaryKnowledge.apply(p, mc, 5, true, src) == TemporaryKnowledge.ApplyResult.NO_GAIN, "MAX: shorter -> no gain");
            h.assertTrue(TemporaryKnowledge.apply(p, mc, 20, false, src) == TemporaryKnowledge.ApplyResult.APPLIED, "MAX: longer applies");
            h.assertTrue(p.getEffect(mc).getDuration() == 20 * 1200, "MAX keeps the longer");
            h.assertTrue(!TemporaryKnowledge.isCurable(p, p.getEffect(mc)), "immune wins");

            ServerPlayer q = h.makeMockServerPlayerInLevel();
            ModLockConfig.stackMode = ModLockConfig.StackMode.ADD;
            TemporaryKnowledge.apply(q, mc, 10, false, src);
            TemporaryKnowledge.apply(q, mc, 5, false, src);
            h.assertTrue(q.getEffect(mc).getDuration() == 15 * 1200, "ADD sums");
            ModLockConfig.maxKnowledgeMinutes = 20;
            TemporaryKnowledge.apply(q, mc, 10, false, src);
            h.assertTrue(q.getEffect(mc).getDuration() == 20 * 1200, "ADD is capped by max_minutes");
            h.assertTrue(TemporaryKnowledge.apply(q, mc, 10, false, src) == TemporaryKnowledge.ApplyResult.NO_GAIN, "at cap -> no gain");
        } finally {
            lockVanilla();
        }
        h.succeed();
    }

    private static void machineLicenseAllowsManualInsert(GameTestHelper h) {
        lockVanilla();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        h.assertTrue(RecipeIndex.manualInsertBlocked(p, Blocks.FURNACE, new ItemStack(Items.RAW_IRON)), "blocked before");
        h.assertTrue(ItemConfigs.canLicense(p, Blocks.FURNACE, true, true), "OP can license a block entity block");
        h.assertTrue(!ItemConfigs.canLicense(p, Blocks.STONE, true, true), "not a machine");
        h.assertTrue(Redemption.useLicense(p, teach("minecraft:furnace", true, 1)), "license used up");
        h.assertTrue(AccessRules.hasMachineLicense(p, Identifier.withDefaultNamespace("furnace")), "license recorded");
        h.assertTrue(!RecipeIndex.manualInsertBlocked(p, Blocks.FURNACE, new ItemStack(Items.RAW_IRON)), "allowed with the license");
        h.succeed();
    }

    private static void blockModlockCraftingBlocksScroll(GameTestHelper h) {
        lockVanilla();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        java.util.function.Supplier<ItemStack> craft = () -> {
            CraftingMenu menu = new CraftingMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(BlockPos.ZERO)));
            String pattern = "PIPILIPIP";
            for (int i = 0; i < 9; i++) {
                menu.getSlot(i + 1).set(new ItemStack(switch (pattern.charAt(i)) {
                    case 'P' -> Items.PAPER;
                    case 'I' -> Items.INK_SAC;
                    default -> Items.LAPIS_LAZULI;
                }));
            }
            return menu.getSlot(0).getItem();
        };
        try {
            h.assertTrue(craft.get().is(ModLockItems.RECIPE_SCROLL.get()), "scroll recipe works");
            ModLockConfig.blockModlockCrafting = true;
            h.assertTrue(craft.get().isEmpty(), "block_modlock_crafting hides the scroll");
            h.assertTrue(AccessRules.isModlockItemBlocked(new ItemStack(ModLockItems.RECIPE_SCROLL.get())), "helper agrees");
        } finally {
            ModLockConfig.blockModlockCrafting = false;
        }
        h.succeed();
    }
}
