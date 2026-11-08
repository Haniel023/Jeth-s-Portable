package com.jethsportable.mixin;

import com.jethsportable.module.VirtualStations;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla wears down the anvil block it finds; a built-in anvil module has none, so it wears down here. */
@Mixin(AnvilMenu.class)
abstract class AnvilMenuMixin {
	@Inject(method = "onTake", at = @At("TAIL"))
	private void jethsportable$wearModule(Player player, ItemStack carried, CallbackInfo ci) {
		if (player instanceof ServerPlayer serverPlayer) VirtualStations.anvilUsed(serverPlayer);
	}
}
