package dev.modlock;

import dev.modlock.attach.ModLockAttachments;
import dev.modlock.config.ModLockConfig;
import dev.modlock.net.ModLockNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@Mod(ModLock.MODID)
public class ModLock {
    public static final String MODID = "modlock";

    public ModLock(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ModLockConfig.SPEC);
        ModLockAttachments.register(modBus);
        modBus.addListener(ModLockNetwork::register);
        modBus.addListener(ModLock::onConfigLoad);
        modBus.addListener(ModLock::onConfigReload);
    }

    private static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ModLockConfig.SPEC) ModLockConfig.bake();
    }

    private static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != ModLockConfig.SPEC) return;
        ModLockConfig.bake();
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> ModLockNetwork.syncAll(server));
    }
}
