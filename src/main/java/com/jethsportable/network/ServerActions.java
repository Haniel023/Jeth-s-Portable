package com.jethsportable.network;

import com.jethsportable.config.PortableServerConfig;
import com.jethsportable.data.PortableBase;
import com.jethsportable.data.PortableData;
import com.jethsportable.data.PortableLink;
import com.jethsportable.item.PortableCraftingTableItem;
import com.jethsportable.menu.StorageMenu;
import com.jethsportable.registry.PortableRegistry;
import com.jethsportable.remote.RemoteAccess;
import com.jethsportable.storage.MachineView;
import com.jethsportable.storage.StorageSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/** Handles every request from the client. Nothing here trusts what the client says it can do. */
public final class ServerActions {
	public static final int TAB_ITEMS = 0;
	public static final int TAB_CHESTS = 1;
	public static final int TAB_MACHINES = 2;
	public static final int TAB_BASE = 3;
	private static final int MAX_NAME_LENGTH = 32;

	private ServerActions() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> sendBases(listener.player));
		ServerTickEvents.END_SERVER_TICK.register(server -> PortableCraftingTableItem.openPending());

		ServerPlayNetworking.registerGlobalReceiver(PortablePayloads.OpenStoragePayload.TYPE, (payload, context) -> requestStorage(context.player(), payload.tab()));
		ServerPlayNetworking.registerGlobalReceiver(PortablePayloads.OpenCraftingPayload.TYPE, (payload, context) -> {
			if (PortableCraftingTableItem.isCarriedBy(context.player())) {
				PortableCraftingTableItem.open(context.player());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(PortablePayloads.SetTabPayload.TYPE, (payload, context) -> {
			StorageSession session = sessionOf(context.player());
			if (session != null) session.setTab(payload.tab());
		});
		ServerPlayNetworking.registerGlobalReceiver(PortablePayloads.ItemActionPayload.TYPE, (payload, context) -> itemAction(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(PortablePayloads.GridActionPayload.TYPE, (payload, context) -> gridAction(context.player(), payload.action()));
		ServerPlayNetworking.registerGlobalReceiver(PortablePayloads.LinkActionPayload.TYPE, (payload, context) -> linkAction(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(PortablePayloads.MachineActionPayload.TYPE, (payload, context) -> machineAction(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(PortablePayloads.BaseActionPayload.TYPE, (payload, context) -> baseAction(context.player(), payload));
	}

	public static @Nullable StorageSession sessionOf(ServerPlayer player) {
		return player.containerMenu instanceof StorageMenu menu ? menu.session() : null;
	}

	private static void requestStorage(ServerPlayer player, int tab) {
		PortableData data = PortableData.get(player.level().getServer());
		if (data.usableBy(player).isEmpty()) {
			player.sendOverlayMessage(Component.translatable("jethsportable.message.out_of_range").withStyle(ChatFormatting.RED));
			return;
		}
		openStorage(player, tab);
	}

	public static void openStorage(ServerPlayer player, int tab) {
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new StorageMenu(id, inventory, player), Component.translatable("jethsportable.storage.title")));
		StorageSession session = sessionOf(player);
		if (session != null) {
			ServerPlayNetworking.send(player, new PortablePayloads.OpenedPayload(session.id(), tab));
			session.tick();
			session.setTab(tab);
		}
	}

	private static void itemAction(ServerPlayer player, PortablePayloads.ItemActionPayload payload) {
		StorageSession session = sessionOf(player);
		if (session == null || !session.spendAction()) return;
		switch (payload.action()) {
			case PortablePayloads.ITEM_TAKE_STACK -> session.takeToCursor(payload.id(), false);
			case PortablePayloads.ITEM_TAKE_HALF -> session.takeToCursor(payload.id(), true);
			case PortablePayloads.ITEM_TAKE_TO_INVENTORY -> session.takeToInventory(payload.id());
			case PortablePayloads.ITEM_STORE_CARRIED -> session.storeCarried(false);
			case PortablePayloads.ITEM_STORE_ONE -> session.storeCarried(true);
			default -> {
			}
		}
	}

	private static void gridAction(ServerPlayer player, int action) {
		StorageSession session = sessionOf(player);
		if (session == null || !session.spendAction()) return;
		switch (action) {
			case PortablePayloads.GRID_STORE_ALL -> session.storeInventory(false);
			case PortablePayloads.GRID_STORE_MATCHING -> session.storeInventory(true);
			case PortablePayloads.GRID_CLEAR -> ((StorageMenu)player.containerMenu).clearGrid();
			default -> {
			}
		}
	}

	/** The base holding a link, if this player may use it from where they stand. */
	private static @Nullable PortableBase usableLinkBase(ServerPlayer player, GlobalPos pos) {
		PortableData data = PortableData.get(player.level().getServer());
		PortableBase base = data.baseOfLink(pos);
		if (base == null || !base.isMember(player) || !base.inRange(pos) || !data.usableBy(player).contains(base)) return null;
		return base;
	}

	private static void linkAction(ServerPlayer player, PortablePayloads.LinkActionPayload payload) {
		StorageSession session = sessionOf(player);
		if (session != null && !session.spendAction()) return;
		PortableData data = PortableData.get(player.level().getServer());
		GlobalPos pos = payload.pos();
		PortableBase base = data.baseOfLink(pos);
		if (base == null || !base.isMember(player)) return;
		switch (payload.action()) {
			case PortablePayloads.LINK_OPEN -> {
				if (usableLinkBase(player, pos) == null) return;
				if (!PortableServerConfig.get().allowRemoteOpening) {
					player.sendOverlayMessage(Component.translatable("jethsportable.message.remote_disabled").withStyle(ChatFormatting.RED));
				} else if (!RemoteAccess.open(player, base, pos)) {
					player.sendOverlayMessage(Component.translatable("jethsportable.message.cant_open").withStyle(ChatFormatting.RED));
				}
			}
			case PortablePayloads.LINK_TARGET -> {
				GlobalPos current = data.target(player.getUUID());
				data.setTarget(player.getUUID(), pos.equals(current) ? null : pos);
			}
			case PortablePayloads.LINK_RENAME -> {
				PortableLink link = base.link(pos);
				if (link != null) {
					link.setName(cleanName(payload.text()));
					data.changed(base);
				}
			}
			case PortablePayloads.LINK_UNLINK -> {
				data.removeLink(base, pos);
				player.getInventory().placeItemBackInInventory(new ItemStack(PortableRegistry.PORTABLE_LINK), Prediction.SERVER_ONLY);
			}
			default -> {
			}
		}
		if (session != null) session.refreshLinks();
	}

	private static void machineAction(ServerPlayer player, PortablePayloads.MachineActionPayload payload) {
		StorageSession session = sessionOf(player);
		if (session == null || !session.spendAction()) return;
		GlobalPos pos = payload.pos();
		PortableBase base = usableLinkBase(player, pos);
		if (base == null) return;
		ServerLevel level = player.level().getServer().getLevel(pos.dimension());
		if (level == null || !level.isLoaded(pos.pos())) return;
		BlockEntity entity = level.getBlockEntity(pos.pos());
		if (!MachineView.isMachine(entity)) return;
		switch (payload.action()) {
			case PortablePayloads.MACHINE_OPEN -> {
				if (!PortableServerConfig.get().allowRemoteOpening) {
					player.sendOverlayMessage(Component.translatable("jethsportable.message.remote_disabled").withStyle(ChatFormatting.RED));
				} else if (!RemoteAccess.open(player, base, pos)) {
					player.sendOverlayMessage(Component.translatable("jethsportable.message.cant_open").withStyle(ChatFormatting.RED));
				}
			}
			case PortablePayloads.MACHINE_COLLECT, PortablePayloads.MACHINE_COLLECT_TO_STORAGE -> {
				List<ItemStack> taken = MachineView.takeOutput(entity);
				if (taken.isEmpty()) return;
				boolean toStorage = payload.action() == PortablePayloads.MACHINE_COLLECT_TO_STORAGE;
				for (ItemStack stack : taken) {
					int left = toStorage ? session.insert(stack.copy()) : stack.getCount();
					if (left > 0) {
						player.getInventory().placeItemBackInInventory(stack.copyWithCount(left), Prediction.SERVER_ONLY);
					}
				}
				if (entity instanceof AbstractFurnaceBlockEntity furnace) {
					furnace.awardUsedRecipesAndPopExperience(player);
				}
				player.containerMenu.broadcastChanges();
				session.setTab(TAB_MACHINES);
			}
			default -> {
			}
		}
	}

	private static void baseAction(ServerPlayer player, PortablePayloads.BaseActionPayload payload) {
		MinecraftServer server = player.level().getServer();
		PortableData data = PortableData.get(server);
		PortableBase base = data.base(payload.base());
		if (base == null || !base.isMember(player)) return;
		switch (payload.action()) {
			case PortablePayloads.BASE_RENAME -> {
				base.setName(cleanName(payload.text()));
				data.changed(base);
			}
			case PortablePayloads.BASE_TRUST -> {
				if (!base.owner().equals(player.getUUID())) return;
				ServerPlayer other = server.getPlayerList().getPlayerByName(payload.text().trim());
				if (other == null) {
					player.sendOverlayMessage(Component.translatable("jethsportable.message.player_not_found", payload.text().trim()).withStyle(ChatFormatting.RED));
				} else if (!base.isMember(other)) {
					base.trusted().put(other.getUUID(), other.getGameProfile().name());
					data.changed(base);
				}
			}
			case PortablePayloads.BASE_UNTRUST -> {
				// Owners can remove anyone; a trusted player can remove themselves.
				UUID target;
				try {
					target = UUID.fromString(payload.text());
				} catch (IllegalArgumentException e) {
					return;
				}
				if (base.owner().equals(player.getUUID()) || target.equals(player.getUUID())) {
					base.trusted().remove(target);
					data.changed(base);
					sendBases(server, target);
				}
			}
			default -> {
			}
		}
	}

	private static String cleanName(String text) {
		String clean = text.strip();
		return clean.length() > MAX_NAME_LENGTH ? clean.substring(0, MAX_NAME_LENGTH) : clean;
	}

	public static void notify(ServerPlayer player, int kind, Component where, ItemStack icon, int count) {
		if (ServerPlayNetworking.canSend(player, PortablePayloads.NotifyPayload.TYPE)) {
			ServerPlayNetworking.send(player, new PortablePayloads.NotifyPayload(kind, where, icon, count));
		}
	}

	public static void sendBases(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, PortablePayloads.BasesPayload.TYPE)) return;
		PortableData data = PortableData.get(player.level().getServer());
		List<PortablePayloads.BaseInfo> infos = new ArrayList<>();
		for (PortableBase base : data.basesOf(player)) {
			infos.add(info(base, player));
		}
		ServerPlayNetworking.send(player, new PortablePayloads.BasesPayload(infos));
	}

	public static void sendBases(MinecraftServer server, PortableBase base) {
		sendBases(server, base.owner());
		for (UUID id : base.trusted().keySet()) {
			sendBases(server, id);
		}
	}

	private static void sendBases(MinecraftServer server, UUID playerId) {
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player != null) sendBases(player);
	}

	private static PortablePayloads.BaseInfo info(PortableBase base, ServerPlayer player) {
		List<PortablePayloads.TrustedInfo> trusted = new ArrayList<>();
		base.trusted().forEach((id, name) -> trusted.add(new PortablePayloads.TrustedInfo(id, name)));
		List<GlobalPos> links = new ArrayList<>();
		for (PortableLink link : base.links()) {
			links.add(link.pos());
		}
		return new PortablePayloads.BaseInfo(
			base.id(), base.name(), base.ownerName(), base.owner().equals(player.getUUID()),
			base.level(), base.range(), base.maxLinks(), Optional.ofNullable(base.core()), trusted, links
		);
	}
}
