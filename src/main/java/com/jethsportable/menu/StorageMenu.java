package com.jethsportable.menu;

import com.jethsportable.registry.PortableRegistry;
import com.jethsportable.storage.StorageSession;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.jspecify.annotations.Nullable;

/**
 * The storage window: a real menu with a crafting grid and the player's inventory, so vanilla clicks,
 * the recipe book and inventory mods all work. The item grid isn't slots; it's drawn from data the
 * {@link StorageSession} sends, and clicks on it go through {@code ItemActionPayload}.
 */
public final class StorageMenu extends AbstractCraftingMenu {
	public static final int RESULT_SLOT = 0;
	public static final int GRID_START = 1;
	public static final int GRID_END = 10;
	public static final int INV_START = 10;
	public static final int INV_END = 37;
	public static final int HOTBAR_END = 46;

	public static final int MIN_ROWS = 5;
	public static final int MAX_ROWS = 10;
	/** Width of the main panel, where the item grid and the inventory sit. */
	public static final int MAIN_WIDTH = 195;
	/** Where the crafting panel on its right starts, and how wide it is. */
	public static final int SIDE_X = MAIN_WIDTH + 2;
	public static final int SIDE_WIDTH = 66;
	public static final int SIDE_GRID_X = SIDE_X + 6;
	public static final int GRID_Y = 18;
	public static final int RESULT_ITEM_X = SIDE_GRID_X + 19;
	public static final int RESULT_ITEM_Y = 102;
	public static final int SIDE_BUTTONS_Y = 126;

	/** Item rows the next client-side menu is laid out for. The screen picks this from the window height. */
	public static int clientRows = 6;
	/** Adds the storage's contents to what the client's recipe book thinks is craftable. */
	public static @Nullable Consumer<StackedItemContents> clientStorageContents;

	private final Player player;
	private final @Nullable StorageSession session;
	private final int rows;
	private boolean placingRecipe;

