package com.jethsportable.mixin;

import com.jethsportable.remote.RemoteAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Menus check this to decide whether you're still close enough. A linked block opened remotely always is. */
@Mixin(Player.class)
abstract class PlayerMixin {
	@Inject(method = "isWithinBlockInteractionRange", at = @At("HEAD"), cancellable = true)
	private void jethsportable$allowRemoteMenu(BlockPos pos, double buffer, CallbackInfoReturnable<Boolean> cir) {
		if ((Object)this instanceof ServerPlayer player && RemoteAccess.allows(player, pos)) {
			cir.setReturnValue(true);
		}
	}
}
