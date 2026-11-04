package com.jethsportable.menu;

import com.jethsportable.item.PortableCraftingTableItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;

/**
 * The vanilla crafting menu, tied to the player rather than a table. Using the real one means the
 * vanilla screen, recipe book and anything built on them keep working.
 */
public final class PortableCraftingMenu extends CraftingMenu {
	public PortableCraftingMenu(int containerId, Inventory inventory) {
		super(containerId, inventory, ContainerLevelAccess.create(inventory.player.level(), inventory.player.blockPosition()));
	}

	@Override
	public boolean stillValid(Player player) {
		return PortableCraftingTableItem.isCarriedBy(player) || this.getCarried().getItem() instanceof PortableCraftingTableItem
			|| this.getInputGridSlots().stream().anyMatch(slot -> slot.getItem().getItem() instanceof PortableCraftingTableItem);
	}
}
