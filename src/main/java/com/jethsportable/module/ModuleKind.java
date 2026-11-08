package com.jethsportable.module;

/** What a module does when it's opened. */
public enum ModuleKind {
	/** A workstation without its own inventory: stonecutter, anvil, loom, a modded woodcutter... */
	STATION,
	/** Built in, it counts bookshelves around the Core. Linked, it uses the table's own. */
	ENCHANTING,
	/** The player's ender chest. */
	ENDER_CHEST,
	/** Unlocks linked furnaces, smokers and blast furnaces on the Machines tab. */
	FURNACE,
	/** Unlocks linked brewing stands on the Machines tab. */
	BREWING
}
