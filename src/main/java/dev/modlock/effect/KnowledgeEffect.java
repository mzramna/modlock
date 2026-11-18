package dev.modlock.effect;

import javax.annotation.Nullable;

import dev.modlock.core.ModNames;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Temporary knowledge: of a whole mod (KNOWLEDGE), of one item's recipes (RECIPE) or of one machine (MACHINE).
 * The effect does nothing by itself: {@link dev.modlock.core.TemporaryKnowledge} checks whether it is active.
 */
public class KnowledgeEffect extends MobEffect {
    public enum Kind { KNOWLEDGE, RECIPE, MACHINE }

    private final Kind kind;
    private final String mod;
    @Nullable
    private final Identifier target;

    public KnowledgeEffect(Kind kind, String mod, @Nullable Identifier target) {
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
    public Identifier target() {
        return target;
    }

    /** 1.21.1 Item#getDescription: the default item name (26.1 keeps it in the ITEM_NAME component). */
    static Component itemName(net.minecraft.world.item.Item item) {
        return item.getName(item.getDefaultInstance());
    }

    @Override
    public Component getDisplayName() {
        return switch (kind) {
            case KNOWLEDGE -> Component.translatable("effect.modlock.knowledge", ModNames.component(mod));
            case RECIPE -> Component.translatable("effect.modlock.recipe",
                    itemName(BuiltInRegistries.ITEM.getValue(target)));
            case MACHINE -> Component.translatable("effect.modlock.machine",
                    BuiltInRegistries.BLOCK.getValue(target).getName());
        };
    }
}
