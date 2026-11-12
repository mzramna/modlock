package dev.modlock.mixin;

import java.util.Optional;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.modlock.core.AccessRules;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The vanilla Crafter never crafts recipes marked manual_only. Everything else stays free. */
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {
    @WrapOperation(method = "dispenseFrom", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/CrafterBlock;getPotentialResults(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/CraftingInput;)Ljava/util/Optional;"))
    private Optional<RecipeHolder<CraftingRecipe>> modlock$manualOnly(Level level, CraftingInput input,
                                                                    Operation<Optional<RecipeHolder<CraftingRecipe>>> original) {
        Optional<RecipeHolder<CraftingRecipe>> result = original.call(level, input);
        if (result.isPresent()) {
            RecipeHolder<CraftingRecipe> holder = result.get();
            if (AccessRules.isManualOnly(holder.id(), holder.value().getResultItem(level.registryAccess()))) {
                return Optional.empty();
            }
        }
        return result;
    }
}
