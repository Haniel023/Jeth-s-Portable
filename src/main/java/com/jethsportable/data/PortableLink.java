package com.jethsportable.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;

/** One linked block. The block itself stays vanilla; only this record marks it as part of a base. */
public final class PortableLink {
	public static final Codec<PortableLink> CODEC = RecordCodecBuilder.create(i -> i.group(
		GlobalPos.CODEC.fieldOf("pos").forGetter(PortableLink::pos),
		Codec.STRING.optionalFieldOf("name", "").forGetter(PortableLink::name),
		Codec.BOOL.optionalFieldOf("busy", false).forGetter(PortableLink::busy)
	).apply(i, PortableLink::new));

	private final GlobalPos pos;
	private String name;
	/** A machine last seen with work to do. Lets its chunk be reloaded after a restart. */
	private boolean busy;

	public PortableLink(GlobalPos pos, String name, boolean busy) {
		this.pos = pos;
		this.name = name;
		this.busy = busy;
	}

	public GlobalPos pos() {
		return this.pos;
	}

	public String name() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean busy() {
		return this.busy;
	}

	public void setBusy(boolean busy) {
		this.busy = busy;
	}
}
