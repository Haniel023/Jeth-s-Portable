package com.jethsportable.module;

import com.jethsportable.registry.PortableRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * A module installed in a Core. Stored as plain data rather than an item stack, so it saves without
 * registry context and travels with the base when the Core is broken.
 */
public record ModuleSlot(Identifier station, int damage, Optional<GlobalPos> link) {
	public static final Codec<ModuleSlot> CODEC = RecordCodecBuilder.create(i -> i.group(
		Identifier.CODEC.fieldOf("station").forGetter(ModuleSlot::station),
		Codec.INT.optionalFieldOf("damage", 0).forGetter(ModuleSlot::damage),
		GlobalPos.CODEC.optionalFieldOf("link").forGetter(ModuleSlot::link)
	).apply(i, ModuleSlot::new));

	public static @Nullable ModuleSlot of(ItemStack stack) {
		Identifier station = stack.get(PortableRegistry.STATION);
		if (station == null || !stack.is(PortableRegistry.MODULE)) return null;
		return new ModuleSlot(station, stack.getOrDefault(PortableRegistry.MODULE_DAMAGE, 0), Optional.ofNullable(stack.get(PortableRegistry.MODULE_LINK)));
	}

	public Block block() {
		return BuiltInRegistries.BLOCK.getValue(this.station);
	}

	/** Null when the station's block is gone, for example because its mod was removed. */
	public @Nullable ModuleKind kind() {
		return Modules.kindOf(this.block());
	}

	public boolean linked() {
		return this.link.isPresent();
	}

	public ModuleSlot withDamage(int damage) {
		return new ModuleSlot(this.station, damage, this.link);
	}

	public ItemStack toStack() {
		return Modules.create(this.station, this.damage, this.link.orElse(null));
	}
}
