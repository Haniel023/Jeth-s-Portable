package com.jethsportable.mixin;

import com.jethsportable.module.VirtualStations;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The anvil and smithing table do their own block check; same answer as {@link AbstractContainerMenuMixin}. */
@Mixin(ItemCombinerMenu.class)
abstract class ItemCombinerMenuMixin {
	@Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
	private void jethsportable$builtInModule(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (VirtualStations.allows(player)) cir.setReturnValue(true);
	}
}
