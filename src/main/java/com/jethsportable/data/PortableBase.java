package com.jethsportable.data;

import com.jethsportable.config.PortableServerConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** One Core and everything linked to it. Outlives the block, so a broken Core can be moved. */
public final class PortableBase {
	private record Trusted(UUID id, String name) {
		static final Codec<Trusted> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(Trusted::id),
			Codec.STRING.fieldOf("name").forGetter(Trusted::name)
		).apply(i, Trusted::new));
	}

	public static final Codec<PortableBase> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("id").forGetter(PortableBase::id),
		UUIDUtil.CODEC.fieldOf("owner").forGetter(PortableBase::owner),
		Codec.STRING.optionalFieldOf("owner_name", "").forGetter(PortableBase::ownerName),
		Codec.STRING.optionalFieldOf("name", "").forGetter(PortableBase::name),
		Codec.INT.optionalFieldOf("level", 1).forGetter(PortableBase::level),
		GlobalPos.CODEC.optionalFieldOf("core").forGetter(b -> Optional.ofNullable(b.core)),
		Trusted.CODEC.listOf().optionalFieldOf("trusted", List.of()).forGetter(b -> b.trusted.entrySet().stream().map(e -> new Trusted(e.getKey(), e.getValue())).toList()),
		PortableLink.CODEC.listOf().optionalFieldOf("links", List.of()).forGetter(PortableBase::links)
	).apply(i, (id, owner, ownerName, name, level, core, trusted, links) -> {
		PortableBase base = new PortableBase(id, owner, ownerName);
		base.name = name;
		base.level = level;
		base.core = core.orElse(null);
		trusted.forEach(t -> base.trusted.put(t.id(), t.name()));
		base.links.addAll(links);
		return base;
	}));

	private final UUID id;
	private final UUID owner;
	private final String ownerName;
	private final Map<UUID, String> trusted = new LinkedHashMap<>();
	private final List<PortableLink> links = new ArrayList<>();
	private String name = "";
	private int level = 1;
	private @Nullable GlobalPos core;

	public PortableBase(UUID id, UUID owner, String ownerName) {
		this.id = id;
		this.owner = owner;
		this.ownerName = ownerName;
	}

	public UUID id() {
		return this.id;
	}

	public UUID owner() {
		return this.owner;
	}

	public String ownerName() {
		return this.ownerName;
	}

	public String name() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int level() {
		return this.level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public @Nullable GlobalPos core() {
		return this.core;
	}

	public void setCore(@Nullable GlobalPos core) {
		this.core = core;
	}

	public Map<UUID, String> trusted() {
		return this.trusted;
	}

	public List<PortableLink> links() {
		return this.links;
	}

	public @Nullable PortableLink link(GlobalPos pos) {
		for (PortableLink link : this.links) {
			if (link.pos().equals(pos)) return link;
		}
		return null;
	}

	public boolean isMember(Player player) {
		return this.isMember(player.getUUID());
	}

	public boolean isMember(UUID player) {
		return this.owner.equals(player) || this.trusted.containsKey(player);
	}

	public int range() {
		return PortableServerConfig.get().range(this.level);
	}

	public int maxLinks() {
		return PortableServerConfig.get().maxLinks(this.level);
	}

	public boolean hasLinkRoom() {
		int max = this.maxLinks();
		return max < 0 || this.links.size() < max;
	}

	/** Whether a position is inside this Core's range. Height doesn't count; a tall base is still one base. */
	public boolean inRange(GlobalPos pos) {
		if (this.core == null) return false;
		int range = this.range();
		if (range < 0) return true;
		if (!this.core.dimension().equals(pos.dimension())) return false;
		return horizontalDistanceSqr(this.core.pos(), pos.pos()) <= (long)range * range;
	}

	private static long horizontalDistanceSqr(BlockPos a, BlockPos b) {
		long dx = a.getX() - b.getX();
		long dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}
}
