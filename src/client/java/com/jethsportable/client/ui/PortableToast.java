package com.jethsportable.client.ui;

import com.jethsportable.client.config.PortableClientConfig;
import com.jethsportable.network.PortablePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** A pop-up in the vanilla style. Several of one kind share a toast instead of stacking up the screen. */
public final class PortableToast implements Toast {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/recipe");
	private static final long DISPLAY_MS = 6000L;
	private static final int TEXT_X = 30;
	private static final int TEXT_WIDTH = 125;

	/** Counts every notification received, whichever way it gets shown. */
	public static int shownForTest;

	private final Integer kind;
	private ItemStack icon;
	private Component title;
	private Component detail;
	private int alsoCount;
	private long lastChanged;
	private boolean changed = true;
	private Visibility visibility = Visibility.SHOW;

	private PortableToast(int kind, ItemStack icon, Component title, Component detail) {
		this.kind = kind;
		this.icon = icon;
		this.title = title;
		this.detail = detail;
	}

	public static void show(Minecraft minecraft, PortablePayloads.NotifyPayload payload) {
		shownForTest++;
		if (!enabled(payload.kind())) return;
		ToastManager manager = minecraft.gui.toastManager();
		Component title = title(payload);
		Component detail = detail(payload);
		PortableToast existing = manager.getToast(PortableToast.class, Integer.valueOf(payload.kind()));
		if (existing == null) {
			manager.addToast(new PortableToast(payload.kind(), payload.icon(), title, detail));
		} else {
			existing.icon = payload.icon();
			existing.title = title;
			existing.detail = detail;
			existing.alsoCount++;
			existing.changed = true;
		}
	}

	private static boolean enabled(int kind) {
		PortableClientConfig config = PortableClientConfig.get();
		return switch (kind) {
			case PortablePayloads.NOTIFY_BATCH_DONE -> config.notifyBatchDone;
			case PortablePayloads.NOTIFY_NO_FUEL -> config.notifyNoFuel;
			case PortablePayloads.NOTIFY_OUTPUT_FULL -> config.notifyOutputFull;
			case PortablePayloads.NOTIFY_BREWING_DONE -> config.notifyBrewingDone;
			case PortablePayloads.NOTIFY_STORAGE_FULL -> config.notifyStorageFull;
			case PortablePayloads.NOTIFY_DIDNT_FIT -> config.notifyDidntFit;
			case PortablePayloads.NOTIFY_ANVIL_WORN, PortablePayloads.NOTIFY_ANVIL_BROKE -> config.notifyAnvil;
			default -> false;
		};
	}

	private static Component title(PortablePayloads.NotifyPayload payload) {
		return switch (payload.kind()) {
			case PortablePayloads.NOTIFY_BATCH_DONE -> Component.translatable("jethsportable.toast.batch_done", payload.where());
			case PortablePayloads.NOTIFY_NO_FUEL -> Component.translatable("jethsportable.toast.no_fuel", payload.where());
			case PortablePayloads.NOTIFY_OUTPUT_FULL -> Component.translatable("jethsportable.toast.output_full", payload.where());
			case PortablePayloads.NOTIFY_BREWING_DONE -> Component.translatable("jethsportable.toast.brewing_done");
			case PortablePayloads.NOTIFY_STORAGE_FULL -> Component.translatable("jethsportable.toast.storage_full");
			case PortablePayloads.NOTIFY_ANVIL_WORN -> Component.translatable("jethsportable.toast.anvil_worn");
			case PortablePayloads.NOTIFY_ANVIL_BROKE -> Component.translatable("jethsportable.toast.anvil_broke");
			default -> Component.translatable("jethsportable.toast.didnt_fit");
		};
	}

	private static Component detail(PortablePayloads.NotifyPayload payload) {
		Component name = payload.icon().getHoverName();
		return switch (payload.kind()) {
			case PortablePayloads.NOTIFY_NO_FUEL -> Component.translatable("jethsportable.toast.detail_waiting", payload.count(), name);
			case PortablePayloads.NOTIFY_STORAGE_FULL -> Component.translatable("jethsportable.toast.detail_percent", payload.count());
			case PortablePayloads.NOTIFY_DIDNT_FIT -> Component.translatable("jethsportable.toast.detail_left", payload.count(), name);
			case PortablePayloads.NOTIFY_ANVIL_WORN -> Component.translatable("jethsportable.toast.detail_anvil_worn");
			case PortablePayloads.NOTIFY_ANVIL_BROKE -> Component.translatable("jethsportable.toast.detail_anvil_broke", payload.where());
			default -> Component.translatable("jethsportable.toast.detail_count", payload.count(), name);
		};
	}

	@Override
	public Object getToken() {
		return this.kind;
	}

	@Override
	public Visibility getWantedVisibility() {
		return this.visibility;
	}

	@Override
	public void update(ToastManager manager, long fullyVisibleForMs) {
		if (this.changed) {
			this.lastChanged = fullyVisibleForMs;
			this.changed = false;
		}
		this.visibility = fullyVisibleForMs - this.lastChanged >= DISPLAY_MS * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, this.width(), this.height());
		graphics.text(font, fit(font, this.title), TEXT_X, 7, 0xFF500050, false);
		Component line = this.alsoCount > 0 ? Component.translatable("jethsportable.toast.and_more", this.detail, this.alsoCount) : this.detail;
		graphics.text(font, fit(font, line), TEXT_X, 18, 0xFF000000, false);
		graphics.fakeItem(this.icon, 8, 8);
	}

	private static String fit(Font font, Component text) {
		String plain = text.getString();
		if (font.width(plain) <= TEXT_WIDTH) return plain;
		return font.plainSubstrByWidth(plain, TEXT_WIDTH - font.width("...")) + "...";
	}
}
