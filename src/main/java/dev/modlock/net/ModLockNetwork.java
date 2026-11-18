package dev.modlock.net;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.modlock.ModLock;
import dev.modlock.attach.KnowledgeSources;
import dev.modlock.attach.ModLockAttachments;
import dev.modlock.core.AccessRules;
import dev.modlock.core.ModBundles;
import dev.modlock.core.ModLockSavedData;
import dev.modlock.core.StartingChoice;
import dev.modlock.core.TemporaryKnowledge;
import dev.modlock.effect.KnowledgeEffect;
import dev.modlock.item.ItemConfigs;
import dev.modlock.item.Redemption;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
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

    /** Server -> owning client: sources of the player's active knowledge effects (for the effect tooltip). */
    public record KnowledgeSourcesPayload(KnowledgeSources sources) implements CustomPacketPayload {
        public static final Type<KnowledgeSourcesPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(ModLock.MODID, "knowledge_sources"));
        public static final StreamCodec<RegistryFriendlyByteBuf, KnowledgeSourcesPayload> CODEC =
                KnowledgeSources.STREAM_CODEC.map(KnowledgeSourcesPayload::new, KnowledgeSourcesPayload::sources);

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

    /**
     * Server -> client: open the "pick N of M" screen. purpose: "start" (starting choice), "key" (CHOOSE Mod Key)
     * or "pure_tome"; it selects the title and is echoed back in the answer.
     */
    public record OpenChoicePayload(String purpose, int count, List<StartingChoice.Option> options) implements CustomPacketPayload {
        public static final Type<OpenChoicePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(ModLock.MODID, "open_choice"));
        public static final StreamCodec<FriendlyByteBuf, OpenChoicePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenChoicePayload::purpose,
                ByteBufCodecs.VAR_INT, OpenChoicePayload::count,
                OPTION.apply(ByteBufCodecs.list()), OpenChoicePayload::options,
                OpenChoicePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: the keys the player picked. */
    public record ChoiceAnswerPayload(String purpose, List<String> keys) implements CustomPacketPayload {
        public static final Type<ChoiceAnswerPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(ModLock.MODID, "choice_answer"));
        public static final StreamCodec<ByteBuf, ChoiceAnswerPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ChoiceAnswerPayload::purpose,
                STRINGS, ChoiceAnswerPayload::keys,
                ChoiceAnswerPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One selectable entry of a configuration screen. label is empty for items/blocks (the client names them). */
    public record ConfigEntry(String id, String label) {}

    private static final StreamCodec<FriendlyByteBuf, ConfigEntry> CONFIG_ENTRY = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ConfigEntry::id,
            ByteBufCodecs.STRING_UTF8, ConfigEntry::label,
            ConfigEntry::new);

    /**
     * Server -> client: open the configuration screen of an unlock item. kind = ItemConfigs.Kind name;
     * flags = ItemConfigs.FLAG_*; preselected = current entries when an OP reconfigures a key.
     */
    public record OpenConfigPayload(String kind, boolean mainHand, List<ConfigEntry> entries, List<String> preselected,
                                    int flags, int maxMinutes, int defaultMinutes, boolean defaultCurable)
            implements CustomPacketPayload {
        public static final Type<OpenConfigPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(ModLock.MODID, "open_config"));
        public static final StreamCodec<FriendlyByteBuf, OpenConfigPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.kind());
                    buf.writeBoolean(p.mainHand());
                    CONFIG_ENTRY.apply(ByteBufCodecs.list()).encode(buf, p.entries());
                    buf.writeCollection(p.preselected(), FriendlyByteBuf::writeUtf);
                    buf.writeVarInt(p.flags());
                    buf.writeVarInt(p.maxMinutes());
                    buf.writeVarInt(p.defaultMinutes());
                    buf.writeBoolean(p.defaultCurable());
                },
                buf -> new OpenConfigPayload(buf.readUtf(), buf.readBoolean(),
                        CONFIG_ENTRY.apply(ByteBufCodecs.list()).decode(buf), buf.readList(FriendlyByteBuf::readUtf),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: the configuration the player confirmed ("Lock"). Fully re-validated by ItemConfigs. */
    public record ConfigAnswerPayload(String kind, boolean mainHand, List<String> selected, String mode, int amount,
                                      int minutes, boolean curable, String name) implements CustomPacketPayload {
        public static final Type<ConfigAnswerPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(ModLock.MODID, "config_answer"));
        public static final StreamCodec<FriendlyByteBuf, ConfigAnswerPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.kind());
                    buf.writeBoolean(p.mainHand());
                    buf.writeCollection(p.selected(), FriendlyByteBuf::writeUtf);
                    buf.writeUtf(p.mode());
                    buf.writeVarInt(p.amount());
                    buf.writeVarInt(p.minutes());
                    buf.writeBoolean(p.curable());
                    buf.writeUtf(p.name(), 256);
                },
                buf -> new ConfigAnswerPayload(buf.readUtf(64), buf.readBoolean(),
                        buf.readCollection(n -> new ArrayList<>(Math.min(n, 1024)), b -> b.readUtf(256)),
                        buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(256)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("3");
        r.playToClient(SyncPayload.TYPE, SyncPayload.CODEC, ClientHandlers::onSync);
        r.playToClient(LockedResultPayload.TYPE, LockedResultPayload.CODEC, ClientHandlers::onLockedResult);
        r.playToClient(OpenChoicePayload.TYPE, OpenChoicePayload.CODEC, ClientHandlers::onOpenChoice);
        r.playToClient(OpenConfigPayload.TYPE, OpenConfigPayload.CODEC, ClientHandlers::onOpenConfig);
        r.playToClient(KnowledgeSourcesPayload.TYPE, KnowledgeSourcesPayload.CODEC, ClientHandlers::onKnowledgeSources);
        r.playToServer(ChoiceAnswerPayload.TYPE, ChoiceAnswerPayload.CODEC, ModLockNetwork::onAnswer);
        r.playToServer(ConfigAnswerPayload.TYPE, ConfigAnswerPayload.CODEC, ModLockNetwork::onConfigAnswer);
    }

    private static void onAnswer(ChoiceAnswerPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (StartingChoice.PURPOSE.equals(p.purpose())) StartingChoice.answer(sp, p.keys());
            else Redemption.answer(sp, p.purpose(), p.keys());
        });
    }

    private static void onConfigAnswer(ConfigAnswerPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) ItemConfigs.answer(sp, p);
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
        List<String> items = new ArrayList<>();
        if (d != null) d.items.forEach(i -> items.add(i.toString()));
        // temporary Recipe Scroll knowledge
        for (MobEffectInstance inst : TemporaryKnowledge.active(player)) {
            if (inst.getEffect().value() instanceof KnowledgeEffect k && k.kind() == KnowledgeEffect.Kind.RECIPE) {
                items.add(String.valueOf(k.target()));
            }
        }
        send(player, new SyncPayload(locked, unlocked, items, new ArrayList<>(hints)));
        send(player, new KnowledgeSourcesPayload(player.getData(ModLockAttachments.KNOWLEDGE_SOURCES)));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p);
    }

    public static void sendLockedResult(ServerPlayer player, int containerId, int slot, ItemStack preview) {
        send(player, new LockedResultPayload(containerId, slot, preview));
    }

    public static void openChoice(ServerPlayer player, String purpose, int count, List<StartingChoice.Option> options) {
        send(player, new OpenChoicePayload(purpose, count, options));
    }

    public static void openConfig(ServerPlayer player, OpenConfigPayload payload) {
        send(player, payload);
    }

    /** Skips players whose connection does not know the channel (vanilla clients, test mock players). */
    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
