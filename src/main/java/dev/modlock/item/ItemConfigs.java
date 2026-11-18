package dev.modlock.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import dev.modlock.ModLock;
import dev.modlock.config.ModLockConfig;
import dev.modlock.core.AccessRules;
import dev.modlock.core.ModBundles;
import dev.modlock.core.ModNames;
import dev.modlock.effect.KnowledgeEffects;
import dev.modlock.net.ModLockNetwork;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * Server side of the configuration screens: what each player may put on an item, and validation of the
 * answer sent by the client (never trusted: everything is recomputed here).
 */
public final class ItemConfigs {
    public static final int MAX_ENTRIES = 4000;
    public static final int MAX_NAME = 50;

    public static final int FLAG_PERMANENT = 1;
    public static final int FLAG_TEMPORARY = 2;
    public static final int FLAG_DURATION = 4;
    public static final int FLAG_MILK = 8;

    public enum Kind {
        KEY(ModLockItems.MOD_KEY::get),
        SCROLL(ModLockItems.RECIPE_SCROLL::get),
        TOME(ModLockItems.MUNDANE_TOME::get),
        LICENSE(ModLockItems.MACHINE_LICENSE::get);

        private final Supplier<Item> item;

        Kind(Supplier<Item> item) {
            this.item = item;
        }

        public Item item() {
            return item.get();
        }

        @Nullable
        public static Kind byName(String s) {
            for (Kind k : values()) if (k.name().equalsIgnoreCase(s)) return k;
            return null;
        }
    }

    private ItemConfigs() {}

    /** OP for this item: creative, or the configured permission level. */
    public static boolean privileged(ServerPlayer p, Kind kind) {
        int level = kind == Kind.KEY ? ModLockConfig.keyPermissionLevel : ModLockConfig.opPermissionLevel;
        return p.isCreative()
                || p.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
    }

    // ---------------- candidates ----------------

