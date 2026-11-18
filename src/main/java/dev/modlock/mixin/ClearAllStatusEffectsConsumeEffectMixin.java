package dev.modlock.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.modlock.core.TemporaryKnowledge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge 26.1 dropped effect cures: milk and the totem of undying both clear effects through this consume
 * effect. The item is remembered while removeAllEffects runs so TemporaryKnowledge can keep the 1.21.1
 * behavior (milk only removes curable knowledge, totems never remove it). Only method arguments are used.
 */
@Mixin(ClearAllStatusEffectsConsumeEffect.class)
public abstract class ClearAllStatusEffectsConsumeEffectMixin {
    @WrapOperation(method = "apply", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;removeAllEffects()Z"))
    private boolean modlock$rememberItem(LivingEntity entity, Operation<Boolean> original,
                                         Level level, ItemStack stack, LivingEntity user) {
        TemporaryKnowledge.beginClearAll(stack);
        try {
            return original.call(entity);
        } finally {
            TemporaryKnowledge.endClearAll();
        }
    }
}
