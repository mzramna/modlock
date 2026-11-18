package dev.modlock.item;

import java.util.List;

import dev.modlock.config.ModLockConfig;
import dev.modlock.core.ModNames;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Mod Key: no recipe. Configured by an OP (mods/bundles, amount, ALL/CHOOSE/RANDOM), redeemed by anyone. */
public class ModKeyItem extends UnlockItem {
    public ModKeyItem(Properties props) {
        super(props);
    }

    @Override
    protected boolean configured(ItemStack stack) {
        return stack.has(ModLockItems.KEY.get());
    }

    @Override
    protected boolean serverUse(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        KeyData data = stack.get(ModLockItems.KEY.get());
        boolean priv = ItemConfigs.privileged(player, ItemConfigs.Kind.KEY);
        if (data == null) {
            if (priv) ItemConfigs.open(player, hand, ItemConfigs.Kind.KEY);
            else player.displayClientMessage(Component.translatable("message.modlock.key.unconfigured"), true);
            return false;
        }
        if (priv && ModLockConfig.keyAllowOpReconfigure && player.isShiftKeyDown()) {
            ItemConfigs.open(player, hand, ItemConfigs.Kind.KEY);
            return false;
        }
        return Redemption.useKey(player, hand, stack, data);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> out, TooltipFlag flag) {
        KeyData d = stack.get(ModLockItems.KEY.get());
        if (d == null) {
            unconfigured(out);
            return;
        }
        MutableComponent head = switch (d.mode()) {
            case ALL -> Component.translatable("tooltip.modlock.key.all");
            case RANDOM -> Component.translatable("tooltip.modlock.key.random", d.amount());
            case CHOOSE -> Component.translatable("tooltip.modlock.key.choose", d.amount());
        };
        out.add(head.withStyle(ChatFormatting.GOLD));
        int shown = 0;
        for (String e : d.entries()) {
            if (shown++ == 8) {
                out.add(Component.literal("  ... +" + (d.entries().size() - 8)).withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            MutableComponent line = e.startsWith("bundle:")
                    ? Component.translatable("tooltip.modlock.key.bundle", e.substring(7))
                    : ModNames.component(e).copy();
            out.add(Component.literal("  • ").append(line).withStyle(ChatFormatting.GRAY));
        }
    }
}
