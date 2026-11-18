package dev.modlock.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * config/modlock-startup.toml. STARTUP configs are read as soon as they are registered (in the mod
 * constructor), before any registry event, so they can decide which effects get registered.
 * Must be identical on the server and on every client (the effect registry is synced).
 */
public final class ModLockStartupConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RECIPE_EFFECT_MODS;
    public static final ModConfigSpec.BooleanValue INCLUDE_MINECRAFT;

    /** System property used by the GameTest run to test with vanilla content as a stand-in mod. */
    public static final String INCLUDE_MINECRAFT_PROPERTY = "modlock.includeMinecraftEffects";

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("effects");
        RECIPE_EFFECT_MODS = b.comment(
                        "Mods that get one effect per item (modlock:recipe/<mod>/<item>, used by temporary Recipe Scrolls)",
                        "and one per block with a block entity (modlock:machine/<mod>/<block>, temporary Machine Licenses).",
                        "\"*\" = every mod except minecraft/neoforge/modlock. Every mod always gets modlock:knowledge/<mod>.",
                        "Keep this file identical on the server and on clients.")
                .defineListAllowEmpty("recipe_effect_mods", List.of("*"), () -> "", o -> o instanceof String);
        INCLUDE_MINECRAFT = b.comment(
                        "Also create knowledge/recipe/machine effects for 'minecraft' (only useful if you lock minecraft).")
                .define("include_minecraft", false);
        b.pop();
        SPEC = b.build();
    }

    public static boolean includeMinecraft() {
        return Boolean.getBoolean(INCLUDE_MINECRAFT_PROPERTY) || INCLUDE_MINECRAFT.get();
    }

    private ModLockStartupConfig() {}
}
