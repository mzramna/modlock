package dev.modlock.mixin;

import java.util.UUID;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.modlock.attach.ModLockAttachments;
import dev.modlock.config.ModLockConfig;
import dev.modlock.core.AccessRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Furnace, blast furnace and smoker: they accept any item, but do not cook when the player
 * who put the input in by hand cannot craft the result. Automation clears the inserter.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {
    @Shadow protected NonNullList<ItemStack> items;

    @Shadow @Final private RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> quickCheck;

    /**
     * Since 1.21.2 canBurn only receives (items, maxStackSize, result), so the call is wrapped inside
     * serverTick and the furnace is taken from serverTick's own arguments (captured after the
     * Operation parameter). No local variables are used, so it works on non-recompiled game jars.
     */
    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;canBurn(Lnet/minecraft/core/NonNullList;ILnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean modlock$checkInserter(NonNullList<ItemStack> inventory, int maxStackSize, ItemStack burnResult,
                                                 Operation<Boolean> original,
                                                 ServerLevel level, BlockPos pos, BlockState state,
                                                 AbstractFurnaceBlockEntity furnace) {
        if (!original.call(inventory, maxStackSize, burnResult)) return false;
        if (!ModLockConfig.smeltingEnabled || !furnace.hasData(ModLockAttachments.FURNACE_INSERTER)) return true;
        String inserter = furnace.getData(ModLockAttachments.FURNACE_INSERTER);
        if (inserter.isEmpty()) return true;
        UUID id;
        try {
            id = UUID.fromString(inserter);
        } catch (IllegalArgumentException e) {
            return true;
        }
        // the cached check returns the recipe serverTick just used (always_allowed may list recipe ids)
        RecipeHolder<? extends AbstractCookingRecipe> recipe = ((AbstractFurnaceBlockEntityMixin) (Object) furnace).quickCheck
                .getRecipeFor(new SingleRecipeInput(inventory.get(0)), level).orElse(null);
        return AccessRules.canCraft(level.getServer(), id, AccessRules.recipeId(recipe), burnResult);
    }

    /**
     * Any change of the input item through setItem (hoppers, pipes, menus) forgets the inserter.
     * When a player did it through the menu, the container-click hook sets it again right after.
     * NeoForge routes setItem(int, ItemStack) and its transfer API through this overload.
     */
    @Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;Z)V", at = @At("HEAD"))
    private void modlock$clearInserter(int index, ItemStack stack, boolean insideTransaction, CallbackInfo ci) {
        if (index != 0) return;
        AbstractFurnaceBlockEntity self = (AbstractFurnaceBlockEntity) (Object) this;
        ItemStack current = this.items.get(0);
        boolean same = !stack.isEmpty() && ItemStack.isSameItemSameComponents(current, stack);
        if (!same && self.hasData(ModLockAttachments.FURNACE_INSERTER)) {
            self.removeData(ModLockAttachments.FURNACE_INSERTER);
            self.setChanged();
        }
    }
}
