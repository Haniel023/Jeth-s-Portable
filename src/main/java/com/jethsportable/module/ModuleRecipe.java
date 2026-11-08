package com.jethsportable.module;

import com.jethsportable.item.BlankModuleItem;
import com.jethsportable.item.ModuleItem;
import com.jethsportable.registry.PortableRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Blank Module + a station block = a built-in module of that station. A module alone in the grid
 * comes apart again: the Blank Module, plus the block if it had one inside. One recipe covers every
 * station, modded ones included, which is why it's code rather than a JSON recipe per block.
 */
public final class ModuleRecipe extends CustomRecipe {
	public static final ModuleRecipe INSTANCE = new ModuleRecipe();
	public static final RecipeSerializer<ModuleRecipe> SERIALIZER = new RecipeSerializer<>(MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

	private record Parts(int count, int blankIndex, int blockIndex, int moduleIndex) {
	}

	private ModuleRecipe() {
	}

	private static Parts parts(CraftingInput input) {
		int count = 0;
		int blank = -1;
		int block = -1;
		int module = -1;
		for (int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if (stack.isEmpty()) continue;
			count++;
			if (stack.getItem() instanceof BlankModuleItem) {
				blank = i;
			} else if (stack.getItem() instanceof ModuleItem) {
				module = i;
			} else if (stack.getItem() instanceof BlockItem) {
				block = i;
			}
		}
		return new Parts(count, blank, block, module);
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		Parts parts = parts(input);
		if (parts.count() == 2 && parts.blankIndex() >= 0 && parts.blockIndex() >= 0) {
			return Modules.canBuildIn(((BlockItem)input.getItem(parts.blockIndex()).getItem()).getBlock(), level);
		}
		return parts.count() == 1 && parts.moduleIndex() >= 0 && ModuleSlot.of(input.getItem(parts.moduleIndex())) != null;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		Parts parts = parts(input);
		if (parts.moduleIndex() >= 0) {
			return new ItemStack(PortableRegistry.BLANK_MODULE);
		}
		var block = ((BlockItem)input.getItem(parts.blockIndex()).getItem()).getBlock();
		return Modules.create(Modules.stationId(block), Modules.anvilDamage(block), null);
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
		Parts parts = parts(input);
		if (parts.count() == 1 && parts.moduleIndex() >= 0) {
			ModuleSlot module = ModuleSlot.of(input.getItem(parts.moduleIndex()));
			if (module != null && !module.linked()) {
				remaining.set(parts.moduleIndex(), Modules.blockStack(module));
			}
		}
		return remaining;
	}

	@Override
	public RecipeSerializer<ModuleRecipe> getSerializer() {
		return SERIALIZER;
	}
}
