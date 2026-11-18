package dev.modlock.item;

import java.util.List;

import dev.modlock.core.ModNames;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Common behavior: the server decides everything on use; the stack shrinks only after a successful grant. */
public abstract class UnlockItem extends Item {
    protected UnlockItem(Properties props) {
        super(props);
    }

    protected abstract boolean configured(ItemStack stack);

    /** Server-side use. Returns true when one item must be used up. */
    protected abstract boolean serverUse(ServerPlayer player, InteractionHand hand, ItemStack stack);

    @Override
    public boolean isFoil(ItemStack stack) {
        return configured(stack) || super.isFoil(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp && serverUse(sp, hand, stack)) {
            stack.consume(1, sp);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // ---------------- tooltip helpers ----------------

    static Component itemName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return Component.literal(id);
        return BuiltInRegistries.ITEM.get(rl).getDescription();
    }

    static Component blockName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) return Component.literal(id);
        return BuiltInRegistries.BLOCK.get(rl).getName();
    }

    static Component modOf(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return ModNames.component(rl == null ? id : rl.getNamespace());
    }

    static void durationLine(List<Component> out, TeachData d) {
        out.add(d.permanent()
                ? Component.translatable("tooltip.modlock.permanent").withStyle(ChatFormatting.GREEN)
                : Component.translatable("tooltip.modlock.minutes", d.minutes()).withStyle(ChatFormatting.AQUA));
        if (!d.permanent()) {
            out.add(Component.translatable(d.curable() ? "tooltip.modlock.milk.curable" : "tooltip.modlock.milk.immune")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    static void unconfigured(List<Component> out) {
        out.add(Component.translatable("tooltip.modlock.unconfigured").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
