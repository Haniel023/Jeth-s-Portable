package com.jethsportable.client.mixin;

import com.jethsportable.client.ui.StorageScreen;
import com.jethsportable.menu.StorageMenu;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.inventory.RecipeBookMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** The book assumes a window 176 wide. The storage window is wider, so it needs to sit further left. */
@Mixin(RecipeBookComponent.class)
abstract class RecipeBookComponentMixin {
	@Shadow
	@Final
	protected RecipeBookMenu menu;

	@ModifyConstant(method = {"initVisuals", "isOffsetNextToMainGUI"}, constant = @Constant(intValue = 86))
	private int jethsportable$offsetForStorageWindow(int offset) {
		return this.menu instanceof StorageMenu ? StorageScreen.bookOffset() : offset;
	}
}
