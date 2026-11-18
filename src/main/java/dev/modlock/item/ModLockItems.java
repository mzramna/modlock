package dev.modlock.item;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import dev.modlock.ModLock;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModLockItems {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ModLock.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ModLock.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ModLock.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<KeyData>> KEY =
            COMPONENTS.registerComponentType("key", b -> b.persistent(KeyData.CODEC).networkSynchronized(KeyData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TeachData>> SCROLL =
            COMPONENTS.registerComponentType("scroll", b -> b.persistent(TeachData.CODEC).networkSynchronized(TeachData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TeachData>> TOME =
            COMPONENTS.registerComponentType("tome", b -> b.persistent(TeachData.CODEC).networkSynchronized(TeachData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TeachData>> LICENSE =
            COMPONENTS.registerComponentType("license", b -> b.persistent(TeachData.CODEC).networkSynchronized(TeachData.STREAM_CODEC));
    /** Pure Tome bound to a bundle id. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> BUNDLE =
            COMPONENTS.registerComponentType("bundle", b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    public static final DeferredItem<ModKeyItem> MOD_KEY = ITEMS.registerItem("mod_key", ModKeyItem::new,
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
    public static final DeferredItem<RecipeScrollItem> RECIPE_SCROLL = ITEMS.registerItem("recipe_scroll", RecipeScrollItem::new,
            new Item.Properties().stacksTo(16));
    public static final DeferredItem<MundaneTomeItem> MUNDANE_TOME = ITEMS.registerItem("mundane_tome", MundaneTomeItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<PureTomeItem> PURE_TOME = ITEMS.registerItem("pure_tome", PureTomeItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<MachineLicenseItem> MACHINE_LICENSE = ITEMS.registerItem("machine_license", MachineLicenseItem::new,
            new Item.Properties().stacksTo(16));

    public static final Supplier<CreativeModeTab> TAB = TABS.register("modlock", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.modlock"))
            .icon(() -> new ItemStack(MOD_KEY.get()))
            .displayItems((params, out) -> {
                for (ItemStack s : List.of(new ItemStack(MOD_KEY.get()), new ItemStack(RECIPE_SCROLL.get()),
                        new ItemStack(MUNDANE_TOME.get()), new ItemStack(PURE_TOME.get()), new ItemStack(MACHINE_LICENSE.get()))) {
                    out.accept(s);
                }
                for (ItemStack s : examples()) out.accept(s);
            })
            .build());

    /** Pre-configured examples using vanilla content only. */
    private static List<ItemStack> examples() {
        ItemStack scroll = new ItemStack(RECIPE_SCROLL.get());
        scroll.set(SCROLL.get(), new TeachData("minecraft:diamond_pickaxe", false, 40, false, "", "", true, ""));
        ItemStack license = new ItemStack(MACHINE_LICENSE.get());
        license.set(LICENSE.get(), new TeachData("minecraft:furnace", true, 60, false, "", "", true, ""));
        ItemStack key = new ItemStack(MOD_KEY.get());
        key.set(KEY.get(), new KeyData(List.of("minecraft"), 1, KeyData.Mode.ALL));
        return List.of(key, scroll, license);
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    private ModLockItems() {}
}
