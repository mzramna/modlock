package dev.modlock.mixin;

import java.util.Set;

import dev.modlock.attach.ModLockAttachments;
import dev.modlock.config.ModLockConfig;
import dev.modlock.core.MachineTracker;
import dev.modlock.core.RecipeIndex;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks every container click on the server:
 *  - furnaces: remember which player put the input item by hand;
 *  - machines of other mods: refuse manual insertion of items whose every recipe is locked.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Shadow @Final private Set<Slot> quickcraftSlots;

    @Shadow public abstract ItemStack getCarried();

    @Shadow public abstract Slot getSlot(int slotId);

    @Shadow public abstract void sendAllDataToRemote();

    @Unique private ItemStack modlock$furnaceBefore = null;

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void modlock$beforeClick(int slotId, int button, ContainerInput clickType, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer sp) || player instanceof FakePlayer) return;
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;

        if (self instanceof AbstractFurnaceMenu) {
            Container c = ((AbstractFurnaceMenuAccessor) self).modlock$getContainer();
            modlock$furnaceBefore = c instanceof AbstractFurnaceBlockEntity ? c.getItem(0).copy() : null;
            return;
        }

        if (!ModLockConfig.blockManualInsert) return;
        Block machine = MachineTracker.machineOf(self);
        if (machine == null) return;

        ItemStack candidate = modlock$insertCandidate(slotId, button, clickType, sp);
        if (candidate == null || candidate.isEmpty()) return;
        if (RecipeIndex.manualInsertBlocked(sp, machine, candidate)) {
            ci.cancel();
            sendAllDataToRemote();
            sp.sendOverlayMessage(Component.translatable("message.modlock.insert_blocked", candidate.getHoverName()));
        }
    }

    @Unique
    private ItemStack modlock$insertCandidate(int slotId, int button, ContainerInput type, ServerPlayer sp) {
        Inventory inv = sp.getInventory();
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        boolean validSlot = slotId >= 0 && slotId < self.slots.size();
        switch (type) {
            case PICKUP: {
                if (!validSlot) return null;
                Slot s = getSlot(slotId);
                return s.container != inv ? getCarried() : null;
            }
            case QUICK_MOVE: {
                if (!validSlot) return null;
                Slot s = getSlot(slotId);
                return s.container == inv ? s.getItem() : null;
            }
            case SWAP: {
                if (!validSlot) return null;
                Slot s = getSlot(slotId);
                if (s.container == inv) return null;
                return button == Inventory.SLOT_OFFHAND || (button >= 0 && button < 9) ? inv.getItem(button) : null;
            }
            case QUICK_CRAFT: {
                if (AbstractContainerMenu.getQuickcraftHeader(button) != 2) return null;
                for (Slot s : quickcraftSlots) {
                    if (s.container != inv) return getCarried();
                }
                return null;
            }
            default:
                return null;
        }
    }

    @Inject(method = "clicked", at = @At("TAIL"))
    private void modlock$afterClick(int slotId, int button, ContainerInput clickType, Player player, CallbackInfo ci) {
        ItemStack before = modlock$furnaceBefore;
        modlock$furnaceBefore = null;
        if (before == null || !(player instanceof ServerPlayer) || player instanceof FakePlayer) return;
        Container c = ((AbstractFurnaceMenuAccessor) this).modlock$getContainer();
        if (!(c instanceof AbstractFurnaceBlockEntity furnace)) return;
        ItemStack after = furnace.getItem(0);
        if (after.isEmpty()) return;
        boolean changedItem = !ItemStack.isSameItemSameComponents(before, after);
        if (changedItem || after.getCount() > before.getCount()) {
            furnace.setData(ModLockAttachments.FURNACE_INSERTER, player.getUUID().toString());
            furnace.setChanged();
        }
    }
}
