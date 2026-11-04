package com.jethsportable.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int jethsportable$getLeftPos();

	@Accessor("topPos")
	int jethsportable$getTopPos();

	/** Final in vanilla, and the recipe book screen's constructor doesn't let a subclass choose it. */
	@Mutable
	@Accessor("imageWidth")
	void jethsportable$setImageWidth(int width);

	@Mutable
	@Accessor("imageHeight")
	void jethsportable$setImageHeight(int height);
}
