package dev.modlock.gametest;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.common.EffectCures;
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

    @GameTest(template = "empty")
    public static void bundleGrantsItsMods(GameTestHelper h) {
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

    @GameTest(template = "empty")
    public static void bundleDetectsAddons(GameTestHelper h) {
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

    @GameTest(template = "empty")
    public static void closedBundleIsOnlyForItsClass(GameTestHelper h) {
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

    @GameTest(template = "empty")
    public static void startingChoiceGrantsAndIsExclusive(GameTestHelper h) {
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

    @GameTest(template = "empty")
    public static void keyAllGrantsModsAndBundles(GameTestHelper h) {
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

    @GameTest(template = "empty")
    public static void keyRandomGrantsAmount(GameTestHelper h) {
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

    @GameTest(template = "empty")
    public static void keyAlreadyOwnedIsNotConsumed(GameTestHelper h) {
        lockVanilla();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        grantVanilla(h, p, true);
        KeyData key = new KeyData(List.of("minecraft", "neoforge"), 2, KeyData.Mode.ALL);
        // neoforge is not locked (free) and minecraft is owned
        h.assertTrue(Redemption.keyCandidates(p, key).isEmpty(), "owned and free mods are not candidates");
        h.assertTrue(!Redemption.useKey(p, InteractionHand.MAIN_HAND, ItemStack.EMPTY, key), "not used up");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void scrollPermanentUnlocksCraft(GameTestHelper h) {
        lockVanilla();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        h.assertTrue(!craftsPlanks(h, p), "locked before");
        h.assertTrue(Redemption.useScroll(p, teach("minecraft:oak_planks", true, 1)), "permanent scroll used up");
        h.assertTrue(data(h, p).items.contains(ResourceLocation.withDefaultNamespace("oak_planks")), "item saved");
        h.assertTrue(craftsPlanks(h, p), "craftable after the scroll");
        h.assertTrue(!Redemption.useScroll(p, teach("minecraft:oak_planks", true, 1)), "already known -> not used up");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void scrollTemporaryAppliesEffect(GameTestHelper h) {
        lockVanilla();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        var effect = KnowledgeEffects.recipe(ResourceLocation.withDefaultNamespace("oak_planks"));
        h.assertTrue(effect != null, "recipe effect registered for minecraft:oak_planks");
        h.assertTrue(Redemption.useScroll(p, teach("minecraft:oak_planks", false, 5)), "temporary scroll used up");
        h.assertTrue(p.hasEffect(effect), "effect applied");
        h.assertTrue(p.getEffect(effect).getDuration() == 5 * 1200, "5 minutes");
        h.assertTrue(craftsPlanks(h, p), "craftable while the effect lasts");
        p.removeEffect(effect);
        h.assertTrue(!craftsPlanks(h, p), "locked again once the effect is gone");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void scrollConfigurationIsValidated(GameTestHelper h) {
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

    @GameTest(template = "empty")
    public static void mundaneTomeNeedsOwnership(GameTestHelper h) {
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

    @GameTest(template = "empty")
    public static void pureTomeRespectsRestrictions(GameTestHelper h) {
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

    @GameTest(template = "empty")
    public static void pureTomeBoundToBundle(GameTestHelper h) {
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

    @GameTest(template = "empty")
    public static void milkCuresOnlyCurableKnowledge(GameTestHelper h) {
        lockVanilla();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        var mc = KnowledgeEffects.knowledge("minecraft");
        var planks = KnowledgeEffects.recipe(ResourceLocation.withDefaultNamespace("oak_planks"));
        TemporaryKnowledge.apply(p, mc, 10, true, Component.literal("test"));
        TemporaryKnowledge.apply(p, planks, 10, false, Component.literal("test"));
        p.removeEffectsCuredBy(EffectCures.MILK);
        h.assertTrue(!p.hasEffect(mc), "curable knowledge removed by milk");
        h.assertTrue(p.hasEffect(planks), "milk-proof knowledge stays");
        ModLockConfig.milkMode = ModLockConfig.MilkMode.NEVER;
        h.assertTrue(!TemporaryKnowledge.resolveCurable(true), "global NEVER wins over the item");
        ModLockConfig.milkMode = ModLockConfig.MilkMode.ALWAYS;
        h.assertTrue(TemporaryKnowledge.resolveCurable(false), "global ALWAYS wins over the item");
        ModLockConfig.milkMode = ModLockConfig.MilkMode.PER_ITEM;
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void knowledgeStacking(GameTestHelper h) {
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
            h.assertTrue(!TemporaryKnowledge.isCurable(p.getEffect(mc)), "immune wins");

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

    @GameTest(template = "empty")
    public static void machineLicenseAllowsManualInsert(GameTestHelper h) {
        lockVanilla();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        h.assertTrue(RecipeIndex.manualInsertBlocked(p, Blocks.FURNACE, new ItemStack(Items.RAW_IRON)), "blocked before");
        h.assertTrue(ItemConfigs.canLicense(p, Blocks.FURNACE, true, true), "OP can license a block entity block");
        h.assertTrue(!ItemConfigs.canLicense(p, Blocks.STONE, true, true), "not a machine");
        h.assertTrue(Redemption.useLicense(p, teach("minecraft:furnace", true, 1)), "license used up");
        h.assertTrue(AccessRules.hasMachineLicense(p, ResourceLocation.withDefaultNamespace("furnace")), "license recorded");
        h.assertTrue(!RecipeIndex.manualInsertBlocked(p, Blocks.FURNACE, new ItemStack(Items.RAW_IRON)), "allowed with the license");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blockModlockCraftingBlocksScroll(GameTestHelper h) {
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
