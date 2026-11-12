package dev.modlock.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;

import dev.modlock.ModLock;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-wide store of every player's permanent unlocks. Lives in the overworld data folder. */
public class ModLockSavedData extends SavedData {
    /**
     * Stored as data/modlock/modlock.dat. The NBT layout is the same as on 1.21.1, it is just
     * wrapped in a passthrough codec because 1.21.5+ saved data is codec based.
     */
    private static final Codec<ModLockSavedData> CODEC =
            CompoundTag.CODEC.xmap(ModLockSavedData::load, ModLockSavedData::save);
    public static final SavedDataType<ModLockSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(ModLock.MODID, "modlock"), ModLockSavedData::new, CODEC);

    public static final class PlayerData {
        public final Set<String> mods = new HashSet<>();
        public final Set<Identifier> items = new HashSet<>();
        public final Set<Identifier> machines = new HashSet<>();
        public boolean madeInitialChoice;
    }

    private final Map<UUID, PlayerData> players = new HashMap<>();
    /** mod id -> owner, used by the exclusivity option (stage 2). */
    private final Map<String, UUID> owners = new HashMap<>();

    public static ModLockSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public PlayerData player(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerData());
    }

    /** Read-only lookup that does not create an entry. */
    public PlayerData peek(UUID id) {
        return players.get(id);
    }

    public Map<String, UUID> owners() {
        return owners;
    }

    // ---- persistence ----

    private static ListTag strings(Iterable<?> values) {
        ListTag list = new ListTag();
        for (Object v : values) list.add(StringTag.valueOf(v.toString()));
        return list;
    }

    private CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag playersTag = new CompoundTag();
        players.forEach((id, d) -> {
            CompoundTag p = new CompoundTag();
            p.put("mods", strings(d.mods));
            p.put("items", strings(d.items));
            p.put("machines", strings(d.machines));
            p.putBoolean("madeInitialChoice", d.madeInitialChoice);
            playersTag.put(id.toString(), p);
        });
        tag.put("players", playersTag);

        CompoundTag ownersTag = new CompoundTag();
        owners.forEach((mod, id) -> ownersTag.store(mod, UUIDUtil.CODEC, id));
        tag.put("owners", ownersTag);
        return tag;
    }

    private static ModLockSavedData load(CompoundTag tag) {
        ModLockSavedData data = new ModLockSavedData();
        CompoundTag playersTag = tag.getCompoundOrEmpty("players");
        for (String key : playersTag.keySet()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompoundTag p = playersTag.getCompoundOrEmpty(key);
            PlayerData d = data.player(id);
            forEachString(p.getListOrEmpty("mods"), d.mods::add);
            forEachString(p.getListOrEmpty("items"), v -> addRl(d.items, v));
            forEachString(p.getListOrEmpty("machines"), v -> addRl(d.machines, v));
            d.madeInitialChoice = p.getBooleanOr("madeInitialChoice", false);
        }
        CompoundTag ownersTag = tag.getCompoundOrEmpty("owners");
        for (String mod : ownersTag.keySet()) {
            ownersTag.read(mod, UUIDUtil.CODEC).ifPresent(id -> data.owners.put(mod, id));
        }
        return data;
    }

    private static void forEachString(ListTag list, java.util.function.Consumer<String> action) {
        for (Tag t : list) t.asString().ifPresent(action);
    }

    private static void addRl(Set<Identifier> set, String s) {
        Identifier rl = Identifier.tryParse(s);
        if (rl != null) set.add(rl);
    }
}
