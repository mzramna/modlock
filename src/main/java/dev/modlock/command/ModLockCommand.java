package dev.modlock.command;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import dev.modlock.core.ModLockSavedData;
import dev.modlock.core.ModNames;
import dev.modlock.core.RecipeIndex;
import dev.modlock.net.ModLockNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * /modlock grant|revoke &lt;players&gt; mod|item|machine &lt;id&gt;
 * /modlock list [player]
 * /modlock reload
 */
public final class ModLockCommand {
    private ModLockCommand() {}

    private static final SuggestionProvider<CommandSourceStack> MODS = (ctx, b) ->
            SharedSuggestionProvider.suggest(ModList.get().getMods().stream().map(m -> m.getModId()), b);
    private static final SuggestionProvider<CommandSourceStack> ITEMS = (ctx, b) ->
            SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> BLOCKS = (ctx, b) ->
            SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("modlock")
                .requires(s -> s.hasPermission(2))
                .then(change("grant", true))
                .then(change("revoke", false))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> list(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("reload").executes(ModLockCommand::reload)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> change(String name, boolean grant) {
        return Commands.literal(name).then(Commands.argument("players", EntityArgument.players())
                .then(Commands.literal("mod").then(Commands.argument("modid", StringArgumentType.word())
                        .suggests(MODS)
                        .executes(ctx -> apply(ctx, grant, "mod", StringArgumentType.getString(ctx, "modid"),
                                d -> d.mods, Function.identity()))))
                .then(Commands.literal("item").then(Commands.argument("item", ResourceLocationArgument.id())
                        .suggests(ITEMS)
                        .executes(ctx -> apply(ctx, grant, "item", ResourceLocationArgument.getId(ctx, "item").toString(),
                                d -> d.items, ResourceLocation::parse))))
                .then(Commands.literal("machine").then(Commands.argument("block", ResourceLocationArgument.id())
                        .suggests(BLOCKS)
                        .executes(ctx -> apply(ctx, grant, "machine", ResourceLocationArgument.getId(ctx, "block").toString(),
                                d -> d.machines, ResourceLocation::parse)))));
    }

    private static <T> int apply(CommandContext<CommandSourceStack> ctx, boolean grant, String kind, String id,
                                 Function<ModLockSavedData.PlayerData, Set<T>> field, Function<String, T> parse)
            throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        ModLockSavedData data = ModLockSavedData.get(ctx.getSource().getServer());
        T value = parse.apply(id);
        int changed = 0;
        for (ServerPlayer p : players) {
            Set<T> set = field.apply(data.player(p.getUUID()));
            if (grant ? set.add(value) : set.remove(value)) changed++;
            ModLockNetwork.sync(p);
        }
        data.setDirty();
        Component label = "mod".equals(kind) ? ModNames.component(id) : Component.literal(id);
        int n = changed;
        ctx.getSource().sendSuccess(() -> Component.translatable(
                grant ? "command.modlock.granted" : "command.modlock.revoked", label, n), true);
        return changed;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        ModLockSavedData.PlayerData d = ModLockSavedData.get(ctx.getSource().getServer()).peek(player.getUUID());
        String mods = d == null || d.mods.isEmpty() ? "-" : String.join(", ", d.mods.stream().sorted().toList());
        String items = d == null || d.items.isEmpty() ? "-" : String.join(", ", d.items.stream().map(Object::toString).sorted().toList());
        String machines = d == null || d.machines.isEmpty() ? "-" : String.join(", ", d.machines.stream().map(Object::toString).sorted().toList());
        ctx.getSource().sendSuccess(() -> Component.translatable("command.modlock.list",
                player.getDisplayName(), mods, items, machines), false);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        RecipeIndex.invalidate();
        ModLockNetwork.syncAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("command.modlock.reloaded"), true);
        return 1;
    }
}
