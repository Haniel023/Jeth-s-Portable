package com.jethsportable.module;

import com.jethsportable.data.PortableBase;
import com.jethsportable.registry.PortableRegistry;
import com.jethsportable.remote.RemoteAccess;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Which blocks can be modules, and opening them. A built-in module carries its block inside; a linked
 * one points at a block placed in the base and opens that block, from wherever the player is.
 */
public final class Modules {
	public static final int SLOTS = 6;
	public static final int ANVIL_BREAKS_AT = 3;
	private static final Identifier ANVIL = Identifier.withDefaultNamespace("anvil");
	private static final float ANVIL_WEAR_CHANCE = 0.12F;

	private Modules() {
	}

	/** What a module made from this block does. Null for a block that can never be one. */
	public static @Nullable ModuleKind kindOf(Block block) {
		if (block == Blocks.AIR) return null;
		if (block instanceof AbstractFurnaceBlock) return ModuleKind.FURNACE;
		if (block instanceof BrewingStandBlock) return ModuleKind.BREWING;
		if (block instanceof EnderChestBlock) return ModuleKind.ENDER_CHEST;
		if (block instanceof EnchantingTableBlock) return ModuleKind.ENCHANTING;
		return ModuleKind.STATION;
	}

	/**
	 * Whether a Blank Module and this block can be crafted into a module. Generic stations must have
	 * a screen and no block entity, because a built-in one has no block in the world to keep data in.
	 */
	public static boolean canBuildIn(Block block, Level level) {
		if (block.defaultBlockState().is(PortableRegistry.NOT_A_MODULE)) return false;
		ModuleKind kind = kindOf(block);
		if (kind == null) return false;
		if (kind != ModuleKind.STATION) return true;
		return !(block instanceof EntityBlock) && hasMenu(block.defaultBlockState(), level, BlockPos.ZERO);
	}

	/**
	 * Whether a placed block might take a linked module. Blocks holding items are linked with a
	 * Portable Link instead. Some mods open their screen straight from a right click instead of
	 * declaring it, so for those the first link tries a right click to find out (see LinkHandler).
	 */
	public static boolean canLink(Level level, BlockPos pos, BlockState state) {
		if (state.is(PortableRegistry.NOT_A_MODULE) || state.isAir()) return false;
		ModuleKind kind = kindOf(state.getBlock());
		if (kind == null || kind == ModuleKind.FURNACE || kind == ModuleKind.BREWING) return false;
		if (kind != ModuleKind.STATION) return true;
		return ItemStorage.SIDED.find(level, pos, state, level.getBlockEntity(pos), null) == null;
	}

