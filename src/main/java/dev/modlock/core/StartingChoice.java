package dev.modlock.core;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import dev.modlock.config.ModLockConfig;
import dev.modlock.net.ModLockNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;

/**
 * The starting choice: on first join (or /modlock choose) the player picks N entries among the bundles
 * their classes can use and, optionally, single mods. The server builds the options, validates the answer
 * and grants it.
 */
public final class StartingChoice {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int MAX_IMAGE_BYTES = 256 * 1024;

    /** One selectable entry. key is "bundle:&lt;id&gt;" or "mod:&lt;id&gt;". */
    public record Option(String key, String name, String description, String icon, String image,
                         byte[] imageBytes, List<String> members, String takenBy) {}

    private StartingChoice() {}

    public static int count() {
        return Math.max(0, ModLockConfig.startingCount);
    }

    /** Entries the player could pick right now (taken ones are included but marked). */
    public static List<Option> options(MinecraftServer server, UUID player) {
        ModLockSavedData data = ModLockSavedData.get(server);
        ModLockSavedData.PlayerData d = data.peek(player);
        Set<String> classes = PlayerClasses.of(server, player);
        List<Option> out = new ArrayList<>();

        for (ModBundles.Bundle b : ModBundles.all()) {
            if (!PlayerClasses.canUse(classes, b)) continue;
            if (d != null && d.bundles.contains(b.id())) continue;
            String key = "bundle:" + b.id();
            List<String> names = b.members().stream().map(ModNames::displayName).sorted().toList();
            out.add(new Option(key, b.name(), b.description(), b.icon() == null ? "" : b.icon(),
                    b.imageFile() == null && b.image() != null ? b.image() : "",
                    imageBytes(b), names, takenBy(data, key, player)));
        }

        if (ModLockConfig.allowSingleMods) {
            for (String mod : choosableMods()) {
                if (AccessRules.hasModAccess(server, player, mod)) continue;
                if (AccessRules.isClosedDenied(server, player, mod)) continue;
                String key = "mod:" + mod;
                String desc = ModList.get().getModContainerById(mod)
                        .map(c -> c.getModInfo().getDescription()).orElse("");
                out.add(new Option(key, ModNames.displayName(mod), firstLine(desc), "", "", new byte[0],
                        List.of(), takenBy(data, key, player)));
            }
        }
        return out;
    }

    private static Set<String> choosableMods() {
        if (!ModLockConfig.choosableMods.isEmpty()) return new TreeSet<>(ModLockConfig.choosableMods);
        Set<String> out = new TreeSet<>();
        for (IModInfo info : ModList.get().getMods()) {
            String id = info.getModId();
            if (id.equals("minecraft") || id.equals("neoforge") || id.equals("modlock")) continue;
            if (AccessRules.isModLocked(id)) out.add(id);
        }
        return out;
    }

    private static String takenBy(ModLockSavedData data, String key, UUID player) {
        if (!ModLockConfig.exclusive) return "";
        UUID owner = data.owners().get(key);
        if (owner == null || owner.equals(player)) return "";
        return data.ownerNames().getOrDefault(key, owner.toString().substring(0, 8));
    }

    private static byte[] imageBytes(ModBundles.Bundle b) {
        if (b.imageFile() == null) return new byte[0];
        try {
            long size = Files.size(b.imageFile());
            if (size > MAX_IMAGE_BYTES) {
                LOG.warn("[ModLock] Bundle image {} is larger than 256 KiB and will not be sent", b.imageFile());
                return new byte[0];
            }
            return Files.readAllBytes(b.imageFile());
        } catch (IOException e) {
            LOG.warn("[ModLock] Could not read bundle image {}", b.imageFile(), e);
            return new byte[0];
        }
    }

    private static String firstLine(String s) {
        s = s.strip();
        int nl = s.indexOf('\n');
        return nl >= 0 ? s.substring(0, nl) : s;
    }

    // ---------------- flow ----------------

    /** Sends the selection screen if the player still has a choice to make. Returns true if sent. */
    public static boolean offer(ServerPlayer player, boolean force) {
        MinecraftServer server = player.getServer();
        if (server == null || count() == 0) return false;
        ModLockSavedData.PlayerData d = ModLockSavedData.get(server).player(player.getUUID());
        if (d.madeInitialChoice && !force) return false;
        List<Option> opts = options(server, player.getUUID());
        long available = opts.stream().filter(o -> o.takenBy().isEmpty()).count();
        if (available == 0) return false;
        ModLockNetwork.openChoice(player, (int) Math.min(count(), available), opts);
        return true;
    }

    /** Validates and applies the player's answer. */
    public static void answer(ServerPlayer player, List<String> keys) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ModLockSavedData data = ModLockSavedData.get(server);
        ModLockSavedData.PlayerData d = data.player(player.getUUID());
        if (d.madeInitialChoice) return;

        Map<String, Option> opts = new LinkedHashMap<>();
        for (Option o : options(server, player.getUUID())) opts.put(o.key(), o);
        long available = opts.values().stream().filter(o -> o.takenBy().isEmpty()).count();
        int needed = (int) Math.min(count(), available);
        Set<String> unique = new HashSet<>(keys);
        boolean valid = unique.size() == keys.size() && keys.size() == needed
                && keys.stream().allMatch(k -> opts.containsKey(k) && opts.get(k).takenBy().isEmpty());
        if (!valid) {
            player.sendSystemMessage(Component.translatable("message.modlock.choice_invalid"));
            offer(player, false);
            return;
        }
        for (String k : keys) {
            String id = k.substring(k.indexOf(':') + 1);
            if (k.startsWith("bundle:")) d.bundles.add(id);
            else d.mods.add(id);
            if (ModLockConfig.exclusive) {
                data.owners().put(k, player.getUUID());
                data.ownerNames().put(k, player.getGameProfile().getName());
            }
            if (ModLockConfig.announceChoice) {
                server.getPlayerList().broadcastSystemMessage(Component.translatable(
                        "message.modlock.chose", player.getDisplayName(), opts.get(k).name()), false);
            }
        }
        d.madeInitialChoice = true;
        data.setDirty();
        ModLockNetwork.sync(player);
    }
}
