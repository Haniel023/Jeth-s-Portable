package com.jethsportable.client.ui;

import com.jethsportable.client.ClientStorage;
import com.jethsportable.client.JethsPortableClient;
import com.jethsportable.client.PortableKeys;
import com.jethsportable.client.config.PortableClientConfig;
import com.jethsportable.client.mixin.AbstractContainerScreenAccessor;
import com.jethsportable.item.PortableCraftingTableItem;
import com.jethsportable.network.PortablePayloads;
import com.jethsportable.network.ServerActions;
import com.jethsportable.registry.PortableRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Adds the storage button to the inventory and crafting screens, plus a crafting button to the
 * inventory when a portable crafting table is carried. They follow the screen when the recipe book
 * pushes it sideways, and stay out of sight for players who have no base yet.
 */
public final class InventoryButtons {
	private static final int WIDTH = 20;
	private static final int HEIGHT = 18;

	private InventoryButtons() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof InventoryScreen || screen instanceof CraftingScreen || screen instanceof CreativeModeInventoryScreen)) return;
			if (!ClientPlayNetworking.canSend(PortablePayloads.OpenStoragePayload.TYPE)) return;
			attach(client, (AbstractContainerScreen<?>)screen);
			ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> {
				if (PortableKeys.OPEN_STORAGE.matches(event)) JethsPortableClient.openStorage(client, ServerActions.TAB_ITEMS);
			});
		});
	}

	private static void attach(Minecraft client, AbstractContainerScreen<?> screen) {
		ItemStack storageIcon = new ItemStack(PortableRegistry.CORE_ITEM);
		ItemStack craftingIcon = new ItemStack(PortableRegistry.PORTABLE_CRAFTING_TABLE);
		Button storage = Button.builder(Component.empty(), b -> JethsPortableClient.openStorage(client, ServerActions.TAB_ITEMS)).bounds(0, 0, WIDTH, HEIGHT).build();
		Button crafting = Button.builder(Component.empty(), b -> JethsPortableClient.openCrafting(client))
			.bounds(0, 0, WIDTH, HEIGHT)
			.tooltip(Tooltip.create(Component.translatable("jethsportable.gui.open_crafting")))
			.build();
		Screens.getWidgets(screen).add(storage);
		Screens.getWidgets(screen).add(crafting);
		boolean inventory = screen instanceof InventoryScreen;
		Component[] shownTip = new Component[1];

		Runnable update = () -> {
			AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor)screen;
			LocalPlayer player = client.player;
			if (player == null) return;
			PortableClientConfig config = PortableClientConfig.get();
			storage.visible = config.showInventoryButton && !ClientStorage.bases().isEmpty();
			crafting.visible = config.showInventoryButton && inventory && PortableCraftingTableItem.isCarriedBy(player);
			place(screen, access, storage, crafting);
			boolean usable = ClientStorage.anyUsable(player);
			storage.active = usable;
			Component tip = usable ? Component.translatable("jethsportable.gui.open_storage") : JethsPortableClient.outOfRangeMessage(client);
			if (!tip.equals(shownTip[0])) {
				storage.setTooltip(Tooltip.create(tip));
				shownTip[0] = tip;
			}
		};
		// Ready before the first frame, and kept in step even when a frame limiter skips drawing.
		update.run();
		ScreenEvents.afterTick(screen).register(s -> update.run());
		ScreenEvents.afterBackground(screen).register((s, graphics, mouseX, mouseY, a) -> update.run());
		ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, a) -> {
			if (storage.visible) graphics.item(storageIcon, storage.getX() + 2, storage.getY() + 1);
			if (crafting.visible) graphics.item(craftingIcon, crafting.getX() + 2, crafting.getY() + 1);
		});
	}

	private static void place(Screen screen, AbstractContainerScreenAccessor access, Button storage, Button crafting) {
		int left = access.jethsportable$getLeftPos();
		int top = access.jethsportable$getTopPos();
		if (screen instanceof InventoryScreen) {
			storage.setPosition(left + 126, top + 61);
			crafting.setPosition(left + 148, top + 61);
		} else if (screen instanceof CraftingScreen) {
			storage.setPosition(left + 5, top + 54);
			crafting.setPosition(left + 5, top + 54);
		} else {
			storage.setPosition(left - WIDTH - 2, top + 4);
			crafting.setPosition(left - WIDTH - 2, top + 24);
		}
	}
}
