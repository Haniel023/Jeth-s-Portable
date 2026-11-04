package com.jethsportable.client.mixin;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {
	/** Lets the storage window refresh what's craftable when the stored items change. */
	@Invoker("updateStackedContents")
	void jethsportable$updateStackedContents();
}
