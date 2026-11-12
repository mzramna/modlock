package dev.modlock.mixin;

import javax.annotation.Nullable;

import dev.modlock.config.ModLockConfig;
import dev.modlock.core.AccessRules;
import dev.modlock.net.ModLockNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin extends ItemCombinerMenu {
    @Shadow
    @Nullable
    private RecipeHolder<SmithingRecipe> selectedRecipe;

    private SmithingMenuMixin(@Nullable MenuType<?> type, int id, Inventory inv, ContainerLevelAccess access) {
        super(type, id, inv, access);
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void modlock$filterResult(CallbackInfo ci) {
        if (!ModLockConfig.stationResults || !(this.player instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        ItemStack result = this.resultSlots.getItem(0);
        if (result.isEmpty()) {
            ModLockNetwork.sendLockedResult(sp, this.containerId, 3, ItemStack.EMPTY);
            return;
        }
        var id = this.selectedRecipe != null ? this.selectedRecipe.id() : null;
        if (AccessRules.canCraft(sp, id, result)) {
            ModLockNetwork.sendLockedResult(sp, this.containerId, 3, ItemStack.EMPTY);
        } else {
            this.resultSlots.setItem(0, ItemStack.EMPTY);
            ModLockNetwork.sendLockedResult(sp, this.containerId, 3, result.copy());
        }
    }
}
