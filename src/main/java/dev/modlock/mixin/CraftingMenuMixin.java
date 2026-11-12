package dev.modlock.mixin;

import javax.annotation.Nullable;

import dev.modlock.core.AccessRules;
import dev.modlock.net.ModLockNetwork;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crafting table and the 2x2 inventory grid (InventoryMenu calls the same static method).
 * After vanilla computes the result, it is removed again when the player cannot craft it,
 * and the client receives a preview to draw it locked.
 * Only method arguments are used (no local variables), so it works on non-recompiled game jars.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
    private static void modlock$filterResult(AbstractContainerMenu menu, Level level, Player player,
                                             CraftingContainer craftSlots, ResultContainer resultSlots,
                                             @Nullable RecipeHolder<CraftingRecipe> recipe, CallbackInfo ci) {
        if (level.isClientSide()) return;
        ItemStack result = resultSlots.getItem(0);
        RecipeHolder<?> used = resultSlots.getRecipeUsed();
        boolean allowed = AccessRules.canCraft(player, used != null ? used.id() : null, result);
        boolean realPlayer = player instanceof ServerPlayer && !(player instanceof FakePlayer);

        if (!allowed) {
            resultSlots.setItem(0, ItemStack.EMPTY);
            menu.setRemoteSlot(0, ItemStack.EMPTY);
            if (realPlayer) {
                ((ServerPlayer) player).connection.send(new ClientboundContainerSetSlotPacket(
                        menu.containerId, menu.incrementStateId(), 0, ItemStack.EMPTY));
            }
        }
        if (realPlayer) {
            ModLockNetwork.sendLockedResult((ServerPlayer) player, menu.containerId, 0,
                    allowed ? ItemStack.EMPTY : result.copy());
        }
    }
}