	/** Client side. */
	public StorageMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, null, clientRows);
	}

	/** Server side. */
	public StorageMenu(int containerId, Inventory inventory, ServerPlayer player) {
		this(containerId, inventory, player, MIN_ROWS);
	}

	private StorageMenu(int containerId, Inventory inventory, @Nullable ServerPlayer serverPlayer, int rows) {
		super(PortableRegistry.STORAGE_MENU, containerId, 3, 3);
		this.player = inventory.player;
		this.rows = rows;
		this.session = serverPlayer == null ? null : new StorageSession(serverPlayer, this);
		this.addResultSlot(this.player, RESULT_ITEM_X, RESULT_ITEM_Y);
		this.addCraftingGridSlots(SIDE_GRID_X + 1, GRID_Y + 1);
		this.addStandardInventorySlots(inventory, 8, inventoryY(rows));
	}

	public static int inventoryY(int rows) {
		return GRID_Y + rows * 18 + 15;
	}

	public static int imageHeight(int rows) {
		return inventoryY(rows) + 76 + 7;
	}

	public int rows() {
		return this.rows;
	}

	public @Nullable StorageSession session() {
		return this.session;
	}

	public ItemStack[] craftingGridCopy() {
		ItemStack[] copy = new ItemStack[9];
		for (int i = 0; i < 9; i++) {
			copy[i] = this.craftSlots.getItem(i).copy();
		}
		return copy;
	}

	/** Moves what's in the crafting grid back to storage (or the inventory, if storage is full). */
	public void clearGrid() {
		if (this.session == null) return;
		for (int i = 0; i < 9; i++) {
			ItemStack stack = this.craftSlots.getItem(i);
			if (stack.isEmpty()) continue;
			int left = this.session.insert(stack.copy());
			ItemStack rest = stack.copyWithCount(left);
			this.craftSlots.setItem(i, ItemStack.EMPTY);
			if (!rest.isEmpty()) {
				this.player.getInventory().placeItemBackInInventory(rest, Prediction.SERVER_ONLY);
			}
		}
		this.broadcastChanges();
	}

	/** Puts a stack into the player's inventory, the way shift-clicking out of a chest does. */
	public void moveToInventory(ItemStack stack) {
		this.moveItemStackTo(stack, INV_START, HOTBAR_END, true);
		if (!stack.isEmpty()) {
			this.player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		}
	}

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (this.session != null) {
			this.session.tick();
		}
	}

	@Override
	public void clicked(int slotId, int buttonNum, ContainerInput input, Player player) {
		ItemStack[] before = slotId == RESULT_SLOT && this.session != null ? this.craftingGridCopy() : null;
		super.clicked(slotId, buttonNum, input, player);
		if (before != null) {
			this.refillGrid(before);
		}
	}

	/** After a craft, tops the grid back up from storage so the same recipe stays ready. */
	private void refillGrid(ItemStack[] before) {
		if (this.session == null) return;
		for (int i = 0; i < 9; i++) {
			ItemStack was = before[i];
			if (was.isEmpty()) continue;
			ItemStack now = this.craftSlots.getItem(i);
			if (!now.isEmpty() && !ItemStack.isSameItemSameComponents(now, was)) continue;
			int missing = was.getCount() - now.getCount();
			if (missing <= 0) continue;
			int got = this.session.extract(was, missing);
			if (got > 0) {
				this.craftSlots.setItem(i, was.copyWithCount(now.getCount() + got));
			}
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = this.slots.get(slotIndex);
		if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

		if (slotIndex == RESULT_SLOT) {
			ItemStack[] before = this.session == null ? null : this.craftingGridCopy();
			ItemStack moved = this.quickMoveResult(player, slot);
			if (before != null && !moved.isEmpty()) {
				this.refillGrid(before);
			}
			return moved;
		}

		ItemStack stack = slot.getItem();
		if (slotIndex >= INV_START) {
			// Into storage. The client waits for the server's answer instead of guessing.
			if (this.session == null) return ItemStack.EMPTY;
			int left = this.session.insert(stack.copy());
			if (left == stack.getCount()) return ItemStack.EMPTY;
			if (left == 0) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				stack.setCount(left);
				slot.setChanged();
			}
			return ItemStack.EMPTY;
		}

		ItemStack clicked = stack.copy();
		if (!this.moveItemStackTo(stack, INV_START, HOTBAR_END, false)) return ItemStack.EMPTY;
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == clicked.getCount()) return ItemStack.EMPTY;
		slot.onTake(player, stack);
		return clicked;
	}

	/** The same steps vanilla's crafting menu takes when you shift-click the result. */
	private ItemStack quickMoveResult(Player player, Slot slot) {
		ItemStack stack = slot.getItem();
		ItemStack clicked = stack.copy();
		stack.getItem().onCraftedBy(stack, player);
		if (!this.moveItemStackTo(stack, INV_START, HOTBAR_END, true)) return ItemStack.EMPTY;
		slot.onQuickCraft(stack, clicked);
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == clicked.getCount()) return ItemStack.EMPTY;
		slot.onTake(player, stack);
		player.drop(stack, false, Prediction.PREDICTED);
		return clicked;
	}

	@Override
	public void slotsChanged(Container container) {
		if (!this.placingRecipe && this.player instanceof ServerPlayer serverPlayer) {
			this.updateResult(serverPlayer.level(), serverPlayer, null);
		}
	}

	private void updateResult(ServerLevel level, ServerPlayer player, @Nullable RecipeHolder<CraftingRecipe> hint) {
		CraftingInput input = this.craftSlots.asCraftInput();
		ItemStack result = ItemStack.EMPTY;
		Optional<RecipeHolder<CraftingRecipe>> found = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level, hint);
		if (found.isPresent() && this.resultSlots.setRecipeUsed(player, found.get())) {
			ItemStack made = found.get().value().assemble(input);
			if (made.isItemEnabled(level.enabledFeatures())) {
				result = made;
			}
		}
		this.resultSlots.setItem(0, result);
		this.setRemoteSlot(0, result);
		player.connection.send(new ClientboundContainerSetSlotPacket(this.containerId, this.incrementStateId(), 0, result));
	}

	@Override
	public void beginPlacingRecipe() {
		this.placingRecipe = true;
	}

	@Override
	public void finishPlacingRecipe(ServerLevel level, RecipeHolder<CraftingRecipe> recipe) {
		this.placingRecipe = false;
		if (this.player instanceof ServerPlayer serverPlayer) {
			this.updateResult(level, serverPlayer, recipe);
		}
	}

	/** Recipe book clicks: lends the ingredients from storage to the inventory while vanilla places them. */
	@Override
	public PostPlaceAction handlePlacement(boolean useMaxItems, boolean allowDroppingItemsToClear, RecipeHolder<?> recipe, ServerLevel level, Inventory inventory) {
		if (this.session == null) {
			return super.handlePlacement(useMaxItems, allowDroppingItemsToClear, recipe, level, inventory);
		}
		StorageSession.Loan loan = this.session.lendForRecipe(recipe, useMaxItems, inventory);
		try {
			return super.handlePlacement(useMaxItems, allowDroppingItemsToClear, recipe, level, inventory);
		} finally {
			this.session.settle(loan, inventory);
		}
	}

	@Override
	public void fillCraftSlotsStackedContents(StackedItemContents contents) {
		super.fillCraftSlotsStackedContents(contents);
		if (this.session == null && clientStorageContents != null) {
			clientStorageContents.accept(contents);
		}
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (this.session != null) {
			this.session.close();
			this.clearContainer(player, this.craftSlots);
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return player.isAlive();
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
		return target.container != this.resultSlots && super.canTakeItemForPickAll(carried, target);
	}

	@Override
	public Slot getResultSlot() {
		return this.slots.get(RESULT_SLOT);
	}

	@Override
	public List<Slot> getInputGridSlots() {
		return this.slots.subList(GRID_START, GRID_END);
	}

	@Override
	public RecipeBookType getRecipeBookType() {
		return RecipeBookType.CRAFTING;
	}

	@Override
	protected Player owner() {
		return this.player;
	}
}
