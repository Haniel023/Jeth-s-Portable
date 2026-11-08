package com.jethsportable.module;

import com.jethsportable.data.PortableBase;
import com.jethsportable.data.PortableData;
import com.jethsportable.network.PortablePayloads;
import com.jethsportable.network.ServerActions;
import com.jethsportable.registry.PortableRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * Menus opened from built-in modules. Their stations aren't in the world, so vanilla's "is the block
 * still there and are you next to it" check would close them at once; this answers it instead, and
 * closes them when the player leaves the base's range or the module is taken out.
 */
public final class VirtualStations {
	private record Open(int containerId, UUID base, int slot, @Nullable GlobalPos keepLoaded) {
	}

	private static final Map<UUID, Open> OPEN = new HashMap<>();
	private static final Set<ServerPlayer> CLOSE_AFTER_TICK = new HashSet<>();

	private VirtualStations() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!CLOSE_AFTER_TICK.isEmpty()) {
				for (ServerPlayer player : CLOSE_AFTER_TICK) {
					if (!player.isRemoved()) player.closeContainer();
				}
				CLOSE_AFTER_TICK.clear();
			}
			if (server.getTickCount() % 20 == 0 && !OPEN.isEmpty()) tick(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			OPEN.clear();
			CLOSE_AFTER_TICK.clear();
		});
	}

	public static void opened(ServerPlayer player, int containerId, PortableBase base, int slot, @Nullable GlobalPos keepLoaded) {
		OPEN.put(player.getUUID(), new Open(containerId, base.id(), slot, keepLoaded));
		keepLoaded(player.level().getServer(), keepLoaded);
	}

	/** Asked by the menus' validity checks, for whichever player is using them. */
	public static boolean allows(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) return false;
		Open open = OPEN.get(serverPlayer.getUUID());
		return open != null && open.containerId() == serverPlayer.containerMenu.containerId;
	}

	/** Called after every use of an anvil menu. Wears down the built-in anvil module it came from, if any. */
	public static void anvilUsed(ServerPlayer player) {
		if (!allows(player)) return;
		Open open = OPEN.get(player.getUUID());
		PortableData data = PortableData.get(player.level().getServer());
		PortableBase base = data.base(open.base());
		ModuleSlot module = base == null ? null : base.module(open.slot());
		if (module == null || module.linked() || !Modules.isAnvil(module.station()) || !Modules.rollAnvilWear(player)) return;
		int damage = module.damage() + 1;
		if (damage >= Modules.ANVIL_BREAKS_AT) {
			base.setModule(open.slot(), null);
			player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 1.0F, 1.0F);
			ServerActions.notify(player, PortablePayloads.NOTIFY_ANVIL_BROKE, base.displayName(), Modules.create(module.station(), 2, null), 0);
			// Closing from inside the click that took the item would leave vanilla's syncing paused.
			CLOSE_AFTER_TICK.add(player);
		} else {
			base.setModule(open.slot(), module.withDamage(damage));
			if (damage == Modules.ANVIL_BREAKS_AT - 1) {
				ServerActions.notify(player, PortablePayloads.NOTIFY_ANVIL_WORN, base.displayName(), Modules.create(module.station(), damage, null), 0);
			}
		}
		data.changed(base);
	}

	private static void tick(MinecraftServer server) {
		PortableData data = PortableData.get(server);
		Iterator<Map.Entry<UUID, Open>> iterator = OPEN.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Open> entry = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Open open = entry.getValue();
			if (player == null || player.containerMenu.containerId != open.containerId()) {
				iterator.remove();
				continue;
			}
			PortableBase base = data.base(open.base());
			boolean ok = base != null && base.isMember(player) && base.module(open.slot()) != null
				&& base.inRange(GlobalPos.of(player.level().dimension(), player.blockPosition()));
			if (!ok) {
				iterator.remove();
				player.closeContainer();
				continue;
			}
			keepLoaded(server, open.keepLoaded());
		}
	}

	private static void keepLoaded(MinecraftServer server, @Nullable GlobalPos pos) {
		if (pos == null) return;
		ServerLevel level = server.getLevel(pos.dimension());
		if (level != null) {
			level.getChunkSource().addTicketWithRadius(PortableRegistry.MACHINE_TICKET, ChunkPos.containing(pos.pos()), 1);
		}
	}
}
