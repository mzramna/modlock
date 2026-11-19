package dev.modlock.client;

import dev.modlock.ModLock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Client mod-bus events. Kept apart from the game-bus subscribers so every NeoForge 21.1.x accepts them. */
@EventBusSubscriber(modid = ModLock.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class KnowledgeEffectClientMod {
    private KnowledgeEffectClientMod() {}

    @SubscribeEvent
    public static void onRegisterExtensions(RegisterClientExtensionsEvent event) {
        KnowledgeEffectClient.onRegisterExtensions(event);
    }
}
