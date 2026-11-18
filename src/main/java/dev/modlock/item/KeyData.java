package dev.modlock.item;

import java.util.List;
import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data component of a configured (locked) Mod Key. entries are mod ids or "bundle:&lt;id&gt;".
 * The component is only present once the key was locked, so presence == locked.
 */
public record KeyData(List<String> entries, int amount, Mode mode) {
    public enum Mode {
        ALL, CHOOSE, RANDOM;

        public static DataResult<Mode> parse(String s) {
            try {
                return DataResult.success(valueOf(s.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "Unknown key mode " + s);
            }
        }
    }

    public static final Codec<Mode> MODE_CODEC = Codec.STRING.comapFlatMap(Mode::parse, Mode::name);

    public static final Codec<KeyData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("entries").forGetter(KeyData::entries),
            Codec.intRange(1, 64).optionalFieldOf("amount", 1).forGetter(KeyData::amount),
            MODE_CODEC.optionalFieldOf("mode", Mode.ALL).forGetter(KeyData::mode)).apply(i, KeyData::new));

    public static final StreamCodec<ByteBuf, KeyData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), KeyData::entries,
            ByteBufCodecs.VAR_INT, KeyData::amount,
            ByteBufCodecs.STRING_UTF8.map(s -> Mode.parse(s).result().orElse(Mode.ALL), Mode::name), KeyData::mode,
            KeyData::new);

    public KeyData {
        entries = List.copyOf(entries);
    }
}
