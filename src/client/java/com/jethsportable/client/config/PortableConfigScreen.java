package com.jethsportable.client.config;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Settings, built from vanilla buttons. Reached from the base page or Mod Menu. */
public final class PortableConfigScreen extends Screen {
	private final @Nullable Screen parent;

	public PortableConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("jethsportable.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		PortableClientConfig config = PortableClientConfig.get();
		int width = 150;
		int left = this.width / 2 - width - 5;
		int right = this.width / 2 + 5;
		int y = 44;
		this.toggle(left, y, "batch_done", config.notifyBatchDone, v -> config.notifyBatchDone = v);
		this.toggle(right, y, "no_fuel", config.notifyNoFuel, v -> config.notifyNoFuel = v);
		y += 24;
		this.toggle(left, y, "output_full", config.notifyOutputFull, v -> config.notifyOutputFull = v);
		this.toggle(right, y, "brewing_done", config.notifyBrewingDone, v -> config.notifyBrewingDone = v);
		y += 24;
		this.toggle(left, y, "storage_full", config.notifyStorageFull, v -> config.notifyStorageFull = v);
		this.toggle(right, y, "didnt_fit", config.notifyDidntFit, v -> config.notifyDidntFit = v);
		y += 36;
		this.toggle(left, y, "inventory_button", config.showInventoryButton, v -> config.showInventoryButton = v);
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
	}

	private void toggle(int x, int y, String key, boolean value, Consumer<Boolean> setter) {
		this.addRenderableWidget(CycleButton.onOffBuilder(value).create(x, y, 150, 20, Component.translatable("jethsportable.config." + key), (button, newValue) -> {
			setter.accept(newValue);
			PortableClientConfig.get().save();
		}));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("jethsportable.config.notifications"), this.width / 2, 32, 0xFFA0A0A0);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}
}
