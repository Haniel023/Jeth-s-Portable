package com.jethsportable.block;

import com.jethsportable.data.PortableBase;
import com.jethsportable.data.PortableData;
import com.jethsportable.item.RangeUpgradeItem;
import com.jethsportable.network.ServerActions;
import com.jethsportable.registry.PortableRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** The heart of a base. Right click to open its page, or with a Range Upgrade to level it up. */
public final class PortableCoreBlock extends BaseEntityBlock {
	public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 6);

	public PortableCoreBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 1));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LEVEL);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PortableCoreBlockEntity(pos, state);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
		if (!(level instanceof ServerLevel serverLevel) || !(by instanceof ServerPlayer player)) return;
		if (!(level.getBlockEntity(pos) instanceof PortableCoreBlockEntity core)) return;
		PortableBase base = PortableData.get(serverLevel.getServer()).placeCore(core.baseId(), player, GlobalPos.of(serverLevel.dimension(), pos));
		core.setBaseId(base.id());
		if (state.getValue(LEVEL) != base.level()) {
			level.setBlock(pos, state.setValue(LEVEL, base.level()), Block.UPDATE_ALL);
		}
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(stack.getItem() instanceof RangeUpgradeItem upgrade)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			PortableBase base = this.baseOf(serverLevel, pos, serverPlayer);
			if (base == null) return InteractionResult.FAIL;
			if (!base.isMember(serverPlayer)) {
				serverPlayer.sendOverlayMessage(Component.translatable("jethsportable.message.not_member", base.ownerName()).withStyle(ChatFormatting.RED));
				return InteractionResult.FAIL;
			}
			if (upgrade.level() != base.level() + 1) {
				String key = upgrade.level() <= base.level() ? "jethsportable.message.upgrade_done" : "jethsportable.message.upgrade_order";
				serverPlayer.sendOverlayMessage(Component.translatable(key, Component.translatable(PortableRegistry.upgradeForLevel(base.level() + 1).getDescriptionId())).withStyle(ChatFormatting.RED));
				return InteractionResult.FAIL;
			}
			base.setLevel(upgrade.level());
			level.setBlock(pos, state.setValue(LEVEL, base.level()), Block.UPDATE_ALL);
			stack.consume(1, player);
			level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6F, 1.2F);
			serverPlayer.sendOverlayMessage(Component.translatable("jethsportable.message.upgraded", base.level()).withStyle(ChatFormatting.GREEN));
			PortableData.get(serverLevel.getServer()).changed(base);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			PortableBase base = this.baseOf(serverLevel, pos, serverPlayer);
			if (base == null) return InteractionResult.FAIL;
			if (!base.isMember(serverPlayer)) {
				serverPlayer.sendOverlayMessage(Component.translatable("jethsportable.message.not_member", base.ownerName()).withStyle(ChatFormatting.RED));
				return InteractionResult.FAIL;
			}
			ServerActions.openStorage(serverPlayer, ServerActions.TAB_BASE);
		}
		return InteractionResult.SUCCESS;
	}

	/** The block's base. A Core placed without a player (a command, say) belongs to whoever uses it first. */
	private @Nullable PortableBase baseOf(ServerLevel level, BlockPos pos, ServerPlayer player) {
		if (!(level.getBlockEntity(pos) instanceof PortableCoreBlockEntity core)) return null;
		PortableData data = PortableData.get(level.getServer());
		PortableBase base = core.baseId() == null ? null : data.base(core.baseId());
		GlobalPos here = GlobalPos.of(level.dimension(), pos);
		if (base == null || !here.equals(base.core())) {
			base = data.placeCore(core.baseId(), player, here);
			core.setBaseId(base.id());
		}
		return base;
	}

	/** Creative players get no drops, but they shouldn't lose a base by breaking its Core. */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && player.preventsBlockDrops() && level.getBlockEntity(pos) instanceof PortableCoreBlockEntity core && core.baseId() != null) {
			ItemStack stack = new ItemStack(this);
			stack.applyComponents(core.collectComponents());
			ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
			entity.setDefaultPickUpDelay();
			level.addFreshEntity(entity);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
