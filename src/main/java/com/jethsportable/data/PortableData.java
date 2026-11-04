package com.jethsportable.data;

import com.jethsportable.JethsPortable;
import com.jethsportable.network.ServerActions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/** Every base on the server. Stored once per world, so a base can reach across dimensions. */
public final class PortableData extends SavedData {
	private record Target(UUID player, GlobalPos pos) {
		static final Codec<Target> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(Target::player),
			GlobalPos.CODEC.fieldOf("pos").forGetter(Target::pos)
		).apply(i, Target::new));
	}

	private static final Codec<PortableData> CODEC = RecordCodecBuilder.create(i -> i.group(
		PortableBase.CODEC.listOf().optionalFieldOf("bases", List.of()).forGetter(data -> new ArrayList<>(data.bases.values())),
		Target.CODEC.listOf().optionalFieldOf("targets", List.of()).forGetter(data -> data.targets.entrySet().stream().map(e -> new Target(e.getKey(), e.getValue())).toList())
	).apply(i, PortableData::new));
	public static final SavedDataType<PortableData> TYPE = new SavedDataType<>(
		JethsPortable.id("bases"), PortableData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);

	private final Map<UUID, PortableBase> bases = new LinkedHashMap<>();
	private final Map<GlobalPos, PortableBase> linkIndex = new HashMap<>();
	/** Each player's "Store here" chest. */
	private final Map<UUID, GlobalPos> targets = new HashMap<>();
	private transient @Nullable MinecraftServer server;

	public PortableData() {
	}

	private PortableData(List<PortableBase> loaded, List<Target> loadedTargets) {
		for (PortableBase base : loaded) {
			this.bases.put(base.id(), base);
			for (PortableLink link : base.links()) {
				this.linkIndex.put(link.pos(), base);
			}
		}
		for (Target target : loadedTargets) {
			this.targets.put(target.player(), target.pos());
		}
	}

	public static PortableData get(MinecraftServer server) {
		PortableData data = server.getDataStorage().computeIfAbsent(TYPE);
		data.server = server;
		return data;
	}

	public Collection<PortableBase> bases() {
		return this.bases.values();
	}

	public @Nullable PortableBase base(UUID id) {
		return this.bases.get(id);
	}

	public List<PortableBase> basesOf(ServerPlayer player) {
		List<PortableBase> result = new ArrayList<>();
		for (PortableBase base : this.bases.values()) {
			if (base.isMember(player)) result.add(base);
		}
		return result;
	}

	/**
	 * Bases whose Core is placed and which this player can use where they stand: they're a member
	 * and inside its range. Several Cores can overlap; the storage window merges them.
	 */
	public List<PortableBase> usableBy(ServerPlayer player) {
		GlobalPos here = GlobalPos.of(player.level().dimension(), player.blockPosition());
		List<PortableBase> result = new ArrayList<>();
		for (PortableBase base : this.bases.values()) {
			if (base.core() != null && base.isMember(player) && base.inRange(here)) result.add(base);
		}
		return result;
	}

	public @Nullable PortableBase baseOfLink(GlobalPos pos) {
		return this.linkIndex.get(pos);
	}

	public @Nullable GlobalPos target(UUID player) {
		return this.targets.get(player);
	}

	public void setTarget(UUID player, @Nullable GlobalPos pos) {
		if (pos == null) {
			this.targets.remove(player);
		} else {
			this.targets.put(player, pos);
		}
		this.setDirty();
	}

	/**
	 * A Core was placed. An item from a broken Core brings its base back, unless that base's Core is
	 * still standing somewhere (a copy made in creative), which starts a new base instead.
	 */
	public PortableBase placeCore(@Nullable UUID wanted, ServerPlayer placer, GlobalPos pos) {
		PortableBase base = wanted == null ? null : this.bases.get(wanted);
		if (base == null || base.core() != null) {
			base = new PortableBase(UUID.randomUUID(), placer.getUUID(), placer.getGameProfile().name());
			this.bases.put(base.id(), base);
		}
		base.setCore(pos);
		this.changed(base);
		return base;
	}

	public void coreRemoved(UUID baseId, GlobalPos pos) {
		PortableBase base = this.bases.get(baseId);
		if (base != null && pos.equals(base.core())) {
			base.setCore(null);
			this.changed(base);
		}
	}

	public PortableLink addLink(PortableBase base, GlobalPos pos) {
		PortableLink link = new PortableLink(pos, "", false);
		base.links().add(link);
		this.linkIndex.put(pos, base);
		this.changed(base);
		return link;
	}

	public void removeLink(PortableBase base, GlobalPos pos) {
		if (base.links().removeIf(link -> link.pos().equals(pos))) {
			this.linkIndex.remove(pos);
			this.changed(base);
		}
	}

	/** Saves and tells every online member of the base. */
	public void changed(PortableBase base) {
		this.setDirty();
		if (this.server != null) {
			ServerActions.sendBases(this.server, base);
		}
	}
}
