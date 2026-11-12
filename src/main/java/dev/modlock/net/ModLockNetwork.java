package dev.modlock.net;

import java.util.ArrayList;
import java.util.List;

import dev.modlock.ModLock;
import dev.modlock.config.ModLockConfig;
import dev.modlock.core.ModLockSavedData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModLockNetwork {
    private ModLockNetwork() {}

    private static final StreamCodec<ByteBuf, List<String>> STRINGS =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    /** Everything the client needs to show lock tooltips. */
    public record SyncPayload(boolean lockAll, List<String> lockedMods, List<String> globalAllowed,
                              List<String> unlockedMods, List<String> unlockedItems) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(ModLock.MODID, "sync"));
        public static final StreamCodec<ByteBuf, SyncPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SyncPayload::lockAll,
                STRINGS, SyncPayload::lockedMods,
                STRINGS, SyncPayload::globalAllowed,
                STRINGS, SyncPayload::unlockedMods,
                STRINGS, SyncPayload::unlockedItems,
                SyncPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Preview of a result that was hidden because the player lacks access (empty stack clears it). */
    public record LockedResultPayload(int containerId, int slot, ItemStack stack) implements CustomPacketPayload {
        public static final Type<LockedResultPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(ModLock.MODID, "locked_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LockedResultPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LockedResultPayload::containerId,
                ByteBufCodecs.VAR_INT, LockedResultPayload::slot,
                ItemStack.OPTIONAL_STREAM_CODEC, LockedResultPayload::stack,
                LockedResultPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(SyncPayload.TYPE, SyncPayload.CODEC, ClientHandlers::onSync);
        r.playToClient(LockedResultPayload.TYPE, LockedResultPayload.CODEC, ClientHandlers::onLockedResult);
    }

    public static void sync(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ModLockSavedData.PlayerData d = ModLockSavedData.get(server).peek(player.getUUID());
        List<String> mods = d == null ? List.of() : new ArrayList<>(d.mods);
        List<String> items = d == null ? List.of() : d.items.stream().map(ResourceLocation::toString).toList();
        send(player, new SyncPayload(
                ModLockConfig.lockAll,
                new ArrayList<>(ModLockConfig.lockedMods),
                new ArrayList<>(ModLockConfig.globalAllowedMods),
                mods, items));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p);
    }

    public static void sendLockedResult(ServerPlayer player, int containerId, int slot, ItemStack preview) {
        send(player, new LockedResultPayload(containerId, slot, preview));
    }

    /** Skips players whose connection does not know the channel (vanilla clients, test mock players). */
    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
