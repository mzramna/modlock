package dev.modlock.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import dev.modlock.attach.KnowledgeSources;
import dev.modlock.attach.ModLockAttachments;
import dev.modlock.config.ModLockConfig;
import dev.modlock.effect.KnowledgeEffect;
import dev.modlock.effect.KnowledgeEffects;
import dev.modlock.net.ModLockNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Temporary knowledge granted by effects (Mundane/Pure Tomes, temporary Recipe Scrolls and Machine Licenses).
 * The effects are the source of truth: a player "knows" something while the matching effect is active.
 * Offline players have no effects, so temporary knowledge never applies to them (e.g. furnaces).
 */
public final class TemporaryKnowledge {
    public enum ApplyResult { APPLIED, NO_GAIN, NO_EFFECT }

    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();

    private TemporaryKnowledge() {}

    @Nullable
    private static ServerPlayer online(MinecraftServer server, UUID player) {
        return server.getPlayerList().getPlayer(player);
    }

    private static boolean has(MinecraftServer server, UUID player, @Nullable Holder<MobEffect> effect) {
        if (effect == null) return false;
        ServerPlayer p = online(server, player);
        return p != null && p.hasEffect(effect);
    }

    public static boolean hasMod(MinecraftServer server, UUID player, String namespace) {
        return has(server, player, KnowledgeEffects.knowledge(namespace));
    }

    public static boolean hasItem(MinecraftServer server, UUID player, Identifier item) {
        return has(server, player, KnowledgeEffects.recipe(item));
    }

    public static boolean hasMachine(MinecraftServer server, UUID player, Identifier block) {
        return has(server, player, KnowledgeEffects.machine(block));
    }

    // ---------------- applying ----------------

    /** Milk rule: the global mode wins; with PER_ITEM, null (source without a checkbox) uses unconfigured_curable. */
    public static boolean resolveCurable(@Nullable Boolean chosen) {
        return switch (ModLockConfig.milkMode) {
            case ALWAYS -> true;
            case NEVER -> false;
            case PER_ITEM -> chosen == null ? ModLockConfig.milkUnconfiguredCurable : chosen;
        };
    }

    /**
     * Milk rule of an effect that was not applied by a ModLock item (e.g. /effect give): the 1.21.1
     * KnowledgeEffect#fillEffectCures default.
     */
    public static boolean defaultCurable() {
        return ModLockConfig.milkMode == ModLockConfig.MilkMode.ALWAYS
                || (ModLockConfig.milkMode == ModLockConfig.MilkMode.PER_ITEM && ModLockConfig.milkUnconfiguredCurable);
    }

    /**
     * NeoForge 26.1 has no effect cures any more, so the milk setting of each active knowledge effect lives
     * in the player's KNOWLEDGE_SOURCES attachment (written by apply); effects without an entry use the default.
     */
    public static boolean isCurable(LivingEntity entity, MobEffectInstance inst) {
        String id = inst.getEffect().unwrapKey().map(k -> k.identifier().toString()).orElse("");
        KnowledgeSources.Source src = entity.getData(ModLockAttachments.KNOWLEDGE_SOURCES).map().get(id);
        return src != null ? src.curable() : defaultCurable();
    }

    /**
     * Item whose clear_all_effects consume effect is running (milk bucket, totem of undying), set by
     * ClearAllStatusEffectsConsumeEffectMixin. 26.1 routes milk and totems through removeAllEffects.
     */
    private static final ThreadLocal<ItemStack> CLEARING_ITEM = new ThreadLocal<>();

    public static void beginClearAll(ItemStack stack) {
        CLEARING_ITEM.set(stack);
    }

    public static void endClearAll() {
        CLEARING_ITEM.remove();
    }

    /**
     * Same outcome as the 1.21.1 cures: milk (any consumed item that clears all effects) only removes
     * curable knowledge, a totem (death protection) keeps all knowledge, and every other removal
     * (/effect clear, other mods, commands) still removes it.
     */
    private static boolean keepOnRemove(LivingEntity entity, MobEffectInstance inst) {
        ItemStack clearing = CLEARING_ITEM.get();
        if (clearing == null) return false;
        if (clearing.has(DataComponents.DEATH_PROTECTION)) return true;
        return !isCurable(entity, inst);
    }

    private static int maxTicks() {
        return Math.max(1, ModLockConfig.maxKnowledgeMinutes) * 1200;
    }

