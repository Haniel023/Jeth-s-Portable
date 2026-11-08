package com.jethsportable.link;

import com.jethsportable.JethsPortable;
import com.jethsportable.block.PortableCoreBlock;
import com.jethsportable.data.PortableBase;
import com.jethsportable.data.PortableData;
import com.jethsportable.item.BlankModuleItem;
import com.jethsportable.item.LinkItem;
import com.jethsportable.item.ModuleItem;
import com.jethsportable.module.ModuleKind;
import com.jethsportable.module.ModuleSlot;
import com.jethsportable.module.Modules;
import com.jethsportable.registry.PortableRegistry;
import com.jethsportable.storage.MachineView;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Links and unlinks blocks. This runs in a later event phase than the default one, so protection
 * mods that deny the interaction get to say no first.
 */
public final class LinkHandler {
	private static final Identifier LATE_PHASE = JethsPortable.id("late");

	private LinkHandler() {
	}

	public static void register() {
		UseBlockCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, LATE_PHASE);
		UseBlockCallback.EVENT.register(LATE_PHASE, LinkHandler::onUseBlock);
		PlayerBlockBreakEvents.BEFORE.register(LinkHandler::allowBreak);
		PlayerBlockBreakEvents.AFTER.register(LinkHandler::afterBreak);
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isSpectator()) return InteractionResult.PASS;
		if (stack.getItem() instanceof BlankModuleItem || stack.getItem() instanceof ModuleItem) return onModuleUse(player, level, hit, stack);
		if (!(stack.getItem() instanceof LinkItem)) return InteractionResult.PASS;
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof PortableCoreBlock) return InteractionResult.PASS;
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return level.getBlockEntity(pos) != null ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (level.getBlockEntity(pos) == null) return InteractionResult.PASS;
		return toggle(serverLevel, serverPlayer, pos, state, stack);
	}

	/** A Blank Module on a placed station makes a module linked to it; a linked module moves its link. */
	private static InteractionResult onModuleUse(Player player, Level level, BlockHitResult hit, ItemStack stack) {
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof PortableCoreBlock) return InteractionResult.PASS;
		boolean linkable = Modules.canLink(level, pos, state);
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return linkable ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (!linkable) {
			ModuleKind kind = Modules.kindOf(state.getBlock());
			if (kind == ModuleKind.FURNACE || kind == ModuleKind.BREWING) {
				serverPlayer.sendOverlayMessage(Component.translatable("jethsportable.message.module_machine").withStyle(ChatFormatting.RED));
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		}
		if (!serverLevel.mayInteract(serverPlayer, pos)) return InteractionResult.FAIL;
		GlobalPos globalPos = GlobalPos.of(level.dimension(), pos);
		Identifier station = Modules.stationId(state.getBlock());
		Component name = Modules.stationName(station);
		if (stack.getItem() instanceof BlankModuleItem) {
			if (Modules.kindOf(state.getBlock()) == ModuleKind.STATION && !Modules.hasMenu(state, level, pos) && !opensOnRightClick(serverLevel, serverPlayer, state, hit)) {
				return InteractionResult.PASS;
			}
			stack.consume(1, serverPlayer);
			serverPlayer.getInventory().placeItemBackInInventory(Modules.create(station, 0, globalPos), Prediction.SERVER_ONLY);
			serverPlayer.sendOverlayMessage(Component.translatable("jethsportable.message.module_linked", name).withStyle(ChatFormatting.GREEN));
			level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8F, 1.3F);
			return InteractionResult.SUCCESS;
		}
		ModuleSlot module = ModuleSlot.of(stack);
		if (module == null) return InteractionResult.PASS;
		if (!module.station().equals(station)) {
			// Only worth a message on another station; anything else just ignores the click.
			if (!Modules.hasMenu(state, level, pos)) return InteractionResult.PASS;
			serverPlayer.sendOverlayMessage(Component.translatable("jethsportable.message.module_wrong_block", Modules.stationName(module.station())).withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		if (!module.linked()) {
			serverPlayer.sendOverlayMessage(Component.translatable("jethsportable.message.module_built_in", Modules.stationName(module.station())).withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		stack.set(PortableRegistry.MODULE_LINK, globalPos);
		serverPlayer.sendOverlayMessage(Component.translatable("jethsportable.message.module_relinked", name).withStyle(ChatFormatting.GREEN));
		level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8F, 1.3F);
		return InteractionResult.SUCCESS;
	}

	/**
	 * Right clicks the block for the player, as vanilla would have anyway, and reports whether a menu
	 * opened. That menu is closed again: the player was linking, not using the block.
	 */
	private static boolean opensOnRightClick(ServerLevel level, ServerPlayer player, BlockState state, BlockHitResult hit) {
		int before = player.containerMenu.containerId;
		try {
			state.useWithoutItem(level, player, hit);
		} catch (RuntimeException e) {
			return false;
		}
		boolean opened = player.containerMenu != player.inventoryMenu && player.containerMenu.containerId != before;
		if (opened) player.closeContainer();
		return opened;
	}

	private static InteractionResult toggle(ServerLevel level, ServerPlayer player, BlockPos clicked, BlockState state, ItemStack stack) {
		if (!level.mayInteract(player, clicked)) return InteractionResult.FAIL;
		BlockPos pos = mainPos(level, clicked, state);
		GlobalPos globalPos = GlobalPos.of(level.dimension(), pos);
		PortableData data = PortableData.get(level.getServer());

		PortableBase existing = data.baseOfLink(globalPos);
		if (existing != null) {
			if (!existing.isMember(player)) {
				player.sendOverlayMessage(Component.translatable("jethsportable.message.linked_by_other", existing.ownerName()).withStyle(ChatFormatting.RED));
				return InteractionResult.FAIL;
			}
			data.removeLink(existing, globalPos);
			player.getInventory().placeItemBackInInventory(new ItemStack(PortableRegistry.PORTABLE_LINK), Prediction.SERVER_ONLY);
			player.sendOverlayMessage(Component.translatable("jethsportable.message.unlinked"));
			level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.6F, 1.0F);
			return InteractionResult.SUCCESS;
		}

		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!canLink(level, pos, state, blockEntity)) {
			String key = Modules.canLink(level, pos, state) ? "jethsportable.message.use_blank_module" : "jethsportable.message.not_linkable";
			player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		if (blockEntity instanceof BaseContainerBlockEntity container && !container.canOpen(player)) {
			return InteractionResult.FAIL;
		}

		PortableBase base = pickBase(data, player, globalPos);
		if (base == null) {
			boolean anyInRange = data.basesOf(player).stream().anyMatch(b -> b.inRange(globalPos));
			player.sendOverlayMessage(Component.translatable(anyInRange ? "jethsportable.message.core_full" : "jethsportable.message.no_core").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		data.addLink(base, globalPos);
		stack.consume(1, player);
		player.sendOverlayMessage(Component.translatable("jethsportable.message.linked", base.links().size(), base.maxLinks() < 0 ? "∞" : base.maxLinks()).withStyle(ChatFormatting.GREEN));
		level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8F, 1.1F);
		return InteractionResult.SUCCESS;
	}

	/** The nearest Core this player belongs to that reaches the block and has room. */
	private static @Nullable PortableBase pickBase(PortableData data, ServerPlayer player, GlobalPos pos) {
		PortableBase best = null;
		double bestDistance = Double.MAX_VALUE;
		for (PortableBase base : data.basesOf(player)) {
			GlobalPos core = base.core();
			if (core == null || !base.inRange(pos) || !base.hasLinkRoom()) continue;
			double distance = core.dimension().equals(pos.dimension()) ? core.pos().distSqr(pos.pos()) : Double.MAX_VALUE / 2;
			if (distance < bestDistance) {
				best = base;
				bestDistance = distance;
			}
		}
		return best;
	}

	public static boolean canLink(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (state.is(PortableRegistry.NOT_LINKABLE) || blockEntity == null) return false;
		if (MachineView.isMachine(blockEntity)) return true;
		return state.getMenuProvider(level, pos) != null && ItemStorage.SIDED.find(level, pos, state, blockEntity, null) != null;
	}

	/** A double chest is one thing: always store it under the same half. */
	public static BlockPos mainPos(Level level, BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
			if (level.getBlockState(other).getBlock() == state.getBlock()) {
				return other.compareTo(pos) < 0 ? other : pos;
			}
		}
		return pos;
	}

	/** Only members can take a Core down, unless the player is an operator. */
	private static boolean allowBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (!(state.getBlock() instanceof PortableCoreBlock) || !(level instanceof ServerLevel serverLevel)) return true;
		PortableData data = PortableData.get(serverLevel.getServer());
		GlobalPos here = GlobalPos.of(level.dimension(), pos);
		for (PortableBase base : data.bases()) {
			if (here.equals(base.core()) && !base.isMember(player) && !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
				player.sendOverlayMessage(Component.translatable("jethsportable.message.not_member", base.ownerName()).withStyle(ChatFormatting.RED));
				return false;
			}
		}
		return true;
	}

	/** Breaking a linked block ends the link and drops the Link where the block was. */
	private static void afterBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel)) return;
		PortableData data = PortableData.get(serverLevel.getServer());
		GlobalPos here = GlobalPos.of(level.dimension(), pos);
		PortableBase base = data.baseOfLink(here);
		if (base == null) return;
		data.removeLink(base, here);
		ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(PortableRegistry.PORTABLE_LINK));
		drop.setDefaultPickUpDelay();
		level.addFreshEntity(drop);
	}
}
