package com.jethsportable.remote;

import com.jethsportable.data.PortableBase;
import com.jethsportable.data.PortableData;
import com.jethsportable.registry.PortableRegistry;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * Vanilla closes a chest's menu when you walk away from it. For a chest opened through a base, the
 * range that matters is the base's, so this answers the vanilla distance check while it holds.
 */
public final class RemoteAccess {
	private record Open(int containerId, UUID base, GlobalPos pos) {
	}

	private static final Map<UUID, Open> OPEN = new HashMap<>();

	private RemoteAccess() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0 && !OPEN.isEmpty()) tick(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> OPEN.clear());
	}

	/** Opens the block's own menu. False if it has none, or the block refused (a locked chest, say). */
	public static boolean open(ServerPlayer player, PortableBase base, GlobalPos pos) {
		ServerLevel level = player.level().getServer().getLevel(pos.dimension());
		if (level == null || !level.isLoaded(pos.pos())) return false;
		MenuProvider provider = level.getBlockState(pos.pos()).getMenuProvider(level, pos.pos());
		if (provider == null) return false;
		OptionalInt containerId = player.openMenu(provider);
		if (containerId.isEmpty()) return false;
		OPEN.put(player.getUUID(), new Open(containerId.getAsInt(), base.id(), pos));
		keepLoaded(level, pos);
		return true;
	}

	/** Asked by the distance check. Accepts the block and the other half of a double chest. */
	public static boolean allows(ServerPlayer player, BlockPos pos) {
		Open open = OPEN.get(player.getUUID());
		return open != null && open.containerId() == player.containerMenu.containerId && open.pos().pos().distManhattan(pos) <= 1;
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
			if (!stillAllowed(data, player, open)) {
				iterator.remove();
				player.closeContainer();
				continue;
			}
			ServerLevel level = server.getLevel(open.pos().dimension());
			if (level != null) keepLoaded(level, open.pos());
		}
	}

	/** The player's looking at this block's menu, so its chunk stays loaded and a furnace keeps cooking. */
	private static void keepLoaded(ServerLevel level, GlobalPos pos) {
		level.getChunkSource().addTicketWithRadius(PortableRegistry.MACHINE_TICKET, ChunkPos.containing(pos.pos()), 1);
	}

	private static boolean stillAllowed(PortableData data, ServerPlayer player, Open open) {
		@Nullable PortableBase base = data.base(open.base());
		if (base == null || !base.isMember(player) || !base.inRange(open.pos())) return false;
		return base.inRange(GlobalPos.of(player.level().dimension(), player.blockPosition()));
	}
}
