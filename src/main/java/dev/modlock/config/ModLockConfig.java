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
    private static final ModConfigSpec.DoubleValue LOOT_CHANCE;
    private static final ModConfigSpec.IntValue OP_PERMISSION_LEVEL;
    // mod key
    private static final ModConfigSpec.IntValue KEY_PERMISSION_LEVEL;
    private static final ModConfigSpec.BooleanValue KEY_ALLOW_OP_RECONFIGURE;
    private static final ModConfigSpec.BooleanValue KEY_ANNOUNCE_REDEEM;
    // recipe scroll
    private static final ModConfigSpec.IntValue SCROLL_MAX_MINUTES;
    private static final ModConfigSpec.BooleanValue SCROLL_PERMANENT_BY_NON_OP;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SCROLL_BLACKLIST_ITEMS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SCROLL_RESTRICTED_MODS;
    private static final ModConfigSpec.BooleanValue SCROLL_OPS_BYPASS;
    // mundane tome
    private static final ModConfigSpec.IntValue TOME_MINUTES;
    private static final ModConfigSpec.BooleanValue TOME_OP_DURATION;
    private static final ModConfigSpec.EnumValue<RestrictionMode> TOME_RESTRICTION_MODE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> TOME_RESTRICTED_MODS;
    private static final ModConfigSpec.BooleanValue TOME_OPS_BYPASS;
    private static final ModConfigSpec.BooleanValue TOME_SELF_USE;
    private static final ModConfigSpec.ConfigValue<String> TOME_OP_TEXT;
    // pure tome
    private static final ModConfigSpec.IntValue PURE_MINUTES;
    private static final ModConfigSpec.BooleanValue PURE_PERMANENT;
    private static final ModConfigSpec.EnumValue<RestrictionMode> PURE_RESTRICTION_MODE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> PURE_RESTRICTED_MODS;
    private static final ModConfigSpec.BooleanValue PURE_EXCLUSIVITY;
    // machine license
    private static final ModConfigSpec.IntValue LICENSE_MAX_MINUTES;
    private static final ModConfigSpec.BooleanValue LICENSE_PERMANENT_BY_NON_OP;
    // temporary knowledge
    private static final ModConfigSpec.EnumValue<StackMode> TK_STACK_MODE;
    private static final ModConfigSpec.IntValue TK_MAX_MINUTES;
    private static final ModConfigSpec.IntValue TK_WARN_SECONDS;
    private static final ModConfigSpec.EnumValue<MilkMode> MILK_MODE;
    private static final ModConfigSpec.BooleanValue MILK_DEFAULT_CURABLE;
    private static final ModConfigSpec.BooleanValue MILK_UNCONFIGURED_CURABLE;
    private static final ModConfigSpec.BooleanValue MILK_PLAYERS_CHOOSE;

    public enum RestrictionMode { BLACKLIST, WHITELIST }

    public enum StackMode { MAX, ADD }

    public enum MilkMode { PER_ITEM, ALWAYS, NEVER }

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
        LOOT_ENABLED = b.comment("Pure Tomes can be found in loot chests. false = never in loot.")
                .define("enabled", true);
        LOOT_TOME_BUNDLES = b.comment(
                "When not empty, only Pure Tomes bound to one of these bundle ids appear in loot.",
                "Empty = unbound Pure Tomes (the player picks a mod).")
                .defineListAllowEmpty("tome_bundles", List.of(), () -> "", ModLockConfig::isString);
        LOOT_CHANCE = b.comment("Chance (0..1) that a loot chest also contains a Pure Tome.")
                .defineInRange("chance", 0.02, 0.0, 1.0);
        b.pop();

        b.push("items");
        BLOCK_MODLOCK_CRAFTING = b.comment(
                "Nobody (players or automation) can craft any ModLock item. Use it when unlock items",
                "should only come from quests, loot or admins.")
                .define("block_modlock_crafting", false);
        OP_PERMISSION_LEVEL = b.comment(
                "Permission level that counts as \"OP\" when configuring Recipe Scrolls, Mundane Tomes and",
                "Machine Licenses (creative players always count as OP).")
                .defineInRange("op_permission_level", 2, 0, 4);
        b.pop();

        b.push("mod_key");
        KEY_PERMISSION_LEVEL = b.comment(
                "Permission level needed to configure an unconfigured Mod Key (creative players always can).",
                "Other players get a message and nothing happens.")
                .defineInRange("config_permission_level", 2, 0, 4);
        KEY_ALLOW_OP_RECONFIGURE = b.comment("OPs can sneak + use a configured key to reconfigure it.")
                .define("allow_op_reconfigure", true);
        KEY_ANNOUNCE_REDEEM = b.comment("Announce in chat what a player got from a Mod Key.")
                .define("announce_redeem", true);
        b.pop();

        b.push("recipe_scroll");
        SCROLL_MAX_MINUTES = b.comment("Longest duration (minutes) of a temporary Recipe Scroll.")
                .defineInRange("max_temporary_minutes", 120, 1, 540);
        SCROLL_PERMANENT_BY_NON_OP = b.comment(
                "Normal players may create PERMANENT scrolls (only for items they can craft permanently).")
                .define("allow_permanent_by_non_op", true);
        SCROLL_BLACKLIST_ITEMS = b.comment("Items that can never be put on a Recipe Scroll.")
                .defineListAllowEmpty("blacklist_items", List.of("mekanism:digital_miner"), () -> "", ModLockConfig::isString);
        SCROLL_RESTRICTED_MODS = b.comment("Mods whose items can never be put on a Recipe Scroll.")
                .defineListAllowEmpty("restricted_mods", List.of(), () -> "", ModLockConfig::isString);
        SCROLL_OPS_BYPASS = b.comment("Scrolls configured by OPs ignore blacklist_items and restricted_mods.")
                .define("ops_bypass_restrictions", true);
        b.pop();

        b.push("mundane_tome");
        TOME_MINUTES = b.comment("How long (minutes) the knowledge of a Mundane Tome lasts.")
                .defineInRange("duration_minutes", 540, 1, 540);
        TOME_OP_DURATION = b.comment("OPs can choose a shorter duration when configuring a tome.")
                .define("op_can_set_duration", true);
        TOME_RESTRICTION_MODE = b.comment("BLACKLIST: restricted_mods can't be shared. WHITELIST: only restricted_mods can.")
                .defineEnum("restriction_mode", RestrictionMode.BLACKLIST);
        TOME_RESTRICTED_MODS = b.defineListAllowEmpty("restricted_mods", List.of(), () -> "", ModLockConfig::isString);
        TOME_OPS_BYPASS = b.comment("Tomes configured by OPs ignore the restriction.")
                .define("ops_bypass_restrictions", true);
        TOME_SELF_USE = b.comment("The player who wrote a tome can read it himself.")
                .define("allow_self_use", false);
        TOME_OP_TEXT = b.comment("Tooltip of tomes made by OPs (a translation key or plain text).")
                .define("op_tooltip_text", "tooltip.modlock.mundane_tome.admin");
        b.pop();

        b.push("pure_tome");
        PURE_MINUTES = b.comment("How long (minutes) the knowledge of a Pure Tome lasts.")
                .defineInRange("duration_minutes", 540, 1, 540);
        PURE_PERMANENT = b.comment("Pure Tomes unlock the mod (or bundle) permanently instead of giving an effect.")
                .define("permanent", false);
        PURE_RESTRICTION_MODE = b.comment("BLACKLIST: restricted_mods are never offered. WHITELIST: only restricted_mods are.")
                .defineEnum("restriction_mode", RestrictionMode.BLACKLIST);
        PURE_RESTRICTED_MODS = b.defineListAllowEmpty("restricted_mods", List.of(), () -> "", ModLockConfig::isString);
        PURE_EXCLUSIVITY = b.comment("Do not offer mods taken by another player when [starting] exclusive is on.")
                .define("respect_exclusivity", false);
        b.pop();

        b.push("machine_license");
        LICENSE_MAX_MINUTES = b.comment("Longest duration (minutes) of a temporary Machine License.")
                .defineInRange("max_temporary_minutes", 120, 1, 540);
        LICENSE_PERMANENT_BY_NON_OP = b.comment("Normal players may create PERMANENT licenses.")
                .define("allow_permanent_by_non_op", true);
        b.pop();

        b.push("temporary_knowledge");
        TK_STACK_MODE = b.comment(
                "Using a second item for knowledge you already have: MAX keeps the longer time,",
                "ADD sums both (capped by max_minutes). Items that would not add time are not used up.")
                .defineEnum("stack_mode", StackMode.MAX);
        TK_MAX_MINUTES = b.comment("Longest time (minutes) any temporary knowledge can last.")
                .defineInRange("max_minutes", 540, 1, 540);
        TK_WARN_SECONDS = b.comment("Warn the player (action bar) this many seconds before knowledge expires. 0 = never.")
                .defineInRange("warn_before_expire_seconds", 60, 0, 3600);
        b.push("milk");
        MILK_MODE = b.comment(
                "PER_ITEM: each item decides (milk checkbox when configuring). ALWAYS / NEVER: milk always /",
                "never removes ModLock knowledge, whatever the item says.")
                .defineEnum("mode", MilkMode.PER_ITEM);
        MILK_DEFAULT_CURABLE = b.comment("Default state of the milk checkbox.")
                .define("default_curable", false);
        MILK_UNCONFIGURED_CURABLE = b.comment("Milk removes knowledge from sources without a checkbox (Pure Tome, /effect).")
                .define("unconfigured_curable", false);
        MILK_PLAYERS_CHOOSE = b.comment("Normal players see the milk checkbox. false = only OPs; players get default_curable.")
                .define("players_can_choose", true);
        b.pop();
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
    public static volatile double lootChance = 0.02;
    public static volatile int opPermissionLevel = 2;
    public static volatile int keyPermissionLevel = 2;
    public static volatile boolean keyAllowOpReconfigure = true;
    public static volatile boolean keyAnnounceRedeem = true;
    public static volatile int scrollMaxMinutes = 120;
    public static volatile boolean scrollPermanentByNonOp = true;
    public static volatile Set<String> scrollBlacklistItems = Set.of("mekanism:digital_miner");
    public static volatile Set<String> scrollRestrictedMods = Set.of();
    public static volatile boolean scrollOpsBypass = true;
    public static volatile int tomeMinutes = 540;
    public static volatile boolean tomeOpCanSetDuration = true;
    public static volatile RestrictionMode tomeRestrictionMode = RestrictionMode.BLACKLIST;
    public static volatile Set<String> tomeRestrictedMods = Set.of();
    public static volatile boolean tomeOpsBypass = true;
    public static volatile boolean tomeAllowSelfUse = false;
    public static volatile String tomeOpText = "tooltip.modlock.mundane_tome.admin";
    public static volatile int pureMinutes = 540;
    public static volatile boolean purePermanent = false;
    public static volatile RestrictionMode pureRestrictionMode = RestrictionMode.BLACKLIST;
    public static volatile Set<String> pureRestrictedMods = Set.of();
    public static volatile boolean pureRespectExclusivity = false;
    public static volatile int licenseMaxMinutes = 120;
    public static volatile boolean licensePermanentByNonOp = true;
    public static volatile StackMode stackMode = StackMode.MAX;
    public static volatile int maxKnowledgeMinutes = 540;
    public static volatile int warnBeforeExpireSeconds = 60;
    public static volatile MilkMode milkMode = MilkMode.PER_ITEM;
    public static volatile boolean milkDefaultCurable = false;
    public static volatile boolean milkUnconfiguredCurable = false;
    public static volatile boolean milkPlayersChoose = true;

    /** Does the restriction list allow this mod? */
    public static boolean allowedBy(RestrictionMode mode, Set<String> list, String mod) {
        return mode == RestrictionMode.WHITELIST ? list.contains(mod) : !list.contains(mod);
    }

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
        lootChance = LOOT_CHANCE.get();
        opPermissionLevel = OP_PERMISSION_LEVEL.get();
        keyPermissionLevel = KEY_PERMISSION_LEVEL.get();
        keyAllowOpReconfigure = KEY_ALLOW_OP_RECONFIGURE.get();
        keyAnnounceRedeem = KEY_ANNOUNCE_REDEEM.get();
        scrollMaxMinutes = SCROLL_MAX_MINUTES.get();
        scrollPermanentByNonOp = SCROLL_PERMANENT_BY_NON_OP.get();
        scrollBlacklistItems = Set.copyOf(SCROLL_BLACKLIST_ITEMS.get());
        scrollRestrictedMods = Set.copyOf(SCROLL_RESTRICTED_MODS.get());
        scrollOpsBypass = SCROLL_OPS_BYPASS.get();
        tomeMinutes = TOME_MINUTES.get();
        tomeOpCanSetDuration = TOME_OP_DURATION.get();
        tomeRestrictionMode = TOME_RESTRICTION_MODE.get();
        tomeRestrictedMods = Set.copyOf(TOME_RESTRICTED_MODS.get());
        tomeOpsBypass = TOME_OPS_BYPASS.get();
        tomeAllowSelfUse = TOME_SELF_USE.get();
        tomeOpText = TOME_OP_TEXT.get();
        pureMinutes = PURE_MINUTES.get();
        purePermanent = PURE_PERMANENT.get();
        pureRestrictionMode = PURE_RESTRICTION_MODE.get();
        pureRestrictedMods = Set.copyOf(PURE_RESTRICTED_MODS.get());
        pureRespectExclusivity = PURE_EXCLUSIVITY.get();
        licenseMaxMinutes = LICENSE_MAX_MINUTES.get();
        licensePermanentByNonOp = LICENSE_PERMANENT_BY_NON_OP.get();
        stackMode = TK_STACK_MODE.get();
        maxKnowledgeMinutes = TK_MAX_MINUTES.get();
        warnBeforeExpireSeconds = TK_WARN_SECONDS.get();
        milkMode = MILK_MODE.get();
        milkDefaultCurable = MILK_DEFAULT_CURABLE.get();
        milkUnconfiguredCurable = MILK_UNCONFIGURED_CURABLE.get();
        milkPlayersChoose = MILK_PLAYERS_CHOOSE.get();
    }

    private ModLockConfig() {}
}
