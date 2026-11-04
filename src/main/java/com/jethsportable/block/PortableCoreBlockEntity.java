package com.jethsportable.block;

import com.jethsportable.data.CoreData;
import com.jethsportable.data.PortableBase;
import com.jethsportable.data.PortableData;
import com.jethsportable.registry.PortableRegistry;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Only remembers which base this Core belongs to. The base itself lives in {@link PortableData}. */
public final class PortableCoreBlockEntity extends BlockEntity {
	private @Nullable UUID baseId;

	public PortableCoreBlockEntity(BlockPos pos, BlockState state) {
		super(PortableRegistry.CORE_BLOCK_ENTITY, pos, state);
	}

	public @Nullable UUID baseId() {
		return this.baseId;
	}

	public void setBaseId(UUID baseId) {
		this.baseId = baseId;
		this.setChanged();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.baseId = input.read("base", UUIDUtil.CODEC).orElse(null);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("base", UUIDUtil.CODEC, this.baseId);
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		CoreData data = components.get(PortableRegistry.CORE_DATA);
		this.baseId = data == null ? null : data.baseId();
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		if (this.baseId != null && this.level instanceof ServerLevel serverLevel) {
			PortableBase base = PortableData.get(serverLevel.getServer()).base(this.baseId);
			if (base != null) {
				components.set(PortableRegistry.CORE_DATA, new CoreData(this.baseId, base.level(), base.links().size()));
			}
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (this.baseId != null && this.level instanceof ServerLevel serverLevel) {
			PortableData.get(serverLevel.getServer()).coreRemoved(this.baseId, GlobalPos.of(serverLevel.dimension(), pos));
		}
	}
}
