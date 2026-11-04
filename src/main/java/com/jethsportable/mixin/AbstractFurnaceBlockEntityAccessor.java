package com.jethsportable.mixin;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityAccessor {
	@Accessor("dataAccess")
	ContainerData jethsportable$dataAccess();

	@Accessor("quickCheck")
	RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> jethsportable$quickCheck();
}
