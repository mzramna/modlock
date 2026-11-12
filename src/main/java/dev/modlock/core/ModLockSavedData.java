package dev.modlock.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-wide store of every player's permanent unlocks. Lives in the overworld data folder. */
public class ModLockSavedData extends SavedData {
    private static final String NAME = "modlock";

    public static final class PlayerData {
        public final Set<String> mods = new HashSet<>();
        public final Set<ResourceLocation> items = new HashSet<>();
        public final Set<ResourceLocation> machines = new HashSet<>();
        public boolean madeInitialChoice;
    }

    private final Map<UUID, PlayerData> players = new HashMap<>();
    /** mod id -> owner, used by the exclusivity option (stage 2). */
    private final Map<String, UUID> owners = new HashMap<>();

    public static ModLockSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ModLockSavedData::new, ModLockSavedData::load, null), NAME);
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

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
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
        owners.forEach((mod, id) -> ownersTag.putUUID(mod, id));
        tag.put("owners", ownersTag);
        return tag;
    }

    public static ModLockSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ModLockSavedData data = new ModLockSavedData();
        CompoundTag playersTag = tag.getCompound("players");
        for (String key : playersTag.getAllKeys()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompoundTag p = playersTag.getCompound(key);
            PlayerData d = data.player(id);
            p.getList("mods", Tag.TAG_STRING).forEach(t -> d.mods.add(t.getAsString()));
            p.getList("items", Tag.TAG_STRING).forEach(t -> addRl(d.items, t.getAsString()));
            p.getList("machines", Tag.TAG_STRING).forEach(t -> addRl(d.machines, t.getAsString()));
            d.madeInitialChoice = p.getBoolean("madeInitialChoice");
        }
        CompoundTag ownersTag = tag.getCompound("owners");
        for (String mod : ownersTag.getAllKeys()) {
            data.owners.put(mod, ownersTag.getUUID(mod));
        }
        return data;
    }

    private static void addRl(Set<ResourceLocation> set, String s) {
        ResourceLocation rl = ResourceLocation.tryParse(s);
        if (rl != null) set.add(rl);
    }
}
