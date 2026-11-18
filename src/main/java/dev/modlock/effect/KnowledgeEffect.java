package dev.modlock.effect;

import java.util.Set;

import javax.annotation.Nullable;

import dev.modlock.config.ModLockConfig;
import dev.modlock.core.ModNames;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;
import net.neoforged.neoforge.common.EffectCures;

/**
 * Temporary knowledge: of a whole mod (KNOWLEDGE), of one item's recipes (RECIPE) or of one machine (MACHINE).
 * The effect does nothing by itself: {@link dev.modlock.core.TemporaryKnowledge} checks whether it is active.
 */
public class KnowledgeEffect extends MobEffect {
    public enum Kind { KNOWLEDGE, RECIPE, MACHINE }

    private final Kind kind;
    private final String mod;
    @Nullable
    private final ResourceLocation target;

    public KnowledgeEffect(Kind kind, String mod, @Nullable ResourceLocation target) {
        super(MobEffectCategory.BENEFICIAL, colorOf(mod));
        this.kind = kind;
        this.mod = mod;
        this.target = target;
    }

    /** Stable color per mod id. */
    static int colorOf(String mod) {
        int h = mod.hashCode() * 0x9E3779B1;
        int r = 80 + ((h >>> 16) & 0x7F);
        int g = 80 + ((h >>> 8) & 0x7F);
        int b = 80 + (h & 0x7F);
        return (r << 16) | (g << 8) | b;
    }

    public Kind kind() {
        return kind;
    }

    public String mod() {
        return mod;
    }

    /** Item (RECIPE) or block (MACHINE) id; null for KNOWLEDGE. */
    @Nullable
    public ResourceLocation target() {
        return target;
    }

    @Override
    public Component getDisplayName() {
        return switch (kind) {
            case KNOWLEDGE -> Component.translatable("effect.modlock.knowledge", ModNames.component(mod));
            case RECIPE -> Component.translatable("effect.modlock.recipe",
                    BuiltInRegistries.ITEM.get(target).getDescription());
            case MACHINE -> Component.translatable("effect.modlock.machine",
                    BuiltInRegistries.BLOCK.get(target).getName());
        };
    }

    /** Only milk, and only when the configuration says so; items set the cures of each instance explicitly. */
    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        if (ModLockConfig.milkMode == ModLockConfig.MilkMode.ALWAYS
                || (ModLockConfig.milkMode == ModLockConfig.MilkMode.PER_ITEM && ModLockConfig.milkUnconfiguredCurable)) {
            cures.add(EffectCures.MILK);
        }
    }
}
