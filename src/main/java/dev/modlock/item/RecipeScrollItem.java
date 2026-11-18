package dev.modlock.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Recipe Scroll: teaches the recipes of one item, permanently or for a while. */
public class RecipeScrollItem extends UnlockItem {
    public RecipeScrollItem(Properties props) {
        super(props);
    }

    @Override
    protected boolean configured(ItemStack stack) {
        return stack.has(ModLockItems.SCROLL.get());
    }

    @Override
    protected boolean serverUse(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        TeachData d = stack.get(ModLockItems.SCROLL.get());
        if (d == null) {
            ItemConfigs.open(player, hand, ItemConfigs.Kind.SCROLL);
            return false;
        }
        return Redemption.useScroll(player, d);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> out, TooltipFlag flag) {
        TeachData d = stack.get(ModLockItems.SCROLL.get());
        if (d == null) {
            unconfigured(out);
            return;
        }
        out.add(Component.translatable("tooltip.modlock.scroll.teaches", itemName(d.target()), modOf(d.target()))
                .withStyle(ChatFormatting.GOLD));
        durationLine(out, d);
        if (!d.creator().isEmpty()) {
            out.add(Component.translatable("tooltip.modlock.created_by", d.creator()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
