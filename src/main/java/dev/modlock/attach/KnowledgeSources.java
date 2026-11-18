package dev.modlock.attach;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Player attachment (sent to the owning client only, see ModLockNetwork.sync): where each active knowledge
 * effect came from.
 * Keyed by effect id. The effects themselves are the source of truth for "active"; stale entries are
 * cleaned when an effect ends.
 */
public record KnowledgeSources(Map<String, Source> map) {
    public static final KnowledgeSources EMPTY = new KnowledgeSources(Map.of());

    public record Source(Component text, boolean curable) {
        public static final Codec<Source> CODEC = RecordCodecBuilder.create(i -> i.group(
                ComponentSerialization.CODEC.fieldOf("text").forGetter(Source::text),
                Codec.BOOL.fieldOf("curable").forGetter(Source::curable)).apply(i, Source::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Source> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC, Source::text,
                ByteBufCodecs.BOOL, Source::curable,
                Source::new);
    }

    public static final Codec<KnowledgeSources> CODEC =
            Codec.unboundedMap(Codec.STRING, Source.CODEC).xmap(KnowledgeSources::new, KnowledgeSources::map);
    public static final StreamCodec<RegistryFriendlyByteBuf, KnowledgeSources> STREAM_CODEC =
            ByteBufCodecs.<RegistryFriendlyByteBuf, String, Source, Map<String, Source>>map(HashMap::new,
                    ByteBufCodecs.STRING_UTF8, Source.STREAM_CODEC).map(KnowledgeSources::new, KnowledgeSources::map);

    public KnowledgeSources with(String id, Source s) {
        Map<String, Source> m = new HashMap<>(map);
        m.put(id, s);
        return new KnowledgeSources(Map.copyOf(m));
    }

    public KnowledgeSources without(String id) {
        if (!map.containsKey(id)) return this;
        Map<String, Source> m = new HashMap<>(map);
        m.remove(id);
        return new KnowledgeSources(Map.copyOf(m));
    }
}
