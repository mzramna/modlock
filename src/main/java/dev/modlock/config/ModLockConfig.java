package dev.modlock.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config (config/modlock-server.toml inside the world's serverconfig folder,
 * or defaultconfigs/ for modpacks). Values are cached in plain sets on load/reload.
 */
public final class ModLockConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.ConfigValue<List<? extends String>> LOCKED_MODS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> GLOBAL_ALLOWED_MODS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ALWAYS_ALLOWED;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> MANUAL_ONLY;
    private static final ModConfigSpec.BooleanValue SMELTING_ENABLED;
    private static final ModConfigSpec.BooleanValue STATION_RESULTS;
    private static final ModConfigSpec.BooleanValue BLOCK_MANUAL_INSERT;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> INSERT_EXEMPT_BLOCKS;
    private static final ModConfigSpec.BooleanValue STRICT_USAGE;
    private static final ModConfigSpec.BooleanValue LOCK_BUNDLE_MEMBERS;
    private static final ModConfigSpec.IntValue STARTING_COUNT;
    private static final ModConfigSpec.BooleanValue SHOW_SELECTION;
    private static final ModConfigSpec.BooleanValue ALLOW_SINGLE_MODS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> CHOOSABLE_MODS;
    private static final ModConfigSpec.BooleanValue EXCLUSIVE;
    private static final ModConfigSpec.BooleanValue ANNOUNCE_CHOICE;
    private static final ModConfigSpec.BooleanValue LOOT_ENABLED;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> LOOT_TOME_BUNDLES;
    private static final ModConfigSpec.BooleanValue BLOCK_MODLOCK_CRAFTING;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("general");
        LOCKED_MODS = b.comment(
                        "Mods controlled by ModLock (mod ids). Items of these mods can only be crafted",
                        "by hand by players who unlocked the mod. Use \"*\" to lock every mod except 'minecraft'",
                        "and the ones in global_allowed_mods.")
                .defineListAllowEmpty("locked_mods", List.of(), () -> "", ModLockConfig::isString);
        GLOBAL_ALLOWED_MODS = b.comment("Mods always allowed for every player, no matter what. Overrides locked_mods.")
                .defineListAllowEmpty("global_allowed_mods", List.of("minecraft"), () -> "", ModLockConfig::isString);
        b.pop();

        b.push("recipes");
        ALWAYS_ALLOWED = b.comment(
                        "Recipes that any player can always craft, even from locked mods.",
                        "Entries are recipe ids (e.g. \"mekanism:crafting/basic_bin\") or item ids prefixed",
                        "with \"item:\" (e.g. \"item:create:andesite_alloy\").")
                .defineListAllowEmpty("always_allowed", List.of(), () -> "", ModLockConfig::isString);
        MANUAL_ONLY = b.comment(
                        "Recipes that can only be crafted by a real player by hand (never by the vanilla Crafter",
                        "block or fake players). Same format as always_allowed. Mod autocrafters that query",
                        "recipes directly (AE2, Refined Storage...) are NOT covered.")
                .defineListAllowEmpty("manual_only", List.of(), () -> "", ModLockConfig::isString);
        b.pop();

        b.push("stations");
        SMELTING_ENABLED = b.comment(
                        "Vanilla furnace, blast furnace and smoker accept items from anyone, but will not cook",
                        "when the player who put the item in cannot craft the result. Items inserted by hoppers",
                        "or automation are never blocked.")
                .define("smelting_enabled", true);
        STATION_RESULTS = b.comment("Also block results in the smithing table and stonecutter.")
                .define("smithing_and_stonecutter", true);
        b.pop();

        b.push("machines");
        BLOCK_MANUAL_INSERT = b.comment(
                        "Players cannot put an item by hand into a machine of another mod when EVERY",
                        "single-ingredient recipe that uses that item produces something they cannot craft.",
                        "If at least one recipe produces something allowed, or no recipe is known, the item is accepted.",
                        "Hoppers/pipes/automation are never checked.")
                .define("block_manual_insert", true);
        INSERT_EXEMPT_BLOCKS = b.comment(
                        "Blocks whose GUI is never checked (storage). Accepts block ids or \"modid:*\".",
                        "The block tag #modlock:insert_exempt is also honored.")
                .defineListAllowEmpty("insert_exempt_blocks", List.of(
                        "sophisticatedstorage:*", "functionalstorage:*", "storagedrawers:*", "ironchest:*",
                        "ae2:*", "refinedstorage:*", "toms_storage:*", "enderstorage:*", "allthemodium:*"),
                        () -> "", ModLockConfig::isString);
        b.pop();

        b.push("strict");
        STRICT_USAGE = b.comment(
                        "Optional, for servers/modpacks: players without access to a mod also cannot place blocks,",
                        "open/interact with blocks, use items or interact with entities of that mod.",
                        "Machines already placed keep working on their own.")
                .define("block_usage_of_locked_mods", false);
        b.pop();

        b.push("starting");
        STARTING_COUNT = b.comment("How many entries (bundles or single mods) a player picks when starting. 0 = no choice.")
                .defineInRange("starting_count", 1, 0, 64);
        SHOW_SELECTION = b.comment(
                        "Open the selection screen on the player's first join. Players can reopen it with",
                        "/modlock choose until they confirm; OPs can reset it with /modlock resetchoice.")
                .define("show_selection_screen", false);
        ALLOW_SINGLE_MODS = b.comment("Besides bundles, also offer single locked mods in the selection screen.")
                .define("allow_single_mods", true);
        CHOOSABLE_MODS = b.comment("Single mods offered (empty = every locked mod).")
                .defineListAllowEmpty("choosable_mods", List.of(), () -> "", ModLockConfig::isString);
        EXCLUSIVE = b.comment("Each bundle / single mod can be picked by only one player.")
                .define("exclusive", false);
        ANNOUNCE_CHOICE = b.comment("Announce in chat what each player picked.")
                .define("announce_choice", true);
        b.pop();

        b.push("loot");
        LOOT_ENABLED = b.comment(
                "Unlock items (Pure Tome, etc.) can be found in loot chests. false = never in loot.",
                "(Takes effect once those items exist; they are not in this version yet.)")
                .define("enabled", true);
        LOOT_TOME_BUNDLES = b.comment(
                "When not empty, only tomes bound to these bundle ids appear in loot (pre-defined bundles).")
                .defineListAllowEmpty("tome_bundles", List.of(), () -> "", ModLockConfig::isString);
        b.pop();

        b.push("items");
        BLOCK_MODLOCK_CRAFTING = b.comment(
                "Nobody (players or automation) can craft any ModLock item. Use it when unlock items",
                "should only come from quests, loot or admins.")
                .define("block_modlock_crafting", false);
        b.pop();

        b.push("bundles");
        LOCK_BUNDLE_MEMBERS = b.comment(
                        "Bundles are defined in config/modlock/bundles/<id>.json (see _example.json.txt there).",
                        "When true, every member of a bundle (e.g. Create addons) is locked whenever one of the",
                        "bundle's base mods is locked, even if the addon itself is not in locked_mods.")
                .define("lock_bundle_members", true);
        b.pop();

        SPEC = b.build();
    }

    private static boolean isString(Object o) {
        return o instanceof String;
    }

    // ---- cached values ----
    public static volatile Set<String> lockedMods = Set.of();
    public static volatile boolean lockAll = false;
    public static volatile Set<String> globalAllowedMods = Set.of("minecraft");
    public static volatile Set<String> alwaysAllowed = Set.of();
    public static volatile Set<String> manualOnly = Set.of();
    public static volatile boolean smeltingEnabled = true;
    public static volatile boolean stationResults = true;
    public static volatile boolean blockManualInsert = true;
    public static volatile Set<String> insertExemptBlocks = Set.of();
    public static volatile boolean strictUsage = false;
    public static volatile boolean lockBundleMembers = true;
    public static volatile int startingCount = 1;
    public static volatile boolean showSelection = false;
    public static volatile boolean allowSingleMods = true;
    public static volatile Set<String> choosableMods = Set.of();
    public static volatile boolean exclusive = false;
    public static volatile boolean announceChoice = true;
    public static volatile boolean lootEnabled = true;
    public static volatile Set<String> lootTomeBundles = Set.of();
    public static volatile boolean blockModlockCrafting = false;

    public static void bake() {
        Set<String> locked = new HashSet<>(LOCKED_MODS.get());
        lockAll = locked.remove("*");
        lockedMods = Set.copyOf(locked);
        globalAllowedMods = Set.copyOf(GLOBAL_ALLOWED_MODS.get());
        alwaysAllowed = Set.copyOf(ALWAYS_ALLOWED.get());
        manualOnly = Set.copyOf(MANUAL_ONLY.get());
        smeltingEnabled = SMELTING_ENABLED.get();
        stationResults = STATION_RESULTS.get();
        blockManualInsert = BLOCK_MANUAL_INSERT.get();
        insertExemptBlocks = Set.copyOf(INSERT_EXEMPT_BLOCKS.get());
        strictUsage = STRICT_USAGE.get();
        lockBundleMembers = LOCK_BUNDLE_MEMBERS.get();
        startingCount = STARTING_COUNT.get();
        showSelection = SHOW_SELECTION.get();
        allowSingleMods = ALLOW_SINGLE_MODS.get();
        choosableMods = Set.copyOf(CHOOSABLE_MODS.get());
        exclusive = EXCLUSIVE.get();
        announceChoice = ANNOUNCE_CHOICE.get();
        lootEnabled = LOOT_ENABLED.get();
        lootTomeBundles = Set.copyOf(LOOT_TOME_BUNDLES.get());
        blockModlockCrafting = BLOCK_MODLOCK_CRAFTING.get();
    }

    private ModLockConfig() {}
}
