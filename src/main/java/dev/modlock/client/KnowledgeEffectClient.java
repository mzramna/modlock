package dev.modlock.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.modlock.ModLock;
import dev.modlock.attach.KnowledgeSources;
import dev.modlock.effect.KnowledgeEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.GatherEffectScreenTooltipsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Knowledge effects draw an item instead of an effect sprite, and get a detailed inventory tooltip. */
@EventBusSubscriber(modid = ModLock.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class KnowledgeEffectClient {
    private static final Map<String, ItemStack> MOD_ICONS = new HashMap<>();

    private KnowledgeEffectClient() {}

    /** Item drawn for an effect: the item (recipe), the block (machine) or the mod's first registered item. */
    static ItemStack iconOf(KnowledgeEffect k) {
        return switch (k.kind()) {
            case RECIPE -> new ItemStack(BuiltInRegistries.ITEM.get(k.target()));
            case MACHINE -> new ItemStack(BuiltInRegistries.BLOCK.get(k.target()).asItem());
            case KNOWLEDGE -> MOD_ICONS.computeIfAbsent(k.mod(), mod -> {
                for (var e : BuiltInRegistries.ITEM.entrySet()) {
                    Item item = e.getValue();
                    if (e.getKey().location().getNamespace().equals(mod) && item != Items.AIR) return new ItemStack(item);
                }
                return new ItemStack(Items.KNOWLEDGE_BOOK);
            });
        };
    }

    private static final IClientMobEffectExtensions EXTENSIONS = new IClientMobEffectExtensions() {
        @Override
        public boolean renderInventoryIcon(MobEffectInstance instance, EffectRenderingInventoryScreen<?> screen,
                                           GuiGraphics g, int x, int y, int blitOffset) {
            if (!(instance.getEffect().value() instanceof KnowledgeEffect k)) return false;
            g.renderItem(iconOf(k), x + 1, y + 8);
            return true;
        }

        @Override
        public boolean renderGuiIcon(MobEffectInstance instance, Gui gui, GuiGraphics g, int x, int y, float z, float alpha) {
            if (!(instance.getEffect().value() instanceof KnowledgeEffect k)) return false;
            g.renderItem(iconOf(k), x + 4, y + 4);
            return true;
        }
    };

    /** Mod bus: registered from {@link KnowledgeEffectClientMod} (older NeoForge 21.1 builds can't mix buses in one subscriber). */
    static void onRegisterExtensions(RegisterClientExtensionsEvent event) {
        List<MobEffect> effects = BuiltInRegistries.MOB_EFFECT.stream().filter(e -> e instanceof KnowledgeEffect).toList();
        if (!effects.isEmpty()) event.registerMobEffect(EXTENSIONS, effects.toArray(MobEffect[]::new));
    }

    @SubscribeEvent
    public static void onEffectTooltip(GatherEffectScreenTooltipsEvent event) {
        MobEffectInstance inst = event.getEffectInstance();
        if (!(inst.getEffect().value() instanceof KnowledgeEffect k)) return;
        var tip = event.getTooltip();
        tip.add(Component.translatable("effect.modlock.description." + k.kind().name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.GRAY));
        KnowledgeSources.Source src = ClientState.knowledgeSources().map()
                .get(inst.getEffect().unwrapKey().map(key -> key.location().toString()).orElse(""));
        if (src != null) {
            tip.add(Component.translatable("effect.modlock.source", src.text()).withStyle(ChatFormatting.DARK_AQUA));
            tip.add(Component.translatable(src.curable() ? "tooltip.modlock.milk.curable" : "tooltip.modlock.milk.immune")
                    .withStyle(src.curable() ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        }
    }

    /**
     * Vanilla only shows effect tooltips when the effect list is compact. In the wide layout we draw
     * the same tooltip ourselves for ModLock effects, so the source / milk / time lines are always visible.
     */
    @SubscribeEvent
    public static void onScreenPost(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen<?> screen)) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int x = screen.getGuiLeft() + screen.getXSize() + 2;
        if (screen.width - x < 120) return; // compact: vanilla already shows the tooltip
        List<MobEffectInstance> list = mc.player.getActiveEffects().stream()
                .filter(net.neoforged.neoforge.client.ClientHooks::shouldRenderEffect).sorted().toList();
        if (list.isEmpty()) return;
        int step = list.size() > 5 ? 132 / (list.size() - 1) : 33;
        int mx = event.getMouseX(), my = event.getMouseY();
        if (mx < x || mx >= x + 120) return;
        int y = screen.getGuiTop();
        MobEffectInstance hovered = null;
        for (MobEffectInstance inst : list) {
            if (my >= y && my < y + Math.min(step, 32)) hovered = inst;
            y += step;
        }
        if (hovered == null || !(hovered.getEffect().value() instanceof KnowledgeEffect)) return;
        float tickRate = mc.level == null ? 20f : mc.level.tickRateManager().tickrate();
        List<Component> lines = List.of(hovered.getEffect().value().getDisplayName(),
                MobEffectUtil.formatDuration(hovered, 1.0f, tickRate));
        lines = net.neoforged.neoforge.client.ClientHooks.getEffectTooltip(screen, hovered, lines);
        event.getGuiGraphics().renderTooltip(mc.font, lines, java.util.Optional.empty(), mx, my);
    }
}