    /** Installed mods that are locked (what unlock items can be about). */
    public static List<String> lockedMods() {
        List<String> out = new ArrayList<>();
        for (IModInfo info : ModList.get().getMods()) {
            String id = info.getModId();
            if (id.equals(ModLock.MODID)) continue;
            if (AccessRules.isModLocked(id)) out.add(id);
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private static Map<RecipeManager, Set<Item>> craftableCache = new IdentityHashMap<>();

    /** Items produced by at least one recipe (cached per RecipeManager instance, i.e. until /reload). */
    static synchronized Set<Item> craftable(MinecraftServer server) {
        RecipeManager rm = server.getRecipeManager();
        Set<Item> set = craftableCache.get(rm);
        if (set != null) return set;
        set = new HashSet<>();
        // 26.1 recipes have no static getResultItem: use the results of their recipe book displays
        ContextMap context = SlotDisplayContext.fromLevel(server.overworld());
        for (RecipeHolder<?> h : rm.getRecipes()) {
            try {
                for (RecipeDisplay display : h.value().display()) {
                    ItemStack r = display.result().resolveForFirstStack(context);
                    if (!r.isEmpty()) set.add(r.getItem());
                }
            } catch (RuntimeException ignored) {
                // some mod recipes cannot compute a static result
            }
        }
        craftableCache = new IdentityHashMap<>();
        craftableCache.put(rm, set);
        return set;
    }

    /**
     * May this player put this item on a Recipe Scroll? OPs: any item of a locked mod. Others: only items of
     * locked mods they can craft right now (PERMANENT scrolls need permanent access). Restrictions apply
     * to everyone unless ops_bypass_restrictions.
     */
    public static boolean canScroll(ServerPlayer p, Item item, boolean priv, boolean permanent) {
        if (item == Items.AIR) return false;
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        String ns = id.getNamespace();
        if (ns.equals(ModLock.MODID) || !AccessRules.isModLocked(ns)) return false;
        if (!(priv && ModLockConfig.scrollOpsBypass) && scrollRestricted(id)) return false;
        if (priv) return true;
        MinecraftServer server = p.level().getServer();
        if (server == null || !craftable(server).contains(item)) return false;
        if (permanent) {
            return ModLockConfig.scrollPermanentByNonOp && AccessRules.hasPermanentItemAccess(server, p.getUUID(), item);
        }
        return AccessRules.canCraft(p, null, new ItemStack(item));
    }

    public static boolean scrollRestricted(Identifier item) {
        return ModLockConfig.scrollBlacklistItems.contains(item.toString())
                || ModLockConfig.scrollRestrictedMods.contains(item.getNamespace());
    }

    /** Mundane Tome: OPs any locked mod, others only mods they own permanently; restriction_mode applies. */
    public static boolean canTome(ServerPlayer p, String mod, boolean priv) {
        if (!AccessRules.isModLocked(mod) || KnowledgeEffects.knowledge(mod) == null) return false;
        if (!(priv && ModLockConfig.tomeOpsBypass) && !tomeAllowed(mod)) return false;
        if (priv) return true;
        return p.level().getServer() != null && AccessRules.ownsModPermanently(p.level().getServer(), p.getUUID(), mod);
    }

    public static boolean tomeAllowed(String mod) {
        return ModLockConfig.allowedBy(ModLockConfig.tomeRestrictionMode, ModLockConfig.tomeRestrictedMods, mod);
    }

    /** Machine License: blocks with a block entity of locked mods; non-OPs only of mods they own permanently. */
    public static boolean canLicense(ServerPlayer p, Block block, boolean priv, boolean permanent) {
        if (!(block instanceof EntityBlock)) return false;
        String ns = BuiltInRegistries.BLOCK.getKey(block).getNamespace();
        if (ns.equals(ModLock.MODID) || !AccessRules.isModLocked(ns)) return false;
        if (priv) return true;
        if (permanent && !ModLockConfig.licensePermanentByNonOp) return false;
        return p.level().getServer() != null && AccessRules.ownsModPermanently(p.level().getServer(), p.getUUID(), ns);
    }

    /** Key: every bundle and every locked mod. */
    public static List<ModLockNetwork.ConfigEntry> keyCandidates() {
        List<ModLockNetwork.ConfigEntry> out = new ArrayList<>();
        for (ModBundles.Bundle b : ModBundles.all()) out.add(new ModLockNetwork.ConfigEntry("bundle:" + b.id(), b.name()));
        for (String m : lockedMods()) out.add(new ModLockNetwork.ConfigEntry(m, ModNames.displayName(m)));
        return out;
    }

    public static List<ModLockNetwork.ConfigEntry> candidates(ServerPlayer p, Kind kind, boolean priv) {
        List<ModLockNetwork.ConfigEntry> out = new ArrayList<>();
        switch (kind) {
            case KEY -> out.addAll(keyCandidates());
            case SCROLL -> {
                for (var e : BuiltInRegistries.ITEM.entrySet()) {
                    if (out.size() >= MAX_ENTRIES) break;
                    if (canScroll(p, e.getValue(), priv, false)) {
                        out.add(new ModLockNetwork.ConfigEntry(e.getKey().identifier().toString(), ""));
                    }
                }
            }
            case TOME -> {
                for (String m : lockedMods()) {
                    if (canTome(p, m, priv)) out.add(new ModLockNetwork.ConfigEntry(m, ModNames.displayName(m)));
                }
            }
            case LICENSE -> {
                for (var e : BuiltInRegistries.BLOCK.entrySet()) {
                    if (out.size() >= MAX_ENTRIES) break;
                    if (canLicense(p, e.getValue(), priv, false)) {
                        out.add(new ModLockNetwork.ConfigEntry(e.getKey().identifier().toString(), ""));
                    }
                }
            }
        }
        if (kind == Kind.SCROLL || kind == Kind.LICENSE) out.sort(Comparator.comparing(ModLockNetwork.ConfigEntry::id));
        return out;
    }

    private static boolean milkShown(boolean priv) {
        return ModLockConfig.milkMode == ModLockConfig.MilkMode.PER_ITEM && (priv || ModLockConfig.milkPlayersChoose);
    }

    // ---------------- open ----------------

    /** Sends the configuration screen. Returns false (with a message) when there is nothing to choose. */
    public static boolean open(ServerPlayer p, InteractionHand hand, Kind kind) {
        boolean priv = privileged(p, kind);
        List<ModLockNetwork.ConfigEntry> entries = candidates(p, kind, priv);
        if (entries.isEmpty()) {
            p.sendOverlayMessage(Component.translatable("message.modlock.config.nothing"));
            return false;
        }
        int flags = 0;
        int max = 0;
        int def = 0;
        List<String> pre = List.of();
        switch (kind) {
            case KEY -> {
                KeyData d = p.getItemInHand(hand).get(ModLockItems.KEY.get());
                if (d != null) pre = d.entries();
            }
            case SCROLL, LICENSE -> {
                boolean permOk = priv || (kind == Kind.SCROLL ? ModLockConfig.scrollPermanentByNonOp : ModLockConfig.licensePermanentByNonOp);
                flags = FLAG_TEMPORARY | FLAG_DURATION | (permOk ? FLAG_PERMANENT : 0) | (milkShown(priv) ? FLAG_MILK : 0);
                max = Math.min(ModLockConfig.maxKnowledgeMinutes,
                        kind == Kind.SCROLL ? ModLockConfig.scrollMaxMinutes : ModLockConfig.licenseMaxMinutes);
                def = max;
            }
            case TOME -> {
                flags = (priv && ModLockConfig.tomeOpCanSetDuration ? FLAG_DURATION : 0) | (milkShown(priv) ? FLAG_MILK : 0);
                max = ModLockConfig.tomeMinutes;
                def = max;
            }
        }
        ModLockNetwork.openConfig(p, new ModLockNetwork.OpenConfigPayload(kind.name(), hand == InteractionHand.MAIN_HAND,
                entries, pre, flags, Math.max(1, max), Math.max(1, def), ModLockConfig.milkDefaultCurable));
        return true;
    }

    // ---------------- answer ----------------

    private static void invalid(ServerPlayer p) {
        p.sendOverlayMessage(Component.translatable("message.modlock.config.invalid"));
    }

    /** Validates the client's answer against the item in hand and the player's rights, then writes the component. */
    public static boolean answer(ServerPlayer p, ModLockNetwork.ConfigAnswerPayload a) {
        Kind kind = Kind.byName(a.kind());
        if (kind == null) return false;
        InteractionHand hand = a.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = p.getItemInHand(hand);
        if (!stack.is(kind.item())) return false;
        boolean priv = privileged(p, kind);
        String creator = p.getGameProfile().name();
        String creatorId = p.getUUID().toString();
        switch (kind) {
            case KEY -> {
                if (!priv) return false;
                if (stack.has(ModLockItems.KEY.get()) && !ModLockConfig.keyAllowOpReconfigure) return false;
                Set<String> allowed = new HashSet<>();
                for (var e : keyCandidates()) allowed.add(e.id());
                Set<String> picked = new LinkedHashSet<>(a.selected());
                KeyData.Mode mode = KeyData.Mode.parse(a.mode()).result().orElse(null);
                if (picked.isEmpty() || !allowed.containsAll(picked) || mode == null || a.amount() < 1 || a.amount() > 64
                        || a.name().length() > MAX_NAME) {
                    invalid(p);
                    return false;
                }
                stack.set(ModLockItems.KEY.get(), new KeyData(List.copyOf(picked), a.amount(), mode));
                if (!a.name().isBlank()) stack.set(DataComponents.CUSTOM_NAME, Component.literal(a.name().strip()));
            }
            case SCROLL, LICENSE, TOME -> {
                var type = kind == Kind.SCROLL ? ModLockItems.SCROLL.get()
                        : kind == Kind.LICENSE ? ModLockItems.LICENSE.get() : ModLockItems.TOME.get();
                if (stack.has(type) || a.selected().size() != 1) {
                    invalid(p);
                    return false;
                }
                String target = a.selected().get(0);
                TeachData data = teachData(p, kind, priv, target, a, creator, creatorId);
                if (data == null) {
                    invalid(p);
                    return false;
                }
                stack.set(type, data);
            }
        }
        p.sendOverlayMessage(Component.translatable("message.modlock.config.done"));
        return true;
    }

    /** Builds the component for a scroll/license/tome answer, or null when invalid. Public for GameTests. */
    @Nullable
    public static TeachData teachData(ServerPlayer p, Kind kind, boolean priv, String target,
                                      ModLockNetwork.ConfigAnswerPayload a, String creator, String creatorId) {
        boolean milk = milkShown(priv);
        boolean curable = milk ? a.curable() : ModLockConfig.milkDefaultCurable;
        String opText = priv ? ModLockConfig.tomeOpText : "";
        switch (kind) {
            case SCROLL, LICENSE -> {
                Identifier id = Identifier.tryParse(target);
                if (id == null) return null;
                boolean permanent = "PERMANENT".equals(a.mode());
                if (!permanent && !"TEMPORARY".equals(a.mode())) return null;
                int max = Math.min(ModLockConfig.maxKnowledgeMinutes,
                        kind == Kind.SCROLL ? ModLockConfig.scrollMaxMinutes : ModLockConfig.licenseMaxMinutes);
                if (!permanent && (a.minutes() < 1 || a.minutes() > max)) return null;
                if (kind == Kind.SCROLL) {
                    if (!BuiltInRegistries.ITEM.containsKey(id)) return null;
                    if (permanent && !priv && !ModLockConfig.scrollPermanentByNonOp) return null;
                    if (!canScroll(p, BuiltInRegistries.ITEM.getValue(id), priv, permanent)) return null;
                } else {
                    if (!BuiltInRegistries.BLOCK.containsKey(id)) return null;
                    if (!canLicense(p, BuiltInRegistries.BLOCK.getValue(id), priv, permanent)) return null;
                }
                return new TeachData(id.toString(), permanent, permanent ? 1 : a.minutes(), curable, creator, creatorId, priv, opText);
            }
            case TOME -> {
                if (!canTome(p, target, priv)) return null;
                int minutes = ModLockConfig.tomeMinutes;
                if (priv && ModLockConfig.tomeOpCanSetDuration) {
                    if (a.minutes() < 1 || a.minutes() > ModLockConfig.tomeMinutes) return null;
                    minutes = a.minutes();
                }
                return new TeachData(target, false, minutes, curable, creator, creatorId, priv, opText);
            }
            default -> {
                return null;
            }
        }
    }
}
