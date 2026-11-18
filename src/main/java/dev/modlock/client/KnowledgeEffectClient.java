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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
@EventBusSubscriber(modid = ModLock.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class KnowledgeEffectClient {
    private static final Map<String, ItemStack> MOD_ICONS = new HashMap<>();

    private KnowledgeEffectClient() {}

    /** Item drawn for an effect: the item (recipe), the block (machine) or the mod's first registered item. */
    static ItemStack iconOf(KnowledgeEffect k) {
        return switch (k.kind()) {
            case RECIPE -> new ItemStack(BuiltInRegistries.ITEM.getValue(k.target()));
            case MACHINE -> new ItemStack(BuiltInRegistries.BLOCK.getValue(k.target()).asItem());
            case KNOWLEDGE -> MOD_ICONS.computeIfAbsent(k.mod(), mod -> {
                for (var e : BuiltInRegistries.ITEM.entrySet()) {
                    Item item = e.getValue();
                    if (e.getKey().identifier().getNamespace().equals(mod) && item != Items.AIR) return new ItemStack(item);
                }
                return new ItemStack(Items.KNOWLEDGE_BOOK);
            });
        };
    }

    /**
     * 26.1 passes the 18x18 sprite area (x, y, width, height) to the extract* methods; the 16x16 item is
     * centered in it, as on 1.21.1.
     */
    private static final IClientMobEffectExtensions EXTENSIONS = new IClientMobEffectExtensions() {
        @Override
        public boolean extractInventoryIcon(MobEffectInstance instance, AbstractContainerScreen<?> screen,
                                            GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
            if (!(instance.getEffect().value() instanceof KnowledgeEffect k)) return false;
            g.item(iconOf(k), x + 1, y + 1);
            return true;
        }

        @Override
        public boolean extractGuiIcon(MobEffectInstance instance, Gui gui, GuiGraphicsExtractor g,
                                      int x, int y, int width, int height, int color) {
            if (!(instance.getEffect().value() instanceof KnowledgeEffect k)) return false;
            g.item(iconOf(k), x + 1, y + 1);
            return true;
        }
    };

    /** Mod bus (the bus is inferred from the event type). */
    @SubscribeEvent
    public static void onRegisterExtensions(RegisterClientExtensionsEvent event) {
        List<MobEffect> effects = BuiltInRegistries.MOB_EFFECT.stream().filter(e -> e instanceof KnowledgeEffect).toList();
        if (!effects.isEmpty()) event.registerMobEffect(EXTENSIONS, effects.toArray(MobEffect[]::new));
    }

    @SubscribeEvent
    public static void onEffectTooltip(GatherEffectScreenTooltipsEvent event) {
        MobEffectInstance inst = event.getEffectInstance();
        if (!(inst.getEffect().value() instanceof KnowledgeEffect k)) return;
        var tip = event.getTooltip();
        if (tip.isEmpty()) {
            // wide effect layout: 26.1 (NeoForge) fires this event with an empty list and shows the tooltip only
            // if a mod fills it, so the name and time come first like in the compact layout
            Minecraft mc = Minecraft.getInstance();
            float tickRate = mc.level == null ? 20f : mc.level.tickRateManager().tickrate();
            tip.add(k.getDisplayName());
            tip.add(MobEffectUtil.formatDuration(inst, 1.0f, tickRate));
        }
        tip.add(Component.translatable("effect.modlock.description." + k.kind().name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.GRAY));
        KnowledgeSources.Source src = ClientState.knowledgeSources().map()
                .get(inst.getEffect().unwrapKey().map(key -> key.identifier().toString()).orElse(""));
        if (src != null) {
            tip.add(Component.translatable("effect.modlock.source", src.text()).withStyle(ChatFormatting.DARK_AQUA));
            tip.add(Component.translatable(src.curable() ? "tooltip.modlock.milk.curable" : "tooltip.modlock.milk.immune")
                    .withStyle(src.curable() ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        }
    }
}
