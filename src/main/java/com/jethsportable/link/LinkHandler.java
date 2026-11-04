package com.jethsportable.link;

import com.jethsportable.JethsPortable;
import com.jethsportable.block.PortableCoreBlock;
import com.jethsportable.data.PortableBase;
import com.jethsportable.data.PortableData;
import com.jethsportable.item.LinkItem;
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
		if (!(stack.getItem() instanceof LinkItem) || player.isSpectator()) return InteractionResult.PASS;
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof PortableCoreBlock) return InteractionResult.PASS;
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return level.getBlockEntity(pos) != null ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (level.getBlockEntity(pos) == null) return InteractionResult.PASS;
		return toggle(serverLevel, serverPlayer, pos, state, stack);
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
			player.sendOverlayMessage(Component.translatable("jethsportable.message.not_linkable").withStyle(ChatFormatting.RED));
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