	/** Whether the block declares its screen the vanilla way, so it can be opened without a right click. */
	public static boolean hasMenu(BlockState state, Level level, BlockPos pos) {
		try {
			return state.getMenuProvider(level, pos) != null;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Chipped and damaged anvils are all "the anvil"; how worn it is goes in the damage instead. */
	public static Identifier stationId(Block block) {
		if (block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL) return ANVIL;
		return BuiltInRegistries.BLOCK.getKey(block);
	}

	public static boolean isAnvil(Identifier station) {
		return ANVIL.equals(station);
	}

	public static int anvilDamage(Block block) {
		if (block == Blocks.CHIPPED_ANVIL) return 1;
		if (block == Blocks.DAMAGED_ANVIL) return 2;
		return 0;
	}

	public static Block anvilFor(int damage) {
		return switch (damage) {
			case 0 -> Blocks.ANVIL;
			case 1 -> Blocks.CHIPPED_ANVIL;
			default -> Blocks.DAMAGED_ANVIL;
		};
	}

	public static boolean sameStation(Identifier station, BlockState state) {
		return station.equals(stationId(state.getBlock()));
	}

	public static ItemStack create(Identifier station, int damage, @Nullable GlobalPos link) {
		ItemStack stack = new ItemStack(PortableRegistry.MODULE);
		stack.set(PortableRegistry.STATION, station);
		if (link != null) {
			stack.set(PortableRegistry.MODULE_LINK, link);
		} else if (isAnvil(station)) {
			stack.set(PortableRegistry.MODULE_DAMAGE, damage);
		}
		return stack;
	}

	public static Component stationName(Identifier station) {
		Block block = BuiltInRegistries.BLOCK.getValue(station);
		return block == Blocks.AIR ? Component.literal(station.toString()) : block.getName();
	}

	/** The block a built-in module gives back when it's taken apart in a crafting grid. */
	public static ItemStack blockStack(ModuleSlot module) {
		Block block = isAnvil(module.station()) ? anvilFor(module.damage()) : module.block();
		return block == Blocks.AIR ? ItemStack.EMPTY : new ItemStack(block);
	}

	/** Opens a module for a player in range of its base. Returns false, with a message, if it can't. */
	public static boolean open(ServerPlayer player, PortableBase base, int slot) {
		ModuleSlot module = base.module(slot);
		if (module == null) return false;
		ModuleKind kind = module.kind();
		if (kind == null) {
			player.sendOverlayMessage(Component.translatable("jethsportable.message.module_missing_mod", module.station().toString()).withStyle(ChatFormatting.RED));
			return false;
		}
		if (kind == ModuleKind.FURNACE || kind == ModuleKind.BREWING) return false;
		return module.linked() ? openLinked(player, base, module, kind) : openBuiltIn(player, base, slot, module, kind);
	}

	private static boolean openLinked(ServerPlayer player, PortableBase base, ModuleSlot module, ModuleKind kind) {
		GlobalPos pos = module.link().orElseThrow();
		if (!base.inRange(pos)) {
			player.sendOverlayMessage(Component.translatable("jethsportable.message.module_out_of_range").withStyle(ChatFormatting.RED));
			return false;
		}
		ServerLevel level = player.level().getServer().getLevel(pos.dimension());
		if (level == null || !level.isLoaded(pos.pos())) {
			player.sendOverlayMessage(Component.translatable("jethsportable.message.cant_open").withStyle(ChatFormatting.RED));
			return false;
		}
		BlockState state = level.getBlockState(pos.pos());
		if (!sameStation(module.station(), state)) {
			player.sendOverlayMessage(Component.translatable("jethsportable.message.module_block_gone", stationName(module.station())).withStyle(ChatFormatting.RED));
			return false;
		}
		MenuProvider provider;
		if (kind == ModuleKind.ENDER_CHEST) {
			if (!(level.getBlockEntity(pos.pos()) instanceof EnderChestBlockEntity enderChest)) return false;
			provider = enderChestMenu(player, enderChest);
		} else {
			provider = state.getMenuProvider(level, pos.pos());
		}
		boolean opened = provider != null ? RemoteAccess.open(player, base, pos, provider) : RemoteAccess.openByUse(player, base, pos);
		if (!opened) {
			player.sendOverlayMessage(Component.translatable("jethsportable.message.cant_open").withStyle(ChatFormatting.RED));
			return false;
		}
		return true;
	}

	private static boolean openBuiltIn(ServerPlayer player, PortableBase base, int slot, ModuleSlot module, ModuleKind kind) {
		MenuProvider provider;
		GlobalPos keepLoaded = null;
		switch (kind) {
			case ENDER_CHEST -> provider = enderChestMenu(player, null);
			case ENCHANTING -> {
				// The bookshelves that count are the ones around the Core, placed like a real table's.
				GlobalPos core = base.core();
				ServerLevel coreLevel = core == null ? null : player.level().getServer().getLevel(core.dimension());
				if (core == null || coreLevel == null) return false;
				keepLoaded = core;
				provider = new SimpleMenuProvider(
					(containerId, inventory, p) -> new EnchantmentMenu(containerId, inventory, ContainerLevelAccess.create(coreLevel, core.pos())),
					Component.translatable("container.enchant")
				);
			}
			default -> {
				// Most stations only use the position for sounds and dropped XP, so the player's own spot is right.
				try {
					provider = module.block().defaultBlockState().getMenuProvider(player.level(), player.blockPosition());
				} catch (RuntimeException e) {
					provider = null;
				}
			}
		}
		if (provider == null) {
			player.sendOverlayMessage(Component.translatable("jethsportable.message.cant_open").withStyle(ChatFormatting.RED));
			return false;
		}
		OptionalInt containerId;
		try {
			containerId = player.openMenu(provider);
		} catch (RuntimeException e) {
			player.sendOverlayMessage(Component.translatable("jethsportable.message.cant_open").withStyle(ChatFormatting.RED));
			return false;
		}
		if (containerId.isEmpty()) return false;
		VirtualStations.opened(player, containerId.getAsInt(), base, slot, keepLoaded);
		return true;
	}

	private static MenuProvider enderChestMenu(ServerPlayer player, @Nullable EnderChestBlockEntity chest) {
		PlayerEnderChestContainer container = player.getEnderChestInventory();
		container.setActiveChest(chest);
		return new SimpleMenuProvider((containerId, inventory, p) -> ChestMenu.threeRows(containerId, inventory, container), Component.translatable("container.enderchest"));
	}

	/** A built-in anvil wears like a placed one: a 12% chance each use to go from intact to chipped to damaged to gone. */
	public static boolean rollAnvilWear(ServerPlayer player) {
		return !player.hasInfiniteMaterials() && player.getRandom().nextFloat() < ANVIL_WEAR_CHANCE;
	}
}
