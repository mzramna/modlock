package dev.modlock.item;

import java.util.function.Consumer;

import dev.modlock.core.ModNames;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Mundane Tome: a player lends the knowledge of a mod they own (temporary effect for the reader). */
public class MundaneTomeItem extends UnlockItem {
    public MundaneTomeItem(Properties props) {
        super(props);
    }

    @Override
    protected boolean configured(ItemStack stack) {
        return stack.has(ModLockItems.TOME.get());
    }

    @Override
    protected boolean serverUse(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        TeachData d = stack.get(ModLockItems.TOME.get());
        if (d == null) {
            ItemConfigs.open(player, hand, ItemConfigs.Kind.TOME);
            return false;
        }
        return Redemption.useTome(player, d);
    }

    /** "Shared by X", or the admin text (translation key or plain text) for OP-made tomes. */
    static Component sharedBy(TeachData d) {
        if (d.byOp()) {
            String t = d.opText().isEmpty() ? "tooltip.modlock.mundane_tome.admin" : d.opText();
            return Component.translatableWithFallback(t, t);
        }
        return Component.translatable("tooltip.modlock.tome.shared_by", d.creator());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out,
                                TooltipFlag flag) {
        TeachData d = stack.get(ModLockItems.TOME.get());
        if (d == null) {
            unconfigured(out);
            return;
        }
        out.accept(Component.translatable("tooltip.modlock.tome.knowledge", ModNames.component(d.target()))
                .withStyle(ChatFormatting.GOLD));
        out.accept(sharedBy(d).copy().withStyle(ChatFormatting.DARK_GRAY));
        durationLine(out, d);
    }
}
