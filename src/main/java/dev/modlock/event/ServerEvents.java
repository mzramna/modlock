package dev.modlock.event;

import dev.modlock.ModLock;
import dev.modlock.config.ModLockConfig;
import dev.modlock.core.AccessRules;
import dev.modlock.core.MachineTracker;
import dev.modlock.core.ModNames;
import dev.modlock.core.RecipeIndex;
import dev.modlock.net.ModLockNetwork;
import dev.modlock.command.ModLockCommand;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.util.TriState;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = ModLock.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        ModLockCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) ModLockNetwork.sync(sp);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MachineTracker.forget(event.getEntity().getUUID());
    }

    /** /reload: recipes changed. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        RecipeIndex.invalidate();
        if (event.getPlayer() != null) ModLockNetwork.sync(event.getPlayer());
        else ModLockNetwork.syncAll(event.getPlayerList().getServer());
    }

    // ---------- machine tracking ----------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || player instanceof FakePlayer) return;
        BlockState state = player.level().getBlockState(event.getPos());

        if (ModLockConfig.strictUsage && !event.isCanceled()) {
            Block block = state.getBlock();
            if (!AccessRules.canUseMachine(player, block)) {
                event.setUseBlock(TriState.FALSE);
                denied(player, BuiltInRegistries.BLOCK.getKey(block).getNamespace());
            }
            ItemStack held = event.getItemStack();
            if (!held.isEmpty() && !AccessRules.canUseNamespace(player, ns(held))) {
                event.setUseItem(TriState.FALSE);
                denied(player, ns(held));
            }
        }
        if (!event.isCanceled()) {
            MachineTracker.onRightClickBlock(player.getUUID(), event.getPos(), state.getBlock(), player.level().getGameTime());
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || player instanceof FakePlayer) return;
        MachineTracker.onMenuOpen(player.getUUID(), event.getContainer(), player.level().getGameTime());
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        MachineTracker.onMenuClose(event.getContainer());
    }

    // ---------- optional strict mode ----------

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!ModLockConfig.strictUsage || event.getLevel().isClientSide()) return;
        ItemStack held = event.getItemStack();
        if (!held.isEmpty() && !AccessRules.canUseNamespace(event.getEntity(), ns(held))) {
            event.setCanceled(true);
            denied(event.getEntity(), ns(held));
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!ModLockConfig.strictUsage || event.getLevel().isClientSide()) return;
        String ns = BuiltInRegistries.ENTITY_TYPE.getKey(event.getTarget().getType()).getNamespace();
        if (!AccessRules.canUseNamespace(event.getEntity(), ns)) {
            event.setCanceled(true);
            denied(event.getEntity(), ns);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!ModLockConfig.strictUsage || !(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        Block block = event.getPlacedBlock().getBlock();
        if (!AccessRules.canUseMachine(player, block)) {
            event.setCanceled(true);
            denied(player, BuiltInRegistries.BLOCK.getKey(block).getNamespace());
        }
    }

    private static String ns(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi) {
            return BuiltInRegistries.BLOCK.getKey(bi.getBlock()).getNamespace();
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    }

    private static void denied(Player player, String namespace) {
        player.sendOverlayMessage(
                Component.translatable("message.modlock.usage_blocked", ModNames.component(namespace)));
    }
}
