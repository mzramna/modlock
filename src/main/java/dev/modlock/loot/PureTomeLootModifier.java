package dev.modlock.loot;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.modlock.ModLock;
import dev.modlock.config.ModLockConfig;
import dev.modlock.core.ModBundles;
import dev.modlock.item.ModLockItems;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Adds a Pure Tome to loot chests ([loot] enabled / chance / tome_bundles), checked when the chest is rolled. */
public class PureTomeLootModifier extends LootModifier {
    public static final MapCodec<PureTomeLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, PureTomeLootModifier::new));

    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ModLock.MODID);
    public static final Supplier<MapCodec<PureTomeLootModifier>> PURE_TOME = SERIALIZERS.register("pure_tome", () -> CODEC);

    public PureTomeLootModifier(LootItemCondition[] conditions, int priority) {
        super(conditions, priority);
    }

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        if (!ModLockConfig.lootEnabled || !ctx.getQueriedLootTableId().getPath().startsWith("chests/")) return loot;
        if (ctx.getRandom().nextDouble() >= ModLockConfig.lootChance) return loot;
        ItemStack tome = new ItemStack(ModLockItems.PURE_TOME.get());
        if (!ModLockConfig.lootTomeBundles.isEmpty()) {
            List<String> ids = ModLockConfig.lootTomeBundles.stream().filter(id -> ModBundles.get(id) != null).sorted().toList();
            if (ids.isEmpty()) return loot;
            tome.set(ModLockItems.BUNDLE.get(), ids.get(ctx.getRandom().nextInt(ids.size())));
        }
        loot.add(tome);
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
