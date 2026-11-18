package dev.modlock.effect;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;

import dev.modlock.ModLock;
import dev.modlock.config.ModLockStartupConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.GameData;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;

/**
 * Dynamic registration of the knowledge effects.
 *
 * NeoForge unfreezes every registry, posts one RegisterEvent per registry (attributes, data components,
 * vanilla registries in vanilla order — mob effects before blocks/items — then modded registries) and only
 * freezes them after the last one. So:
 * - modlock:knowledge/&lt;mod&gt; is registered during the MOB_EFFECT event (only the mod list is needed);
 * - modlock:recipe/... and modlock:machine/... are written into the (still unfrozen) MOB_EFFECT registry during
 *   the LAST RegisterEvent, when every mod has registered its blocks, items and block entities.
 */
public final class KnowledgeEffects {
    private static final Logger LOG = LogUtils.getLogger();

    private static final Map<String, Holder<MobEffect>> KNOWLEDGE = new LinkedHashMap<>();
    private static final Map<Identifier, Holder<MobEffect>> RECIPE = new HashMap<>();
    private static final Map<Identifier, Holder<MobEffect>> MACHINE = new HashMap<>();

    @Nullable
    private static Identifier lastRegistry;

    private KnowledgeEffects() {}

    public static void onRegister(RegisterEvent event) {
        if (lastRegistry == null) {
            Identifier last = null;
            for (Identifier rl : GameData.getRegistrationOrder()) last = rl;
            lastRegistry = last;
        }
        if (event.getRegistryKey().equals(Registries.MOB_EFFECT)) registerKnowledge();
        if (event.getRegistryKey().identifier().equals(lastRegistry)) registerPerItem();
    }

    private static boolean skipped(String mod) {
        if (mod.equals("neoforge") || mod.equals(ModLock.MODID)) return true;
        return mod.equals("minecraft") && !ModLockStartupConfig.includeMinecraft();
    }

    private static void registerKnowledge() {
        long t0 = System.nanoTime();
        for (IModInfo info : ModList.get().getMods()) {
            String mod = info.getModId();
            if (skipped(mod) || KNOWLEDGE.containsKey(mod)) continue;
            Identifier id = Identifier.fromNamespaceAndPath(ModLock.MODID, "knowledge/" + sanitize(mod));
            if (BuiltInRegistries.MOB_EFFECT.containsKey(id)) continue;
            KNOWLEDGE.put(mod, Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id,
                    new KnowledgeEffect(KnowledgeEffect.Kind.KNOWLEDGE, mod, null)));
        }
        LOG.info("[ModLock] Registered {} knowledge effects in {} ms", KNOWLEDGE.size(), (System.nanoTime() - t0) / 1_000_000);
    }

    private static Set<String> recipeMods() {
        Set<String> out = new HashSet<>();
        for (String s : ModLockStartupConfig.RECIPE_EFFECT_MODS.get()) {
            if (s.equals("*")) {
                for (IModInfo info : ModList.get().getMods()) {
                    if (!skipped(info.getModId())) out.add(info.getModId());
                }
            } else if (!s.isBlank()) {
                out.add(s.toLowerCase(Locale.ROOT));
            }
        }
        if (ModLockStartupConfig.includeMinecraft()) out.add("minecraft");
        return out;
    }

    private static void registerPerItem() {
        long t0 = System.nanoTime();
        Set<String> mods = recipeMods();
        if (mods.isEmpty()) return;
        for (var e : BuiltInRegistries.ITEM.entrySet()) {
            Identifier itemId = e.getKey().identifier();
            Item item = e.getValue();
            if (!mods.contains(itemId.getNamespace()) || item == net.minecraft.world.item.Items.AIR) continue;
            Identifier id = Identifier.fromNamespaceAndPath(ModLock.MODID,
                    "recipe/" + sanitize(itemId.getNamespace()) + "/" + sanitize(itemId.getPath()));
            if (BuiltInRegistries.MOB_EFFECT.containsKey(id)) continue;
            RECIPE.put(itemId, Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id,
                    new KnowledgeEffect(KnowledgeEffect.Kind.RECIPE, itemId.getNamespace(), itemId)));
        }
        for (var e : BuiltInRegistries.BLOCK.entrySet()) {
            Identifier blockId = e.getKey().identifier();
            Block block = e.getValue();
            if (!mods.contains(blockId.getNamespace()) || !(block instanceof EntityBlock)) continue;
            Identifier id = Identifier.fromNamespaceAndPath(ModLock.MODID,
                    "machine/" + sanitize(blockId.getNamespace()) + "/" + sanitize(blockId.getPath()));
            if (BuiltInRegistries.MOB_EFFECT.containsKey(id)) continue;
            MACHINE.put(blockId, Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id,
                    new KnowledgeEffect(KnowledgeEffect.Kind.MACHINE, blockId.getNamespace(), blockId)));
        }
        LOG.info("[ModLock] Registered {} recipe and {} machine effects for {} mod(s) in {} ms",
                RECIPE.size(), MACHINE.size(), mods.size(), (System.nanoTime() - t0) / 1_000_000);
    }

    /** Lowercase; anything outside [a-z0-9/._-] becomes '_'. */
    static String sanitize(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            b.append(Identifier.validPathChar(c) ? c : '_');
        }
        return b.toString();
    }

    // ---------------- lookups ----------------

    @Nullable
    public static Holder<MobEffect> knowledge(String mod) {
        return KNOWLEDGE.get(mod);
    }

    @Nullable
    public static Holder<MobEffect> recipe(Identifier item) {
        return RECIPE.get(item);
    }

    @Nullable
    public static Holder<MobEffect> machine(Identifier block) {
        return MACHINE.get(block);
    }

    public static Map<String, Holder<MobEffect>> allKnowledge() {
        return Collections.unmodifiableMap(KNOWLEDGE);
    }

    public static int count() {
        return KNOWLEDGE.size() + RECIPE.size() + MACHINE.size();
    }

    @Nullable
    public static KnowledgeEffect of(Holder<MobEffect> holder) {
        return holder.value() instanceof KnowledgeEffect k ? k : null;
    }
}
