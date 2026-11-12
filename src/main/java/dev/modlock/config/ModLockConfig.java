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
    }

    private ModLockConfig() {}
}
