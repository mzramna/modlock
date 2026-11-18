package dev.modlock.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import dev.modlock.config.ModLockConfig;
import dev.modlock.core.AccessRules;
import dev.modlock.core.ModBundles;
import dev.modlock.core.ModLockSavedData;
import dev.modlock.core.ModNames;
import dev.modlock.core.PlayerClasses;
import dev.modlock.core.StartingChoice;
import dev.modlock.core.TemporaryKnowledge;
import dev.modlock.effect.KnowledgeEffects;
import dev.modlock.net.ModLockNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Server side of using the unlock items. Every "use" returns true only when one item must be used up,
 * i.e. after a successful grant. Picker answers (CHOOSE keys, unbound Pure Tomes) are re-validated against
 * freshly computed candidates and against the item still in the same hand.
 */
public final class Redemption {
    public static final String PURPOSE_KEY = "key";
    public static final String PURPOSE_PURE = "pure_tome";

    private record Pending(String purpose, InteractionHand hand, Item item, @Nullable Object snapshot, int count) {}

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private Redemption() {}

    private static void say(ServerPlayer p, String key, Object... args) {
        p.displayClientMessage(Component.translatable(key, args), true);
    }

    private static String takenBy(ModLockSavedData data, String key, UUID player) {
        if (!ModLockConfig.exclusive) return "";
        UUID owner = data.owners().get(key);
        return owner == null || owner.equals(player) ? "" : data.ownerNames().getOrDefault(key, "?");
    }

    // =============================== Mod Key ===============================

    /** Normalized key ("mod:x" / "bundle:x") of a key entry ("x" / "bundle:x"). */
    static String normalize(String entry) {
        return entry.startsWith("bundle:") ? entry : "mod:" + entry;
    }

    /**
     * Entries of the key the player can still get: minus what they already own permanently, minus free mods,
     * minus closed/class-restricted ones and, with [starting] exclusive, minus entries taken by someone else.
     */
    public static List<String> keyCandidates(ServerPlayer p, KeyData data) {
        MinecraftServer server = Objects.requireNonNull(p.getServer());
        ModLockSavedData saved = ModLockSavedData.get(server);
        ModLockSavedData.PlayerData d = saved.peek(p.getUUID());
        Set<String> classes = PlayerClasses.of(server, p.getUUID());
        List<String> out = new ArrayList<>();
        for (String raw : data.entries()) {
            String key = normalize(raw);
            if (out.contains(key)) continue;
            String id = key.substring(key.indexOf(':') + 1);
            if (key.startsWith("bundle:")) {
                ModBundles.Bundle b = ModBundles.get(id);
                if (b == null || (d != null && d.bundles.contains(id)) || !PlayerClasses.canUse(classes, b)) continue;
                if (b.members().stream().noneMatch(AccessRules::isModLocked)) continue;
            } else {
                if (!AccessRules.isModLocked(id) || AccessRules.ownsModPermanently(server, p.getUUID(), id)) continue;
                if (AccessRules.isClosedDenied(server, p.getUUID(), id)) continue;
            }
            if (!takenBy(saved, key, p.getUUID()).isEmpty()) continue;
            out.add(key);
        }
        return out;
    }

