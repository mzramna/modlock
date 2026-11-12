package dev.modlock.client;

import java.util.ArrayList;
import java.util.List;

import dev.modlock.ModLock;
import dev.modlock.core.ModNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = ModLock.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (event.getEntity() == null) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !ClientState.isLockedForMe(stack)) return;
        String ns = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        event.getToolTip().add(Component.translatable("tooltip.modlock.locked", ModNames.component(ns))
                .withStyle(ChatFormatting.RED));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.reset();
    }

    private static Slot lockedSlot(AbstractContainerScreen<?> screen) {
        if (ClientState.lockedPreview().isEmpty()) return null;
        var menu = screen.getMenu();
        if (menu.containerId != ClientState.lockedContainer()) return null;
        int idx = ClientState.lockedSlot();
        if (idx < 0 || idx >= menu.slots.size()) return null;
        Slot slot = menu.slots.get(idx);
        return slot.hasItem() ? null : slot;
    }

    /** Ghost of the hidden result with a red overlay, so players know why nothing came out. */
    @SubscribeEvent
    public static void onForeground(ContainerScreenEvent.Render.Foreground event) {
        Slot slot = lockedSlot(event.getContainerScreen());
        if (slot == null) return;
        GuiGraphics g = event.getGuiGraphics();
        g.renderFakeItem(ClientState.lockedPreview(), slot.x, slot.y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x99B02020);
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onScreenPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        Slot slot = lockedSlot(screen);
        if (slot == null) return;
        int x = screen.getGuiLeft() + slot.x;
        int y = screen.getGuiTop() + slot.y;
        int mx = event.getMouseX();
        int my = event.getMouseY();
        if (mx < x - 1 || mx >= x + 17 || my < y - 1 || my >= y + 17) return;
        if (!screen.getMenu().getCarried().isEmpty()) return;
        ItemStack preview = ClientState.lockedPreview();
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>(preview.getTooltipLines(
                net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL));
        lines.add(Component.translatable("tooltip.modlock.result_locked").withStyle(ChatFormatting.RED));
        event.getGuiGraphics().renderComponentTooltip(mc.font, lines, mx, my);
    }
}
