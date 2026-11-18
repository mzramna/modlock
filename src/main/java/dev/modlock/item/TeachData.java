package dev.modlock.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data component of a configured Recipe Scroll (target = item id), Mundane Tome (target = mod id) or
 * Machine License (target = block id). curable is the creator's milk choice (the global milk mode still wins).
 * byOp: configured by an OP / creative player (restriction bypass, admin tooltip); opText: that tooltip.
 */
public record TeachData(String target, boolean permanent, int minutes, boolean curable, String creator,
                        String creatorId, boolean byOp, String opText) {
    public static final Codec<TeachData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("target").forGetter(TeachData::target),
            Codec.BOOL.optionalFieldOf("permanent", false).forGetter(TeachData::permanent),
            Codec.intRange(1, 540).optionalFieldOf("minutes", 60).forGetter(TeachData::minutes),
            Codec.BOOL.optionalFieldOf("curable", false).forGetter(TeachData::curable),
            Codec.STRING.optionalFieldOf("creator", "").forGetter(TeachData::creator),
            Codec.STRING.optionalFieldOf("creator_id", "").forGetter(TeachData::creatorId),
            Codec.BOOL.optionalFieldOf("by_op", false).forGetter(TeachData::byOp),
            Codec.STRING.optionalFieldOf("op_text", "").forGetter(TeachData::opText)).apply(i, TeachData::new));

    public static final StreamCodec<ByteBuf, TeachData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
