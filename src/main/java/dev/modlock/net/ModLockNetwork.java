package dev.modlock.net;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.modlock.ModLock;
import dev.modlock.core.AccessRules;
import dev.modlock.core.ModBundles;
import dev.modlock.core.ModLockSavedData;
import dev.modlock.core.StartingChoice;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforgespi.language.IModInfo;

public final class ModLockNetwork {
    private ModLockNetwork() {}

    private static final StreamCodec<ByteBuf, List<String>> STRINGS =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    /**
     * Everything the client needs to show lock tooltips, already resolved by the server
     * (config, bundles, classes): locked = mods locked for players without access,
     * unlockedMods = mods this player can use, bundleHints = "mod=Bundle name" for tooltips.
     */
    public record SyncPayload(List<String> lockedMods, List<String> unlockedMods, List<String> unlockedItems,
                              List<String> bundleHints) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(ModLock.MODID, "sync"));
        public static final StreamCodec<ByteBuf, SyncPayload> CODEC = StreamCodec.composite(
                STRINGS, SyncPayload::lockedMods,
                STRINGS, SyncPayload::unlockedMods,
                STRINGS, SyncPayload::unlockedItems,
                STRINGS, SyncPayload::bundleHints,
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

    private static final StreamCodec<FriendlyByteBuf, StartingChoice.Option> OPTION = StreamCodec.of(
            (buf, o) -> {
                buf.writeUtf(o.key());
                buf.writeUtf(o.name());
                buf.writeUtf(o.description());
                buf.writeUtf(o.icon());
                buf.writeUtf(o.image());
                buf.writeByteArray(o.imageBytes());
                buf.writeCollection(o.members(), FriendlyByteBuf::writeUtf);
                buf.writeUtf(o.takenBy());
            },
            buf -> new StartingChoice.Option(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readUtf(), buf.readByteArray(), buf.readList(FriendlyByteBuf::readUtf), buf.readUtf()));

    /** Server -> client: open the selection screen. */
    public record OpenChoicePayload(int count, List<StartingChoice.Option> options) implements CustomPacketPayload {
        public static final Type<OpenChoicePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(ModLock.MODID, "open_choice"));
        public static final StreamCodec<FriendlyByteBuf, OpenChoicePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpenChoicePayload::count,
                OPTION.apply(ByteBufCodecs.list()), OpenChoicePayload::options,
                OpenChoicePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: the keys the player picked. */
    public record ChoiceAnswerPayload(List<String> keys) implements CustomPacketPayload {
        public static final Type<ChoiceAnswerPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(ModLock.MODID, "choice_answer"));
        public static final StreamCodec<ByteBuf, ChoiceAnswerPayload> CODEC =
                STRINGS.map(ChoiceAnswerPayload::new, ChoiceAnswerPayload::keys);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("2");
        r.playToClient(SyncPayload.TYPE, SyncPayload.CODEC, ClientHandlers::onSync);
        r.playToClient(LockedResultPayload.TYPE, LockedResultPayload.CODEC, ClientHandlers::onLockedResult);
        r.playToClient(OpenChoicePayload.TYPE, OpenChoicePayload.CODEC, ClientHandlers::onOpenChoice);
        r.playToServer(ChoiceAnswerPayload.TYPE, ChoiceAnswerPayload.CODEC, ModLockNetwork::onAnswer);
    }

    private static void onAnswer(ChoiceAnswerPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) StartingChoice.answer(sp, p.keys());
        });
    }

    public static void sync(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        List<String> locked = new ArrayList<>();
        List<String> unlocked = new ArrayList<>();
        Set<String> hints = new HashSet<>();
        for (IModInfo info : ModList.get().getMods()) {
            String ns = info.getModId();
            if (!AccessRules.isModLocked(ns) && !AccessRules.isClosedDenied(server, player.getUUID(), ns)) continue;
            locked.add(ns);
            if (AccessRules.hasModAccess(server, player.getUUID(), ns)) unlocked.add(ns);
            for (ModBundles.Bundle b : ModBundles.bundlesOf(ns)) hints.add(ns + "=" + b.name());
        }
        ModLockSavedData.PlayerData d = ModLockSavedData.get(server).peek(player.getUUID());
        List<String> items = d == null ? List.of() : d.items.stream().map(ResourceLocation::toString).toList();
        send(player, new SyncPayload(locked, unlocked, items, new ArrayList<>(hints)));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p);
    }

    public static void sendLockedResult(ServerPlayer player, int containerId, int slot, ItemStack preview) {
        send(player, new LockedResultPayload(containerId, slot, preview));
    }

    public static void openChoice(ServerPlayer player, int count, List<StartingChoice.Option> options) {
        send(player, new OpenChoicePayload(count, options));
    }

    /** Skips players whose connection does not know the channel (vanilla clients, test mock players). */
    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