    /** Permanently grants "mod:x" / "bundle:x" keys (SavedData), with exclusivity bookkeeping and announcement. */
    public static void grantKeys(ServerPlayer p, List<String> keys, String announceKey) {
        MinecraftServer server = Objects.requireNonNull(p.getServer());
        ModLockSavedData data = ModLockSavedData.get(server);
        ModLockSavedData.PlayerData d = data.player(p.getUUID());
        for (String k : keys) {
            String id = k.substring(k.indexOf(':') + 1);
            Component label;
            if (k.startsWith("bundle:")) {
                d.bundles.add(id);
                ModBundles.Bundle b = ModBundles.get(id);
                label = Component.literal(b == null ? id : b.name());
            } else {
                d.mods.add(id);
                label = ModNames.component(id);
            }
            if (ModLockConfig.exclusive) {
                data.owners().put(k, p.getUUID());
                data.ownerNames().put(k, p.getGameProfile().getName());
            }
            p.sendSystemMessage(Component.translatable("message.modlock.unlocked", label).withStyle(ChatFormatting.GREEN));
            if (ModLockConfig.keyAnnounceRedeem && announceKey != null) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.translatable(announceKey, p.getDisplayName(), label), false);
            }
        }
        data.setDirty();
        ModLockNetwork.sync(p);
    }

    public static boolean useKey(ServerPlayer p, InteractionHand hand, ItemStack stack, KeyData data) {
        List<String> left = keyCandidates(p, data);
        if (left.isEmpty()) {
            say(p, "message.modlock.nothing_left");
            return false;
        }
        int n = Math.min(Math.max(1, data.amount()), left.size());
        switch (data.mode()) {
            case ALL -> grantKeys(p, left, "message.modlock.key.redeemed");
            case RANDOM -> {
                List<String> shuffled = new ArrayList<>(left);
                Collections.shuffle(shuffled, new java.util.Random(p.getRandom().nextLong()));
                grantKeys(p, shuffled.subList(0, n), "message.modlock.key.redeemed");
            }
            case CHOOSE -> {
                PENDING.put(p.getUUID(), new Pending(PURPOSE_KEY, hand, stack.getItem(), data, n));
                ModLockNetwork.openChoice(p, PURPOSE_KEY, n, options(p, left));
                return false;
            }
        }
        return true;
    }

    /** Picker options for "mod:x" / "bundle:x" keys. */
    private static List<StartingChoice.Option> options(ServerPlayer p, List<String> keys) {
        List<StartingChoice.Option> out = new ArrayList<>();
        for (String k : keys) {
            String id = k.substring(k.indexOf(':') + 1);
            if (k.startsWith("bundle:")) {
                ModBundles.Bundle b = ModBundles.get(id);
                if (b != null) out.add(StartingChoice.bundleOption(b, ""));
            } else {
                out.add(StartingChoice.modOption(id, ""));
            }
        }
        return out;
    }

    // =============================== Recipe Scroll ===============================

    public static boolean useScroll(ServerPlayer p, TeachData d) {
        MinecraftServer server = Objects.requireNonNull(p.getServer());
        ResourceLocation id = ResourceLocation.tryParse(d.target());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            say(p, "message.modlock.unknown_target", d.target());
            return false;
        }
        if (!(d.byOp() && ModLockConfig.scrollOpsBypass) && ItemConfigs.scrollRestricted(id)) {
            say(p, "message.modlock.restricted");
            return false;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (AccessRules.isClosedDenied(server, p.getUUID(), id.getNamespace())) {
            say(p, "message.modlock.closed_denied", ModNames.component(id.getNamespace()));
            return false;
        }
        if (AccessRules.hasPermanentItemAccess(server, p.getUUID(), item)) {
            say(p, "message.modlock.already_known");
            return false;
        }
        Component name = item.getDescription();
        if (d.permanent()) {
            ModLockSavedData data = ModLockSavedData.get(server);
            data.player(p.getUUID()).items.add(id);
            data.setDirty();
            ModLockNetwork.sync(p);
            p.sendSystemMessage(Component.translatable("message.modlock.learned", name).withStyle(ChatFormatting.GREEN));
            return true;
        }
        return applied(p, TemporaryKnowledge.apply(p, KnowledgeEffects.recipe(id), d.minutes(),
                TemporaryKnowledge.resolveCurable(d.curable()),
                Component.translatable("source.modlock.recipe_scroll", d.creator())), name, d.minutes());
    }

    private static boolean applied(ServerPlayer p, TemporaryKnowledge.ApplyResult r, Component what, int minutes) {
        switch (r) {
            case APPLIED -> {
                p.sendSystemMessage(Component.translatable("message.modlock.knowledge.gained", what,
                        Component.translatable("tooltip.modlock.minutes", minutes)).withStyle(ChatFormatting.GREEN));
                return true;
            }
            case NO_GAIN -> say(p, "message.modlock.no_gain");
            case NO_EFFECT -> say(p, "message.modlock.no_effect", what);
        }
        return false;
    }

    // =============================== Machine License ===============================

    public static boolean useLicense(ServerPlayer p, TeachData d) {
        MinecraftServer server = Objects.requireNonNull(p.getServer());
        ResourceLocation id = ResourceLocation.tryParse(d.target());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            say(p, "message.modlock.unknown_target", d.target());
            return false;
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        ModLockSavedData data = ModLockSavedData.get(server);
        ModLockSavedData.PlayerData pd = data.peek(p.getUUID());
        if ((pd != null && pd.machines.contains(id)) || AccessRules.ownsModPermanently(server, p.getUUID(), id.getNamespace())
                || !AccessRules.isModLocked(id.getNamespace())) {
            say(p, "message.modlock.already_known");
            return false;
        }
        if (d.permanent()) {
            data.player(p.getUUID()).machines.add(id);
            data.setDirty();
            ModLockNetwork.sync(p);
            p.sendSystemMessage(Component.translatable("message.modlock.learned", block.getName()).withStyle(ChatFormatting.GREEN));
            return true;
        }
        return applied(p, TemporaryKnowledge.apply(p, KnowledgeEffects.machine(id), d.minutes(),
                TemporaryKnowledge.resolveCurable(d.curable()),
                Component.translatable("source.modlock.machine_license", d.creator())), block.getName(), d.minutes());
    }

    // =============================== Mundane Tome ===============================

    public static boolean useTome(ServerPlayer p, TeachData d) {
        MinecraftServer server = Objects.requireNonNull(p.getServer());
        String mod = d.target();
        if (!ModLockConfig.tomeAllowSelfUse && p.getUUID().toString().equals(d.creatorId())) {
            say(p, "message.modlock.self_use");
            return false;
        }
        if (!(d.byOp() && ModLockConfig.tomeOpsBypass) && !ItemConfigs.tomeAllowed(mod)) {
            say(p, "message.modlock.restricted");
            return false;
        }
        if (AccessRules.isClosedDenied(server, p.getUUID(), mod)) {
            say(p, "message.modlock.closed_denied", ModNames.component(mod));
            return false;
        }
        if (!AccessRules.isModLocked(mod) || AccessRules.ownsModPermanently(server, p.getUUID(), mod)) {
            say(p, "message.modlock.already_known");
            return false;
        }
        Component source = d.byOp()
                ? Component.translatable("source.modlock.mundane_tome_admin", MundaneTomeItem.sharedBy(d))
                : Component.translatable("source.modlock.mundane_tome", d.creator());
        return applied(p, TemporaryKnowledge.apply(p, KnowledgeEffects.knowledge(mod), d.minutes(),
                TemporaryKnowledge.resolveCurable(d.curable()), source), ModNames.component(mod), d.minutes());
    }

    // =============================== Pure Tome ===============================

    /** Locked mods the player has no access to, filtered by [pure_tome] restrictions and exclusivity. */
    public static List<String> pureCandidates(ServerPlayer p) {
        MinecraftServer server = Objects.requireNonNull(p.getServer());
        ModLockSavedData saved = ModLockSavedData.get(server);
        List<String> out = new ArrayList<>();
        for (String mod : ItemConfigs.lockedMods()) {
            if (AccessRules.hasModAccess(server, p.getUUID(), mod)) continue;
            if (AccessRules.isClosedDenied(server, p.getUUID(), mod)) continue;
            if (!ModLockConfig.allowedBy(ModLockConfig.pureRestrictionMode, ModLockConfig.pureRestrictedMods, mod)) continue;
            if (ModLockConfig.pureRespectExclusivity && !takenBy(saved, "mod:" + mod, p.getUUID()).isEmpty()) continue;
            if (!ModLockConfig.purePermanent && KnowledgeEffects.knowledge(mod) == null) continue;
            out.add(mod);
        }
        return out;
    }

    public static boolean usePureTome(ServerPlayer p, InteractionHand hand, ItemStack stack) {
        String bundleId = stack.get(ModLockItems.BUNDLE.get());
        if (bundleId != null) return usePureBundle(p, bundleId);
        List<String> left = pureCandidates(p);
        if (left.isEmpty()) {
            say(p, "message.modlock.nothing_left");
            return false;
        }
        PENDING.put(p.getUUID(), new Pending(PURPOSE_PURE, hand, stack.getItem(), null, 1));
        ModLockNetwork.openChoice(p, PURPOSE_PURE, 1, options(p, left.stream().map(m -> "mod:" + m).toList()));
        return false;
    }

    /** Bound Pure Tome: knowledge of every locked member of the bundle the player can use. */
    public static boolean usePureBundle(ServerPlayer p, String bundleId) {
        MinecraftServer server = Objects.requireNonNull(p.getServer());
        ModBundles.Bundle b = ModBundles.get(bundleId);
        if (b == null) {
            say(p, "message.modlock.unknown_target", bundleId);
            return false;
        }
        if (!PlayerClasses.canUse(PlayerClasses.of(server, p.getUUID()), b)) {
            say(p, "message.modlock.cannot_use_bundle", b.name());
            return false;
        }
        if (ModLockConfig.purePermanent) {
            ModLockSavedData.PlayerData d = ModLockSavedData.get(server).peek(p.getUUID());
            if (d != null && d.bundles.contains(b.id())) {
                say(p, "message.modlock.nothing_left");
                return false;
            }
            grantKeys(p, List.of("bundle:" + b.id()), null);
            return true;
        }
        boolean any = false;
        Component source = Component.translatable("source.modlock.pure_tome");
        boolean curable = TemporaryKnowledge.resolveCurable(null);
        for (String m : b.members().stream().sorted().toList()) {
            if (!AccessRules.isModLocked(m) || AccessRules.ownsModPermanently(server, p.getUUID(), m)) continue;
            if (TemporaryKnowledge.apply(p, KnowledgeEffects.knowledge(m), ModLockConfig.pureMinutes, curable, source)
                    == TemporaryKnowledge.ApplyResult.APPLIED) {
                any = true;
            }
        }
        if (!any) {
            say(p, "message.modlock.nothing_left");
            return false;
        }
        p.sendSystemMessage(Component.translatable("message.modlock.knowledge.gained", Component.literal(b.name()),
                Component.translatable("tooltip.modlock.minutes", ModLockConfig.pureMinutes)).withStyle(ChatFormatting.GREEN));
        return true;
    }

    /** Grants one mod from an unbound Pure Tome (permanent or knowledge effect). Returns true on success. */
    public static boolean grantPure(ServerPlayer p, String mod) {
        if (ModLockConfig.purePermanent) {
            grantKeys(p, List.of("mod:" + mod), null);
            return true;
        }
        return applied(p, TemporaryKnowledge.apply(p, KnowledgeEffects.knowledge(mod), ModLockConfig.pureMinutes,
                TemporaryKnowledge.resolveCurable(null), Component.translatable("source.modlock.pure_tome")),
                ModNames.component(mod), ModLockConfig.pureMinutes);
    }

    // =============================== picker answers ===============================

    /** Answer of the picker opened by a CHOOSE key or an unbound Pure Tome. */
    public static void answer(ServerPlayer p, String purpose, List<String> keys) {
        Pending pending = PENDING.remove(p.getUUID());
        if (pending == null || !pending.purpose().equals(purpose)) return;
        ItemStack stack = p.getItemInHand(pending.hand());
        if (!stack.is(pending.item())) {
            say(p, "message.modlock.choice_invalid");
            return;
        }
        if (PURPOSE_KEY.equals(purpose)) {
            KeyData data = stack.get(ModLockItems.KEY.get());
            if (data == null || !data.equals(pending.snapshot())) {
                say(p, "message.modlock.choice_invalid");
                return;
            }
            List<String> left = keyCandidates(p, data);
            int needed = Math.min(Math.max(1, data.amount()), left.size());
            if (!validPick(keys, left, needed)) {
                say(p, "message.modlock.choice_invalid");
                return;
            }
            grantKeys(p, keys, "message.modlock.key.redeemed");
            stack.consume(1, p);
        } else if (PURPOSE_PURE.equals(purpose)) {
            if (stack.has(ModLockItems.BUNDLE.get())) return;
            List<String> left = pureCandidates(p).stream().map(m -> "mod:" + m).toList();
            if (!validPick(keys, left, Math.min(1, left.size()))) {
                say(p, "message.modlock.choice_invalid");
                return;
            }
            if (grantPure(p, keys.get(0).substring(4))) stack.consume(1, p);
        }
    }

    private static boolean validPick(List<String> keys, List<String> allowed, int needed) {
        return needed > 0 && keys.size() == needed && new HashSet<>(keys).size() == keys.size() && allowed.containsAll(keys);
    }
}
