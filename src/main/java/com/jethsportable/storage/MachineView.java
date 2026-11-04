package com.jethsportable.storage;

import com.jethsportable.mixin.AbstractFurnaceBlockEntityAccessor;
import com.jethsportable.mixin.BrewingStandBlockEntityAccessor;
import com.jethsportable.network.PortablePayloads;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.jspecify.annotations.Nullable;

/** Reads furnaces, smokers, blast furnaces and brewing stands through one small interface. */
public final class MachineView {
	public static final int FURNACE_INPUT = 0;
	public static final int FURNACE_FUEL = 1;
	public static final int FURNACE_RESULT = 2;
	public static final int BREWING_INGREDIENT = 3;
	public static final int BREWING_FUEL = 4;
	private static final int BREW_TIME = 400;
	private static final int BLAZE_FUEL = 20;

	private MachineView() {
	}

	public static boolean isMachine(@Nullable BlockEntity entity) {
		return entity instanceof AbstractFurnaceBlockEntity || entity instanceof BrewingStandBlockEntity;
	}

	public static boolean canCook(ServerLevel level, AbstractFurnaceBlockEntity furnace) {
		ItemStack input = furnace.getItem(MachineView.FURNACE_INPUT);
		return !input.isEmpty() && ((AbstractFurnaceBlockEntityAccessor)furnace).jethsportable$quickCheck().getRecipeFor(new SingleRecipeInput(input), level).isPresent();
	}

	/** Whether the machine is running or has something waiting to run. */
	public static boolean hasWork(ServerLevel level, BlockEntity entity) {
		if (entity instanceof AbstractFurnaceBlockEntity furnace) return canCook(level, furnace);
		if (entity instanceof BrewingStandBlockEntity brewing) return data(brewing).get(0) > 0;
		return false;
	}

	public static ContainerData data(AbstractFurnaceBlockEntity furnace) {
		return ((AbstractFurnaceBlockEntityAccessor)furnace).jethsportable$dataAccess();
	}

	public static ContainerData data(BrewingStandBlockEntity brewing) {
		return ((BrewingStandBlockEntityAccessor)brewing).jethsportable$dataAccess();
	}

	/** The bottles in a brewing stand, as one stack: the first bottle with a count of how many there are. */
	public static ItemStack bottles(BrewingStandBlockEntity brewing) {
		ItemStack first = ItemStack.EMPTY;
		int count = 0;
		for (int i = 0; i < 3; i++) {
			ItemStack bottle = brewing.getItem(i);
			if (bottle.isEmpty()) continue;
			if (first.isEmpty()) first = bottle;
			count++;
		}
		return first.isEmpty() ? ItemStack.EMPTY : first.copyWithCount(count);
	}

	public static PortablePayloads.MachineInfo describe(GlobalPos pos, String name, ItemStack icon, BlockEntity entity) {
		if (entity instanceof AbstractFurnaceBlockEntity furnace) {
			ContainerData data = data(furnace);
			int total = data.get(3);
			int burnTotal = data.get(1);
			return new PortablePayloads.MachineInfo(
				pos, name, icon,
				furnace.getItem(MachineView.FURNACE_INPUT).copy(),
				furnace.getItem(MachineView.FURNACE_FUEL).copy(),
				furnace.getItem(MachineView.FURNACE_RESULT).copy(),
				total <= 0 ? 0 : data.get(2) * 100 / total,
				burnTotal <= 0 ? 0 : data.get(0) * 100 / burnTotal,
				PortablePayloads.STATE_OK, false
			);
		}
		BrewingStandBlockEntity brewing = (BrewingStandBlockEntity)entity;
		ContainerData data = data(brewing);
		int time = data.get(0);
		return new PortablePayloads.MachineInfo(
			pos, name, icon,
			brewing.getItem(MachineView.BREWING_INGREDIENT).copy(),
			brewing.getItem(MachineView.BREWING_FUEL).copy(),
			bottles(brewing),
			time <= 0 ? 0 : (BREW_TIME - time) * 100 / BREW_TIME,
			data.get(1) * 100 / BLAZE_FUEL,
			PortablePayloads.STATE_OK, true
		);
	}

	/** Takes out what's finished. Returns the stacks to hand over, already removed from the machine. */
	public static List<ItemStack> takeOutput(BlockEntity entity) {
		List<ItemStack> taken = new ArrayList<>();
		if (entity instanceof AbstractFurnaceBlockEntity furnace) {
			take(furnace, MachineView.FURNACE_RESULT, taken);
		} else if (entity instanceof BrewingStandBlockEntity brewing && data(brewing).get(0) == 0) {
			for (int i = 0; i < 3; i++) {
				take(brewing, i, taken);
			}
		}
		return taken;
	}

	private static void take(Container container, int slot, List<ItemStack> into) {
		ItemStack stack = container.getItem(slot);
		if (stack.isEmpty()) return;
		into.add(stack.copy());
		container.setItem(slot, ItemStack.EMPTY);
		container.setChanged();
	}
}
