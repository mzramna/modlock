package dev.modlock.mixin;

import java.util.UUID;

import javax.annotation.Nullable;

import dev.modlock.attach.ModLockAttachments;
import dev.modlock.config.ModLockConfig;
import dev.modlock.core.AccessRules;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Furnace, blast furnace and smoker: they accept any item, but do not cook when the player
 * who put the input in by hand cannot craft the result. Automation clears the inserter.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {
    @Shadow protected NonNullList<ItemStack> items;

    @Inject(method = "canBurn", at = @At("HEAD"), cancellable = true)
    private static void modlock$checkInserter(RegistryAccess registryAccess, @Nullable RecipeHolder<?> recipe,
                                              NonNullList<ItemStack> inventory, int maxStackSize,
                                              AbstractFurnaceBlockEntity furnace, CallbackInfoReturnable<Boolean> cir) {
        if (!ModLockConfig.smeltingEnabled || recipe == null || !furnace.hasData(ModLockAttachments.FURNACE_INSERTER)) return;
        String inserter = furnace.getData(ModLockAttachments.FURNACE_INSERTER);
        if (inserter.isEmpty() || furnace.getLevel() == null || furnace.getLevel().getServer() == null) return;
        UUID id;
        try {
            id = UUID.fromString(inserter);
        } catch (IllegalArgumentException e) {
            return;
        }
        ItemStack result = recipe.value().getResultItem(registryAccess);
        if (!AccessRules.canCraft(furnace.getLevel().getServer(), id, recipe.id(), result)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Any change of the input item through setItem (hoppers, pipes, menus) forgets the inserter.
     * When a player did it through the menu, the container-click hook sets it again right after.
     */
    @Inject(method = "setItem", at = @At("HEAD"))
    private void modlock$clearInserter(int index, ItemStack stack, CallbackInfo ci) {
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
