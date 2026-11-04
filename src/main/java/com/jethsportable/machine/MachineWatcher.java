package com.jethsportable.machine;

import com.jethsportable.config.PortableServerConfig;
import com.jethsportable.data.PortableBase;
import com.jethsportable.data.PortableData;
import com.jethsportable.data.PortableLink;
import com.jethsportable.mixin.AbstractFurnaceMenuAccessor;
import com.jethsportable.mixin.BrewingStandMenuAccessor;
import com.jethsportable.network.PortablePayloads;
import com.jethsportable.network.ServerActions;
import com.jethsportable.registry.PortableRegistry;
import com.jethsportable.storage.MachineView;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/**
 * Once a second, looks at linked machines and any machine a player just loaded by hand. Tells people
 * when a whole batch is done (not every item), when one has run out of fuel, or when its output is full.
 */
public final class MachineWatcher {
	private static final class Watch {
		final Set<UUID> players = new HashSet<>();
		boolean hadWork;
		boolean noFuelSent;
		boolean fullSent;
		boolean linked;
	}

	private static final Map<GlobalPos, Watch> WATCHES = new HashMap<>();

	private MachineWatcher() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) tick(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WATCHES.clear());
	}

	/** Called as a player closes any menu. Loading a furnace needs no Link to get its notification. */
	public static void onMenuClosed(ServerPlayer player, AbstractContainerMenu menu) {
		Container container = null;
		if (menu instanceof AbstractFurnaceMenu furnace) {
			container = ((AbstractFurnaceMenuAccessor)furnace).jethsportable$container();
		} else if (menu instanceof BrewingStandMenu brewing) {
			container = ((BrewingStandMenuAccessor)brewing).jethsportable$container();
		}
		if (!(container instanceof BlockEntity entity) || !(entity.getLevel() instanceof ServerLevel level)) return;
		if (!MachineView.hasWork(level, entity)) return;
		Watch watch = WATCHES.computeIfAbsent(GlobalPos.of(level.dimension(), entity.getBlockPos()), pos -> new Watch());
		watch.players.add(player.getUUID());
		watch.hadWork = true;
	}

	private static void tick(MinecraftServer server) {
		PortableData data = PortableData.get(server);
		int keepFrom = PortableServerConfig.get().keepMachinesLoadedFromLevel;
		Set<GlobalPos> linked = new HashSet<>();
		for (PortableBase base : data.bases()) {
			if (base.core() == null || base.links().isEmpty()) continue;
			boolean memberOnline = isMemberOnline(server, base);
			boolean keepLoaded = keepFrom > 0 && base.level() >= keepFrom && memberOnline;
			for (PortableLink link : base.links()) {
				linked.add(link.pos());
				tickLink(server, data, base, link, keepLoaded);
			}
		}
		tickHandLoaded(server, linked);
	}

	private static void tickLink(MinecraftServer server, PortableData data, PortableBase base, PortableLink link, boolean keepLoaded) {
		ServerLevel level = server.getLevel(link.pos().dimension());
		if (level == null) return;
		BlockPos pos = link.pos().pos();
		if (!level.isLoaded(pos)) {
			if (keepLoaded && link.busy()) {
				level.getChunkSource().addTicketWithRadius(PortableRegistry.MACHINE_TICKET, ChunkPos.containing(pos), 1);
			}
			return;
		}
		BlockEntity entity = level.getBlockEntity(pos);
		if (!MachineView.isMachine(entity)) return;
		Watch watch = WATCHES.computeIfAbsent(link.pos(), p -> new Watch());
		if (!watch.linked) {
			watch.linked = true;
			watch.hadWork = watch.hadWork || link.busy();
		}
		boolean work = check(server, level, entity, watch, base);
		if (work != link.busy()) {
			link.setBusy(work);
			data.setDirty();
		}
		if (work && keepLoaded) {
			level.getChunkSource().addTicketWithRadius(PortableRegistry.MACHINE_TICKET, ChunkPos.containing(pos), 1);
		}
	}

	private static void tickHandLoaded(MinecraftServer server, Set<GlobalPos> linked) {
		Iterator<Map.Entry<GlobalPos, Watch>> iterator = WATCHES.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<GlobalPos, Watch> entry = iterator.next();
			GlobalPos pos = entry.getKey();
			Watch watch = entry.getValue();
			if (linked.contains(pos)) continue;
			ServerLevel level = server.getLevel(pos.dimension());
			if (level == null) {
				iterator.remove();
				continue;
			}
			if (!level.isLoaded(pos.pos())) continue;
			BlockEntity blockEntity = level.getBlockEntity(pos.pos());
			if (!MachineView.isMachine(blockEntity) || !check(server, level, blockEntity, watch, null)) {
				iterator.remove();
			}
		}
	}

	/** Compares against last time and sends what's new. Returns whether the machine still has work. */
	private static boolean check(MinecraftServer server, ServerLevel level, BlockEntity entity, Watch watch, PortableBase base) {
		Component name = new ItemStack(entity.getBlockState().getBlock()).getHoverName();
		boolean work = MachineView.hasWork(level, entity);
		if (entity instanceof AbstractFurnaceBlockEntity furnace) {
			ItemStack input = furnace.getItem(MachineView.FURNACE_INPUT);
			ItemStack fuel = furnace.getItem(MachineView.FURNACE_FUEL);
			ItemStack output = furnace.getItem(MachineView.FURNACE_RESULT);
			ContainerData data = MachineView.data(furnace);
			if (watch.hadWork && !work && !output.isEmpty()) {
				send(server, watch, base, PortablePayloads.NOTIFY_BATCH_DONE, name, output, output.getCount());
			}
			boolean noFuel = work && data.get(0) <= 0 && fuel.isEmpty();
			if (noFuel && !watch.noFuelSent) {
				send(server, watch, base, PortablePayloads.NOTIFY_NO_FUEL, name, input, input.getCount());
			}
			watch.noFuelSent = noFuel;
			boolean full = work && !output.isEmpty() && output.getCount() >= output.getMaxStackSize();
			if (full && !watch.fullSent) {
				send(server, watch, base, PortablePayloads.NOTIFY_OUTPUT_FULL, name, output, output.getCount());
			}
			watch.fullSent = full;
		} else if (entity instanceof BrewingStandBlockEntity brewing) {
			ItemStack bottles = MachineView.bottles(brewing);
			if (watch.hadWork && !work && !bottles.isEmpty()) {
				send(server, watch, base, PortablePayloads.NOTIFY_BREWING_DONE, name, bottles, bottles.getCount());
			}
			boolean noFuel = !brewing.getItem(MachineView.BREWING_INGREDIENT).isEmpty()
				&& !bottles.isEmpty()
				&& MachineView.data(brewing).get(1) <= 0
				&& brewing.getItem(MachineView.BREWING_FUEL).isEmpty();
			if (noFuel && !watch.noFuelSent) {
				send(server, watch, base, PortablePayloads.NOTIFY_NO_FUEL, name, brewing.getItem(MachineView.BREWING_INGREDIENT), 1);
			}
			watch.noFuelSent = noFuel;
		}
		watch.hadWork = work;
		return work;
	}

	private static void send(MinecraftServer server, Watch watch, PortableBase base, int kind, Component name, ItemStack icon, int count) {
		Set<UUID> targets = new HashSet<>(watch.players);
		if (base != null) {
			targets.add(base.owner());
			targets.addAll(base.trusted().keySet());
		}
		for (UUID id : targets) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				ServerActions.notify(player, kind, name, icon.copyWithCount(1), count);
			}
		}
		watch.players.clear();
	}

	private static boolean isMemberOnline(MinecraftServer server, PortableBase base) {
		if (server.getPlayerList().getPlayer(base.owner()) != null) return true;
		for (UUID id : base.trusted().keySet()) {
			if (server.getPlayerList().getPlayer(id) != null) return true;
		}
		return false;
	}
}
