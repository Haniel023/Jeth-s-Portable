package com.jethsportable.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Carried by a broken Core's item so that placing it again restores the same base. */
public record CoreData(UUID baseId, int level, int links) {
	public static final Codec<CoreData> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("base").forGetter(CoreData::baseId),
		Codec.INT.optionalFieldOf("level", 1).forGetter(CoreData::level),
		Codec.INT.optionalFieldOf("links", 0).forGetter(CoreData::links)
	).apply(i, CoreData::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, CoreData> STREAM_CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC, CoreData::baseId,
		ByteBufCodecs.VAR_INT, CoreData::level,
		ByteBufCodecs.VAR_INT, CoreData::links,
		CoreData::new
	);
}
