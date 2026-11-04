package com.jethsportable.mixin;

import com.jethsportable.machine.MachineWatcher;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	@Inject(method = "doCloseContainer", at = @At("HEAD"))
	private void jethsportable$watchLoadedMachine(CallbackInfo ci) {
		ServerPlayer player = (ServerPlayer)(Object)this;
		MachineWatcher.onMenuClosed(player, player.containerMenu);
	}
}
