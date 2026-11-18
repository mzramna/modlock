package dev.modlock.item;

import java.util.function.Consumer;

import dev.modlock.core.ModNames;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp && serverUse(sp, hand, stack)) {
            stack.consume(1, sp);
        }
        // 1.21.1 sidedSuccess: the client swings the arm, the server only consumes
        return InteractionResult.SUCCESS;
    }

    // ---------------- tooltip helpers ----------------

    static Component itemName(String id) {
        Identifier rl = Identifier.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return Component.literal(id);
        return itemName(BuiltInRegistries.ITEM.getValue(rl));
    }

    /** 1.21.1 Item#getDescription: the default item name (26.1 keeps it in the ITEM_NAME component). */
    static Component itemName(net.minecraft.world.item.Item item) {
        return item.getName(item.getDefaultInstance());
    }

    static Component blockName(String id) {
        Identifier rl = Identifier.tryParse(id);
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) return Component.literal(id);
        return BuiltInRegistries.BLOCK.getValue(rl).getName();
    }

    static Component modOf(String id) {
        Identifier rl = Identifier.tryParse(id);
        return ModNames.component(rl == null ? id : rl.getNamespace());
    }

    static void durationLine(Consumer<Component> out, TeachData d) {
        out.accept(d.permanent()
                ? Component.translatable("tooltip.modlock.permanent").withStyle(ChatFormatting.GREEN)
                : Component.translatable("tooltip.modlock.minutes", d.minutes()).withStyle(ChatFormatting.AQUA));
        if (!d.permanent()) {
            out.accept(Component.translatable(d.curable() ? "tooltip.modlock.milk.curable" : "tooltip.modlock.milk.immune")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    static void unconfigured(Consumer<Component> out) {
        out.accept(Component.translatable("tooltip.modlock.unconfigured").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
