package com.jethsportable.client;

import com.jethsportable.client.config.PortableClientConfig;
import com.jethsportable.client.ui.InventoryButtons;
import com.jethsportable.client.ui.PortableToast;
import com.jethsportable.client.ui.StorageScreen;
import com.jethsportable.item.PortableCraftingTableItem;
import com.jethsportable.menu.StorageMenu;
import com.jethsportable.network.PortablePayloads;
import com.jethsportable.network.ServerActions;
import com.jethsportable.registry.PortableRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class JethsPortableClient implements ClientModInitializer {
	private static int reopenTab = -1;
	private static int waitForRemoteTicks;
	private static boolean remoteScreenOpen;

	@Override
	public void onInitializeClient() {
		PortableClientConfig.get();
		PortableKeys.register();
		MenuScreens.register(PortableRegistry.STORAGE_MENU, StorageScreen::new);
		StorageMenu.clientStorageContents = ClientStorage::fillStackedContents;
		InventoryButtons.register();

		ClientPlayNetworking.registerGlobalReceiver(PortablePayloads.BasesPayload.TYPE, (payload, context) -> ClientStorage.setBases(payload.bases()));
		ClientPlayNetworking.registerGlobalReceiver(PortablePayloads.OpenedPayload.TYPE, (payload, context) -> {
			if (!ClientStorage.enterSession(payload.session())) return;
			StorageScreen.rememberTab(payload.tab());
			if (context.client().gui.screen() instanceof StorageScreen screen) {
				screen.onOpened(payload.tab());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(PortablePayloads.ItemsPayload.TYPE, (payload, context) -> {
			if (ClientStorage.enterSession(payload.session())) ClientStorage.apply(payload);
		});
		ClientPlayNetworking.registerGlobalReceiver(PortablePayloads.StatusPayload.TYPE, (payload, context) -> {
			if (ClientStorage.enterSession(payload.session())) ClientStorage.setStatus(payload);
		});
		ClientPlayNetworking.registerGlobalReceiver(PortablePayloads.LinksPayload.TYPE, (payload, context) -> {
			if (ClientStorage.enterSession(payload.session())) ClientStorage.setLinks(payload.links());
		});
		ClientPlayNetworking.registerGlobalReceiver(PortablePayloads.MachinesPayload.TYPE, (payload, context) -> {
			if (ClientStorage.enterSession(payload.session())) ClientStorage.setMachines(payload.machines());
		});
		ClientPlayNetworking.registerGlobalReceiver(PortablePayloads.NotifyPayload.TYPE, (payload, context) -> PortableToast.show(context.client(), payload));

		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(() -> {
			ClientStorage.clearAll();
			PortableHighlights.clear();
			reopenTab = -1;
			remoteScreenOpen = false;
		}));

		ClientTickEvents.END_CLIENT_TICK.register(JethsPortableClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		StorageMenu.clientRows = StorageScreen.rowsFor(minecraft.getWindow().getGuiScaledHeight());
		LocalPlayer player = minecraft.player;
		if (player == null) return;
		PortableHighlights.tick(minecraft);
		Screen screen = minecraft.gui.screen();
		returnToStorage(screen);
		if (screen == null) {
			while (PortableKeys.OPEN_STORAGE.consumeClick()) {
				openStorage(minecraft, ServerActions.TAB_ITEMS);
			}
			while (PortableKeys.OPEN_CRAFTING.consumeClick()) {
				openCrafting(minecraft);
			}
		}
	}

	/**
	 * A chest opened from the list goes back to the list when you close it. Only starts counting once
	 * another menu has actually replaced the storage window, because the server can refuse to open one.
	 */
	private static void returnToStorage(Screen screen) {
		if (waitForRemoteTicks > 0) {
			waitForRemoteTicks--;
			if (screen != null && !(screen instanceof StorageScreen)) {
				remoteScreenOpen = true;
				waitForRemoteTicks = 0;
			} else if (waitForRemoteTicks == 0) {
				reopenTab = -1;
			}
		}
		if (remoteScreenOpen && screen == null) {
			remoteScreenOpen = false;
			int tab = reopenTab;
			reopenTab = -1;
			if (tab >= 0 && ClientPlayNetworking.canSend(PortablePayloads.OpenStoragePayload.TYPE)) {
				ClientPlayNetworking.send(new PortablePayloads.OpenStoragePayload(tab));
			}
		}
	}

	/** The storage window is about to be replaced by a chest or furnace's own menu. */
	public static void expectRemoteScreen(int tab) {
		reopenTab = tab;
		waitForRemoteTicks = 40;
		remoteScreenOpen = false;
	}

	public static void openStorage(Minecraft minecraft, int tab) {
		LocalPlayer player = minecraft.player;
		if (player == null || !ClientPlayNetworking.canSend(PortablePayloads.OpenStoragePayload.TYPE)) return;
		if (!ClientStorage.anyUsable(player)) {
			player.sendOverlayMessage(outOfRangeMessage(minecraft).copy().withStyle(ChatFormatting.RED));
			return;
		}
		ClientPlayNetworking.send(new PortablePayloads.OpenStoragePayload(tab));
	}

	public static Component outOfRangeMessage(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || ClientStorage.bases().isEmpty()) {
			return Component.translatable("jethsportable.gui.no_base");
		}
		int outside = ClientStorage.blocksOutsideRange(player);
		return outside > 0
			? Component.translatable("jethsportable.gui.blocks_out", outside)
			: Component.translatable("jethsportable.message.out_of_range");
	}

	public static void openCrafting(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || !ClientPlayNetworking.canSend(PortablePayloads.OpenCraftingPayload.TYPE)) return;
		if (PortableCraftingTableItem.isCarriedBy(player)) {
			ClientPlayNetworking.send(new PortablePayloads.OpenCraftingPayload());
		} else {
			player.sendOverlayMessage(Component.translatable("jethsportable.message.no_table").withStyle(ChatFormatting.RED));
		}
	}
}
