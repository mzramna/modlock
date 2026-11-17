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

import dev.modlock.core.ModBundles;
import dev.modlock.core.ModLockSavedData;
import dev.modlock.core.PlayerClasses;
import dev.modlock.core.StartingChoice;
import dev.modlock.core.ModNames;
import dev.modlock.core.RecipeIndex;
import dev.modlock.net.ModLockNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * OP: /modlock grant|revoke &lt;players&gt; mod|item|machine|bundle &lt;id&gt;
 *     /modlock class add|remove &lt;players&gt; &lt;class&gt;
 *     /modlock bundles [id] | list [player] | resetchoice &lt;players&gt; | reload
 * Everyone: /modlock choose (reopens the starting choice while it is not confirmed)
 */
public final class ModLockCommand {
    private ModLockCommand() {}

    private static final SuggestionProvider<CommandSourceStack> MODS = (ctx, b) ->
            SharedSuggestionProvider.suggest(ModList.get().getMods().stream().map(m -> m.getModId()), b);
    private static final SuggestionProvider<CommandSourceStack> ITEMS = (ctx, b) ->
            SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> BLOCKS = (ctx, b) ->
            SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(), b);

    private static final SuggestionProvider<CommandSourceStack> BUNDLES = (ctx, b) ->
            SharedSuggestionProvider.suggest(ModBundles.all().stream().map(ModBundles.Bundle::id), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("modlock")
                .then(Commands.literal("choose").executes(ModLockCommand::choose))
                .then(change("grant", true).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                .then(change("revoke", false).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                .then(Commands.literal("class").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(classChange("add", true))
                        .then(classChange("remove", false)))
                .then(Commands.literal("bundles").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ModLockCommand::bundles)
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(BUNDLES)
                                .executes(ctx -> bundle(ctx, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("resetchoice").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ModLockCommand::resetChoice)))
                .then(Commands.literal("list").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> list(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> list(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ModLockCommand::reload)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> classChange(String name, boolean add) {
        return Commands.literal(name).then(Commands.argument("players", EntityArgument.players())
                .then(Commands.argument("class", StringArgumentType.word())
                        .executes(ctx -> {
                            String cls = PlayerClasses.normalize(StringArgumentType.getString(ctx, "class"));
                            ModLockSavedData data = ModLockSavedData.get(ctx.getSource().getServer());
                            int n = 0;
                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players")) {
                                var set = data.player(p.getUUID()).classes;
                                if (add ? set.add(cls) : set.remove(cls)) n++;
                                ModLockNetwork.sync(p);
                            }
                            data.setDirty();
                            int changed = n;
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    add ? "command.modlock.class_added" : "command.modlock.class_removed", cls, changed), true);
                            return changed;
                        })));
    }

    private static int choose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!StartingChoice.offer(p, false)) {
            ctx.getSource().sendFailure(Component.translatable("command.modlock.no_choice"));
            return 0;
        }
        return 1;
    }

    private static int resetChoice(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ModLockSavedData data = ModLockSavedData.get(ctx.getSource().getServer());
        int n = 0;
        for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players")) {
            data.player(p.getUUID()).madeInitialChoice = false;
            data.setDirty();
            StartingChoice.offer(p, false);
            n++;
        }
        int changed = n;
        ctx.getSource().sendSuccess(() -> Component.translatable("command.modlock.choice_reset", changed), true);
        return n;
    }

    private static int bundles(CommandContext<CommandSourceStack> ctx) {
        if (ModBundles.all().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.modlock.no_bundles",
                    ModBundles.directory().toString()), false);
            return 0;
        }
        for (ModBundles.Bundle b : ModBundles.all()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.modlock.bundle_line",
                    b.id(), b.name(), b.members().size(),
                    b.classes().isEmpty() ? "-" : String.join(", ", b.classes()),
                    b.closed() ? "\u2714" : "-"), false);
        }
        return ModBundles.all().size();
    }

    private static int bundle(CommandContext<CommandSourceStack> ctx, String id) {
        ModBundles.Bundle b = ModBundles.get(id);
        if (b == null) {
            ctx.getSource().sendFailure(Component.translatable("command.modlock.unknown_bundle", id));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.modlock.bundle_detail",
                b.name(), String.join(", ", b.members().stream().sorted().toList())), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> change(String name, boolean grant) {
        return Commands.literal(name).then(Commands.argument("players", EntityArgument.players())
                .then(Commands.literal("mod").then(Commands.argument("modid", StringArgumentType.word())
                        .suggests(MODS)
                        .executes(ctx -> apply(ctx, grant, "mod", StringArgumentType.getString(ctx, "modid"),
                                d -> d.mods, Function.identity()))))
                .then(Commands.literal("item").then(Commands.argument("item", IdentifierArgument.id())
                        .suggests(ITEMS)
                        .executes(ctx -> apply(ctx, grant, "item", IdentifierArgument.getId(ctx, "item").toString(),
                                d -> d.items, Identifier::parse))))
                .then(Commands.literal("machine").then(Commands.argument("block", IdentifierArgument.id())
                        .suggests(BLOCKS)
                        .executes(ctx -> apply(ctx, grant, "machine", IdentifierArgument.getId(ctx, "block").toString(),
                                d -> d.machines, Identifier::parse))))
                .then(Commands.literal("bundle").then(Commands.argument("bundle", StringArgumentType.word())
                        .suggests(BUNDLES)
                        .executes(ctx -> applyBundle(ctx, grant, StringArgumentType.getString(ctx, "bundle"))))));
    }

    private static int applyBundle(CommandContext<CommandSourceStack> ctx, boolean grant, String id)
            throws CommandSyntaxException {
        ModBundles.Bundle b = ModBundles.get(id);
        if (b == null && grant) {
            ctx.getSource().sendFailure(Component.translatable("command.modlock.unknown_bundle", id));
            return 0;
        }
        if (grant && b.restricted()) {
            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players")) {
                if (!PlayerClasses.canUse(PlayerClasses.of(ctx.getSource().getServer(), p.getUUID()), b)) {
                    ctx.getSource().sendSystemMessage(Component.translatable("command.modlock.wrong_class",
                            p.getDisplayName(), b.name(), String.join(", ", b.classes())));
                }
            }
        }
        return apply(ctx, grant, "bundle", id, d -> d.bundles, Function.identity());
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
        Component label = "mod".equals(kind) ? ModNames.component(id)
                : "bundle".equals(kind) && ModBundles.get(id) != null ? Component.literal(ModBundles.get(id).name())
                : Component.literal(id);
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
        String bundles = d == null || d.bundles.isEmpty() ? "-" : String.join(", ", d.bundles.stream().sorted().toList());
        var classSet = PlayerClasses.of(ctx.getSource().getServer(), player.getUUID());
        String classes = classSet.isEmpty() ? "-" : String.join(", ", classSet.stream().sorted().toList());
        ctx.getSource().sendSuccess(() -> Component.translatable("command.modlock.list",
                player.getDisplayName(), mods, items, machines, bundles, classes), false);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ModBundles.reload();
        RecipeIndex.invalidate();
        ModLockNetwork.syncAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("command.modlock.reloaded"), true);
        return 1;
    }
}
