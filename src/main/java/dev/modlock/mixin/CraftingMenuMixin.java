package dev.modlock.mixin;

import java.util.Optional;

import com.llamalad7.mixinextras.sugar.Local;

import dev.modlock.core.AccessRules;
import dev.modlock.net.ModLockNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Crafting table and the 2x2 inventory grid (InventoryMenu calls the same static method).
 * When the player cannot craft the result, the result slot stays empty and the client
 * receives a preview to draw it locked.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @ModifyVariable(
            method = "slotChangedCraftingGrid",
            at = @At(value = "LOAD", ordinal = 0),
            ordinal = 0)
    private static ItemStack modlock$filterResult(
            ItemStack result,
            @Local(argsOnly = true) AbstractContainerMenu menu,
            @Local(argsOnly = true) Player player,
            @Local Optional<RecipeHolder<CraftingRecipe>> recipe) {
        var recipeId = recipe.map(RecipeHolder::id).orElse(null);
        boolean allowed = AccessRules.canCraft(player, recipeId, result);
        if (player instanceof ServerPlayer sp && !(player instanceof FakePlayer)) {
            ModLockNetwork.sendLockedResult(sp, menu.containerId, 0, allowed ? ItemStack.EMPTY : result.copy());
        }
        return allowed ? result : ItemStack.EMPTY;
    }
}
