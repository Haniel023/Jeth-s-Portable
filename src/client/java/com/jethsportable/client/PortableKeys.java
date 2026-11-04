package com.jethsportable.client;

import com.jethsportable.JethsPortable;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class PortableKeys {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(JethsPortable.id("keys"));
	public static final KeyMapping OPEN_STORAGE = key("open_storage", InputConstants.KEY_K);
	public static final KeyMapping OPEN_CRAFTING = key("open_crafting", InputConstants.KEY_V);

	private PortableKeys() {
	}

	private static KeyMapping key(String name, int key) {
		return new KeyMapping("key.jethsportable." + name, InputConstants.Type.KEYBOARD, key, CATEGORY);
	}

	public static void register() {
		KeyMappingHelper.registerKeyMapping(OPEN_STORAGE);
		KeyMappingHelper.registerKeyMapping(OPEN_CRAFTING);
	}
}