    /**
     * Applies (or extends) a knowledge effect following [temporary_knowledge] stack_mode. Returns NO_GAIN
     * when the remaining time would not increase (the item must then not be used up).
     * {@code curable} is the item's already-resolved milk setting.
     */
    public static ApplyResult apply(ServerPlayer player, @Nullable Holder<MobEffect> effect, int minutes,
                                    boolean curable, Component source) {
        if (effect == null) return ApplyResult.NO_EFFECT;
        int max = maxTicks();
        int add = Math.min(max, Math.max(1, minutes) * 1200);
        MobEffectInstance existing = player.getEffect(effect);
        int duration = add;
        if (existing != null) {
            if (existing.isInfiniteDuration()) return ApplyResult.NO_GAIN;
            int cur = existing.getDuration();
            duration = ModLockConfig.stackMode == ModLockConfig.StackMode.ADD
                    ? (int) Math.min((long) cur + add, max) : Math.max(cur, add);
            if (duration <= cur) return ApplyResult.NO_GAIN;
            // immune wins: the result can be removed by milk only if both were
            curable = curable && isCurable(player, existing);
        }
        MobEffectInstance inst = new MobEffectInstance(effect, duration, 0, false, false, true);
        player.forceAddEffect(inst, null);
        String id = effect.unwrapKey().map(k -> k.identifier().toString()).orElse("");
        player.setData(ModLockAttachments.KNOWLEDGE_SOURCES,
                player.getData(ModLockAttachments.KNOWLEDGE_SOURCES).with(id, new KnowledgeSources.Source(source, curable)));
        ModLockNetwork.sync(player);
        return ApplyResult.APPLIED;
    }

    /** Active ModLock knowledge effects of a player. */
    public static List<MobEffectInstance> active(LivingEntity player) {
        List<MobEffectInstance> out = new ArrayList<>();
        for (MobEffectInstance inst : player.getActiveEffects()) {
            if (inst.getEffect().value() instanceof KnowledgeEffect) out.add(inst);
        }
        return out;
    }

    /** Removes one (id != null) or every knowledge effect. Returns how many were removed. */
    public static int clear(ServerPlayer player, @Nullable Identifier id) {
        int n = 0;
        for (MobEffectInstance inst : active(player)) {
            if (id != null && !inst.getEffect().is(id)) continue;
            if (player.removeEffect(inst.getEffect())) n++;
        }
        if (n > 0) cleanup(player);
        return n;
    }

    // ---------------- events ----------------

    private static void markDirty(LivingEntity entity, @Nullable Holder<MobEffect> effect) {
        if (entity instanceof ServerPlayer && effect != null && effect.value() instanceof KnowledgeEffect) {
            DIRTY.add(entity.getUUID());
        }
    }

    public static void onRemove(MobEffectEvent.Remove event) {
        MobEffectInstance inst = event.getEffectInstance();
        if (inst != null && inst.getEffect().value() instanceof KnowledgeEffect && keepOnRemove(event.getEntity(), inst)) {
            event.setCanceled(true);
            return;
        }
        markDirty(event.getEntity(), event.getEffect());
    }

    public static void onExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null) markDirty(event.getEntity(), event.getEffectInstance().getEffect());
    }

    public static void onAdded(MobEffectEvent.Added event) {
        markDirty(event.getEntity(), event.getEffectInstance().getEffect());
    }

    /** Effects are removed from the map after the events fire, so access is resynced on the next tick. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (DIRTY.isEmpty()) return;
        List<UUID> ids = new ArrayList<>(DIRTY);
        DIRTY.clear();
        for (UUID id : ids) {
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(id);
            if (p != null) cleanup(p);
        }
    }

    /** Drops sources of effects that are gone and resyncs access to the client. */
    public static void cleanup(ServerPlayer player) {
        KnowledgeSources src = player.getData(ModLockAttachments.KNOWLEDGE_SOURCES);
        KnowledgeSources kept = src;
        for (String id : src.map().keySet()) {
            Identifier rl = Identifier.tryParse(id);
            var holder = rl == null ? null : BuiltInRegistries.MOB_EFFECT.get(rl).orElse(null);
            if (holder == null || !player.hasEffect(holder)) kept = kept.without(id);
        }
        if (kept != src) player.setData(ModLockAttachments.KNOWLEDGE_SOURCES, kept);
        ModLockNetwork.sync(player);
    }

    /** One action-bar warning, warn_before_expire_seconds before an effect ends. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int warn = ModLockConfig.warnBeforeExpireSeconds * 20;
        if (warn <= 0) return;
        for (MobEffectInstance inst : player.getActiveEffects()) {
            if (inst.getDuration() == warn && inst.getEffect().value() instanceof KnowledgeEffect k) {
                player.sendOverlayMessage(Component.translatable("message.modlock.knowledge.expiring",
                        k.getDisplayName(), ModLockConfig.warnBeforeExpireSeconds).withStyle(ChatFormatting.GOLD));
            }
        }
    }
}
