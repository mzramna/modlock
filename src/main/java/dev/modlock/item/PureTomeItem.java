package dev.modlock.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Pure Tome: no recipe (loot, quests, admins). Knowledge of a mod of the player's choice, or of a bound bundle. */
public class PureTomeItem extends UnlockItem {
    public PureTomeItem(Properties props) {
        super(props);
    }

    @Override
    protected boolean configured(ItemStack stack) {
        return true;
    }

    @Override
    protected boolean serverUse(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        return Redemption.usePureTome(player, hand, stack);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out,
                                TooltipFlag flag) {
        String bundle = stack.get(ModLockItems.BUNDLE.get());
        out.accept((bundle == null
                ? Component.translatable("tooltip.modlock.pure.any")
                : Component.translatable("tooltip.modlock.pure.bundle", bundle)).withStyle(ChatFormatting.AQUA));
        out.accept(Component.translatable("tooltip.modlock.pure.desc").withStyle(ChatFormatting.GRAY));
    }
}
