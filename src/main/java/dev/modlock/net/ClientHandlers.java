package dev.modlock.net;

import dev.modlock.client.ClientState;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Thin indirection so common code never loads client classes directly. */
final class ClientHandlers {
    private ClientHandlers() {}

    static void onSync(ModLockNetwork.SyncPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientState.apply(p));
    }

    static void onKnowledgeSources(ModLockNetwork.KnowledgeSourcesPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientState.setKnowledgeSources(p.sources()));
    }

    static void onLockedResult(ModLockNetwork.LockedResultPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientState.setLockedResult(p.containerId(), p.slot(), p.stack()));
    }

    static void onOpenChoice(ModLockNetwork.OpenChoicePayload p, IPayloadContext ctx) {
        if (!FMLEnvironment.getDist().isClient()) return;
        ctx.enqueueWork(() -> dev.modlock.client.ChoiceScreen.open(p.purpose(), p.count(), p.options()));
    }

    static void onOpenConfig(ModLockNetwork.OpenConfigPayload p, IPayloadContext ctx) {
        if (!FMLEnvironment.getDist().isClient()) return;
        ctx.enqueueWork(() -> dev.modlock.client.ConfigScreen.open(p));
    }
}
