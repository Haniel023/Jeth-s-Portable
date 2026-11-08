package com.jethsportable.mixin;

import com.jethsportable.module.VirtualStations;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stations check that their block is still there and you're next to it. A built-in module's station
 * isn't in the world, so for those menus the answer is yes. Modded stations usually use this check too.
 */
@Mixin(AbstractContainerMenu.class)
abstract class AbstractContainerMenuMixin {
	@Inject(
		method = "stillValid(Lnet/minecraft/world/inventory/ContainerLevelAccess;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/Block;)Z",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void jethsportable$builtInModule(ContainerLevelAccess access, Player player, Block block, CallbackInfoReturnable<Boolean> cir) {
		if (VirtualStations.allows(player)) cir.setReturnValue(true);
	}
}
