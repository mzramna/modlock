package dev.modlock.net;

import dev.modlock.client.ClientState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Thin indirection so common code never loads client classes directly. */
final class ClientHandlers {
    private ClientHandlers() {}

    static void onSync(ModLockNetwork.SyncPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientState.apply(p));
    }

    static void onLockedResult(ModLockNetwork.LockedResultPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientState.setLockedResult(p.containerId(), p.slot(), p.stack()));
    }
}
