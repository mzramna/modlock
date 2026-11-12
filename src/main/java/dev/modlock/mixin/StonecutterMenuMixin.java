package dev.modlock.mixin;

import dev.modlock.config.ModLockConfig;
import dev.modlock.core.AccessRules;
import dev.modlock.net.ModLockNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StonecutterMenu.class)
public abstract class StonecutterMenuMixin {
    @Shadow @Final Slot resultSlot;
    @Shadow @Final private ResultContainer resultContainer;

    @Unique
    private Player modlock$player;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("TAIL"))
    private void modlock$capturePlayer(int id, Inventory inv, ContainerLevelAccess access, CallbackInfo ci) {
        this.modlock$player = inv.player;
    }

    @Inject(method = "setupResultSlot", at = @At("TAIL"))
    private void modlock$filterResult(int index, CallbackInfo ci) {
        if (!ModLockConfig.stationResults || !(modlock$player instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        ItemStack result = resultSlot.getItem();
        int containerId = ((StonecutterMenu) (Object) this).containerId;
        if (result.isEmpty()) {
            ModLockNetwork.sendLockedResult(sp, containerId, 1, ItemStack.EMPTY);
            return;
        }
        // vanilla stores the selected recipe in the result container since 1.21.2
        if (AccessRules.canCraft(sp, AccessRules.recipeId(resultContainer.getRecipeUsed()), result)) {
            ModLockNetwork.sendLockedResult(sp, containerId, 1, ItemStack.EMPTY);
        } else {
            resultSlot.set(ItemStack.EMPTY);
            ((StonecutterMenu) (Object) this).broadcastChanges();
            ModLockNetwork.sendLockedResult(sp, containerId, 1, result.copy());
        }
    }
}
