package com.jethsportable.client.ui;

import com.jethsportable.client.ClientStorage;
import com.jethsportable.client.JethsPortableClient;
import com.jethsportable.client.PortableHighlights;
import com.jethsportable.client.PortableKeys;
import com.jethsportable.client.config.PortableClientConfig;
import com.jethsportable.client.config.PortableConfigScreen;
import com.jethsportable.client.mixin.AbstractContainerScreenAccessor;
import com.jethsportable.client.mixin.RecipeBookComponentAccessor;
import com.jethsportable.menu.StorageMenu;
import com.jethsportable.network.PortablePayloads;
import com.jethsportable.network.ServerActions;
import com.jethsportable.registry.PortableRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The storage window. The left panel changes with the tab (items, chests, machines, base); the
 * crafting grid on the right and the inventory below are always there. Everything is drawn from
 * vanilla textures, so resource packs reskin it too.
 */
public final class StorageScreen extends AbstractRecipeBookScreen<StorageMenu> {
	private static final int IMAGE_WIDTH = StorageMenu.SIDE_X + StorageMenu.SIDE_WIDTH;
	private static final int TAB_HEIGHT = 28;
	private static final int TAB_WIDTH = 27;
	private static final int ROW_HEIGHT = 20;
	private static final int GRID_X = 8;
	private static final int COLUMNS = 9;
	private static final int LIST_WIDTH = 162;
	private static final int SCROLLBAR_X = 175;
	private static final int BUTTON_SIZE = 14;
	private static final int RECIPE_BOOK_WIDTH = 147;
	private static final Identifier FURNACE_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/furnace.png");
	private static final Identifier LIT_SPRITE = Identifier.withDefaultNamespace("container/furnace/lit_progress");
	private static final Identifier BURN_SPRITE = Identifier.withDefaultNamespace("container/furnace/burn_progress");
	private static final String[] TAB_NAMES = {"items", "chests", "machines", "base"};
	/** The tab the next window opens on. */
	private static int lastTab = ServerActions.TAB_ITEMS;

	// Item stacks can't be made before a world has loaded, so these can't be static.
	private final ItemStack[] tabIcons = {
		new ItemStack(Items.BUNDLE), new ItemStack(Items.CHEST), new ItemStack(Items.FURNACE), new ItemStack(PortableRegistry.CORE_ITEM)
	};
	private final ItemStack iconTarget = new ItemStack(Items.HOPPER);
	private final ItemStack iconShow = new ItemStack(Items.SPYGLASS);
	private final ItemStack iconRename = new ItemStack(Items.NAME_TAG);
	private final ItemStack iconUnlink = new ItemStack(Items.BARRIER);
	private final ItemStack iconCollect = new ItemStack(Items.BUNDLE);
	private final ItemStack iconToStorage = new ItemStack(Items.CHEST);

	private final CraftingRecipeBookComponent book;
	private final int rows;
	private int tab = lastTab;
	private int scroll;
	private boolean draggingScrollbar;
	private boolean skipRelease;
	private List<ClientStorage.Item> itemView = new ArrayList<>();
	private int itemViewVersion = -1;
	private int refreshCooldown;
	private String search = "";
	private ClientStorage.@Nullable Item hoveredItem;
	private ItemStack hoveredStack = ItemStack.EMPTY;
	private @Nullable Component hoverText;

	private EditBox searchBox;
	private EditBox nameBox;
	private EditBox trustBox;
	private Button storeAllButton;
	private Button matchingButton;
	private Button clearButton;
	private Button sortButton;
	private Button orderButton;
	private Button notificationsButton;
	private Button addTrustButton;

	/** What the name box is renaming: a link, or a base when this is null and renamingBase is set. */
	private @Nullable GlobalPos renamingLink;
	private PortablePayloads.@Nullable BaseInfo renamingBase;
	private int baseIndex;

	public StorageScreen(StorageMenu menu, Inventory inventory, Component title) {
		this(menu, new CraftingRecipeBookComponent(menu), inventory, title);
	}

	private StorageScreen(StorageMenu menu, CraftingRecipeBookComponent book, Inventory inventory, Component title) {
		super(menu, book, inventory, title);
		this.book = book;
		this.rows = menu.rows();
		((AbstractContainerScreenAccessor)(Object)this).jethsportable$setImageWidth(IMAGE_WIDTH);
		((AbstractContainerScreenAccessor)(Object)this).jethsportable$setImageHeight(StorageMenu.imageHeight(this.rows));
		this.inventoryLabelY = StorageMenu.inventoryY(this.rows) - 11;
	}

	/** How many item rows fit a window this tall, leaving room for the tabs above. */
	public static int rowsFor(int guiHeight) {
		int rows = (guiHeight - TAB_HEIGHT - StorageMenu.imageHeight(0) - 2) / 18;
		return Math.max(StorageMenu.MIN_ROWS, Math.min(StorageMenu.MAX_ROWS, rows));
	}

	/** The packet with the tab can arrive either side of the one that creates this screen. */
	public static void rememberTab(int tab) {
		lastTab = tab;
	}

	/** Where the recipe book's left edge goes so that it and the window sit side by side, centred. */
	public static int bookOffset() {
		return IMAGE_WIDTH / 2;
	}

	private int computeLeft() {
		if (this.book.isVisible() && this.width >= 379) {
			return (this.width - RECIPE_BOOK_WIDTH) / 2 - bookOffset() + RECIPE_BOOK_WIDTH;
		}
		return (this.width - this.imageWidth) / 2;
	}

	private int computeTop() {
		return Math.max(TAB_HEIGHT, (this.height - this.imageHeight + TAB_HEIGHT) / 2);
	}

	@Override
	protected void init() {
		super.init();
		this.leftPos = this.computeLeft();
		this.topPos = this.computeTop();

		this.searchBox = new EditBox(this.font, 0, 0, 92, 12, Component.translatable("jethsportable.gui.search"));
		this.searchBox.setHint(Component.translatable("jethsportable.gui.search").withStyle(ChatFormatting.DARK_GRAY));
		this.searchBox.setMaxLength(64);
		this.searchBox.setValue(this.search);
		this.searchBox.setResponder(text -> {
			this.search = text.toLowerCase(Locale.ROOT);
			this.scroll = 0;
			this.itemViewVersion = -1;
			this.refreshCooldown = 0;
		});
		this.addRenderableWidget(this.searchBox);

		this.nameBox = new EditBox(this.font, 0, 0, 92, 12, Component.translatable("jethsportable.gui.rename"));
		this.nameBox.setMaxLength(32);
		this.nameBox.visible = false;
		this.addRenderableWidget(this.nameBox);

		this.trustBox = new EditBox(this.font, 0, 0, 112, 14, Component.translatable("jethsportable.gui.player_name"));
		this.trustBox.setMaxLength(16);
		this.trustBox.setHint(Component.translatable("jethsportable.gui.player_name").withStyle(ChatFormatting.DARK_GRAY));
		this.addRenderableWidget(this.trustBox);
		this.addTrustButton = this.addRenderableWidget(Button.builder(Component.translatable("jethsportable.gui.add"), b -> this.addTrusted()).bounds(0, 0, 46, 14).build());

		int buttonWidth = 54;
		this.storeAllButton = this.addRenderableWidget(Button.builder(Component.translatable("jethsportable.gui.store_all"), b -> this.gridAction(PortablePayloads.GRID_STORE_ALL))
			.bounds(0, 0, buttonWidth, 16).tooltip(Tooltip.create(Component.translatable("jethsportable.gui.store_all.tip"))).build());
		this.matchingButton = this.addRenderableWidget(Button.builder(Component.translatable("jethsportable.gui.matching"), b -> this.gridAction(PortablePayloads.GRID_STORE_MATCHING))
			.bounds(0, 0, buttonWidth, 16).tooltip(Tooltip.create(Component.translatable("jethsportable.gui.matching.tip"))).build());
		this.clearButton = this.addRenderableWidget(Button.builder(Component.translatable("jethsportable.gui.clear_grid"), b -> this.gridAction(PortablePayloads.GRID_CLEAR))
			.bounds(0, 0, buttonWidth, 16).tooltip(Tooltip.create(Component.translatable("jethsportable.gui.clear_grid.tip"))).build());
		this.sortButton = this.addRenderableWidget(Button.builder(this.sortName(), b -> this.cycleSort())
			.bounds(0, 0, 40, 16).tooltip(Tooltip.create(Component.translatable("jethsportable.gui.sort.tip"))).build());
		this.orderButton = this.addRenderableWidget(Button.builder(this.orderName(), b -> this.flipOrder())
			.bounds(0, 0, 13, 16).tooltip(Tooltip.create(Component.translatable("jethsportable.gui.order.tip"))).build());
		this.notificationsButton = this.addRenderableWidget(Button.builder(Component.translatable("jethsportable.gui.settings"), b -> this.minecraft.gui.setScreen(new PortableConfigScreen(this)))
			.bounds(0, 0, buttonWidth, 16).tooltip(Tooltip.create(Component.translatable("jethsportable.gui.settings.tip"))).build());

		this.layoutWidgets();
		this.applyTabVisibility();
	}

	private void layoutWidgets() {
		this.searchBox.setPosition(this.leftPos + 79, this.topPos + 4);
		this.nameBox.setPosition(this.leftPos + 79, this.topPos + 4);
		int sideX = this.leftPos + StorageMenu.SIDE_GRID_X;
		int y = this.topPos + StorageMenu.SIDE_BUTTONS_Y;
		this.storeAllButton.setPosition(sideX, y);
		this.matchingButton.setPosition(sideX, y + 18);
		this.clearButton.setPosition(sideX, y + 36);
		this.sortButton.setPosition(sideX, y + 54);
		this.orderButton.setPosition(sideX + 41, y + 54);
		this.notificationsButton.setPosition(sideX, y);
		int trustY = this.topPos + StorageMenu.GRID_Y + this.rows * 18 - 14;
		this.trustBox.setPosition(this.leftPos + GRID_X, trustY);
		this.addTrustButton.setPosition(this.leftPos + GRID_X + 116, trustY);
	}

	private void applyTabVisibility() {
		boolean items = this.tab == ServerActions.TAB_ITEMS;
		boolean base = this.tab == ServerActions.TAB_BASE;
		this.searchBox.visible = !base && this.renamingLink == null && this.renamingBase == null;
		this.storeAllButton.visible = items;
		this.matchingButton.visible = items;
		this.clearButton.visible = items;
		this.sortButton.visible = items;
		this.orderButton.visible = items;
		this.notificationsButton.visible = base;
		PortablePayloads.BaseInfo shown = base ? this.currentBase() : null;
		boolean canTrust = shown != null && shown.isOwner();
		this.trustBox.visible = canTrust;
		this.addTrustButton.visible = canTrust;
	}

	@Override
	protected ScreenPosition getRecipeBookButtonPosition() {
		return new ScreenPosition(this.computeLeft() + StorageMenu.SIDE_X + 4, this.computeTop() + 76);
	}

	@Override
	protected void onRecipeBookButtonClick() {
		this.leftPos = this.computeLeft();
		this.layoutWidgets();
	}

	/** The server has opened (or reopened) this window on a tab. */
	public void onOpened(int tab) {
		this.selectTab(tab, false);
	}

	private void selectTab(int tab, boolean tellServer) {
		this.tab = tab;
		lastTab = tab;
		this.scroll = 0;
		this.cancelRename();
		this.applyTabVisibility();
		if (tellServer) ClientPlayNetworking.send(new PortablePayloads.SetTabPayload(tab));
	}

	private void gridAction(int action) {
		ClientPlayNetworking.send(new PortablePayloads.GridActionPayload(action));
	}

	// Sorting and searching

	private Component sortName() {
		return switch (PortableClientConfig.get().sort) {
			case PortableClientConfig.SORT_NAME -> Component.translatable("jethsportable.gui.sort.name");
			case PortableClientConfig.SORT_MOD -> Component.translatable("jethsportable.gui.sort.mod");
			default -> Component.translatable("jethsportable.gui.sort.count");
		};
	}

	private Component orderName() {
		return Component.literal(PortableClientConfig.get().sortReversed ? "▲" : "▼");
	}

	private void cycleSort() {
		PortableClientConfig config = PortableClientConfig.get();
		config.sort = (config.sort + 1) % 3;
		config.save();
		this.sortButton.setMessage(this.sortName());
		this.itemViewVersion = -1;
		this.refreshCooldown = 0;
	}

	private void flipOrder() {
		PortableClientConfig config = PortableClientConfig.get();
		config.sortReversed = !config.sortReversed;
		config.save();
		this.orderButton.setMessage(this.orderName());
		this.itemViewVersion = -1;
		this.refreshCooldown = 0;
	}

	private void refreshItemView() {
		this.itemViewVersion = ClientStorage.version();
		List<String> words = this.search.isBlank() ? List.of() : List.of(this.search.trim().split("\\s+"));
		List<ClientStorage.Item> result = new ArrayList<>();
		for (ClientStorage.Item item : ClientStorage.items()) {
			if (item.count > 0 && matches(item, words)) result.add(item);
		}
		PortableClientConfig config = PortableClientConfig.get();
		Comparator<ClientStorage.Item> byName = Comparator.comparing(ClientStorage.Item::lowerName);
		Comparator<ClientStorage.Item> order = switch (config.sort) {
			case PortableClientConfig.SORT_NAME -> byName;
			case PortableClientConfig.SORT_MOD -> Comparator.comparing(ClientStorage.Item::mod).thenComparing(byName);
			default -> Comparator.<ClientStorage.Item>comparingLong(i -> -i.count).thenComparing(byName);
		};
		result.sort(config.sortReversed ? order.reversed() : order);
		this.itemView = result;
		this.clampScroll();
	}

	/** Words must all match the name. {@code @mod} matches the mod, {@code #tag} an item tag, {@code -word} excludes. */
	private static boolean matches(ClientStorage.Item item, List<String> words) {
		for (String word : words) {
			boolean match;
			boolean negate = word.startsWith("-") && word.length() > 1;
			String text = negate ? word.substring(1) : word;
			if (text.startsWith("@")) {
				match = item.mod().contains(text.substring(1));
			} else if (text.startsWith("#")) {
				String tag = text.substring(1);
				match = item.stack.getItem().builtInRegistryHolder().tags().anyMatch(key -> key.location().getPath().contains(tag));
			} else {
				match = item.lowerName().contains(text);
			}
			if (match == negate) return false;
		}
		return true;
	}

	private boolean nameMatches(String name) {
		return this.search.isBlank() || name.toLowerCase(Locale.ROOT).contains(this.search.trim());
	}

	private List<PortablePayloads.LinkInfo> linkView() {
		List<PortablePayloads.LinkInfo> result = new ArrayList<>();
		for (PortablePayloads.LinkInfo link : ClientStorage.links()) {
			if (this.nameMatches(this.linkName(link))) result.add(link);
		}
		return result;
	}

	private String linkName(PortablePayloads.LinkInfo link) {
		return link.name().isEmpty() ? link.icon().getHoverName().getString() : link.name();
	}

	private List<PortablePayloads.MachineInfo> machineView() {
		List<PortablePayloads.MachineInfo> result = new ArrayList<>();
		for (PortablePayloads.MachineInfo machine : ClientStorage.machines()) {
			String name = machine.name().isEmpty() ? machine.icon().getHoverName().getString() : machine.name();
			if (this.nameMatches(name)) result.add(machine);
		}
		return result;
	}

	private PortablePayloads.@Nullable BaseInfo currentBase() {
		if (this.minecraft == null || this.minecraft.player == null) return null;
		List<PortablePayloads.BaseInfo> bases = ClientStorage.basesForPage(this.minecraft.player);
		if (bases.isEmpty()) return null;
		this.baseIndex = Math.floorMod(this.baseIndex, bases.size());
		return bases.get(this.baseIndex);
	}

	private int listRows() {
		return this.rows * 18 / ROW_HEIGHT;
	}

	private int contentRows() {
		return switch (this.tab) {
			case ServerActions.TAB_ITEMS -> (this.itemView.size() + COLUMNS - 1) / COLUMNS;
			case ServerActions.TAB_CHESTS -> this.linkView().size();
			case ServerActions.TAB_MACHINES -> this.machineView().size();
			default -> {
				PortablePayloads.BaseInfo base = this.currentBase();
				yield base == null ? 0 : base.trusted().size();
			}
		};
	}

	private int visibleRows() {
		return switch (this.tab) {
			case ServerActions.TAB_ITEMS -> this.rows;
			case ServerActions.TAB_BASE -> this.trustedRows();
			default -> this.listRows();
		};
	}

	private int maxScroll() {
		return Math.max(0, this.contentRows() - this.visibleRows());
	}

	private void clampScroll() {
		this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
	}

	// Drawing

	@Override
	public void containerTick() {
		super.containerTick();
		if (this.refreshCooldown > 0) this.refreshCooldown--;
		if (this.itemViewVersion != ClientStorage.version() && this.refreshCooldown == 0) {
			boolean changed = this.itemViewVersion != -1;
			this.refreshItemView();
			this.refreshCooldown = 4;
			if (changed && this.book.isVisible()) {
				((RecipeBookComponentAccessor)this.book).jethsportable$updateStackedContents();
			}
		}
		this.applyTabVisibility();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		for (int i = 0; i < TAB_NAMES.length; i++) {
			if (i != this.tab) this.extractTab(graphics, i, false);
		}
		VanillaGui.panel(graphics, x, y, StorageMenu.MAIN_WIDTH, this.imageHeight);
		VanillaGui.panel(graphics, x + StorageMenu.SIDE_X, y, StorageMenu.SIDE_WIDTH, this.imageHeight);
		this.extractTab(graphics, this.tab, true);

		// Crafting panel
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				VanillaGui.slot(graphics, x + StorageMenu.SIDE_GRID_X + column * 18, y + StorageMenu.GRID_Y + row * 18);
			}
		}
		VanillaGui.arrowDown(graphics, x + StorageMenu.SIDE_X + 34, y + 75);
		VanillaGui.bigSlot(graphics, x + StorageMenu.RESULT_ITEM_X - 5, y + StorageMenu.RESULT_ITEM_Y - 5);

		// Inventory
		int inventoryY = y + StorageMenu.inventoryY(this.rows);
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < COLUMNS; column++) {
				VanillaGui.slot(graphics, x + 7 + column * 18, inventoryY - 1 + row * 18);
			}
		}
		for (int column = 0; column < COLUMNS; column++) {
			VanillaGui.slot(graphics, x + 7 + column * 18, inventoryY + 57);
		}

		// Item grid slots belong under the items, so they're drawn here with the rest of the panel
		if (this.tab == ServerActions.TAB_ITEMS) {
			for (int row = 0; row < this.rows; row++) {
				for (int column = 0; column < COLUMNS; column++) {
					VanillaGui.slot(graphics, x + GRID_X - 1 + column * 18, y + StorageMenu.GRID_Y - 1 + row * 18);
				}
			}
		}
		if (this.tab != ServerActions.TAB_BASE || this.trustedRows() > 0) {
			int scrollMax = this.maxScroll();
			int barY = this.tab == ServerActions.TAB_BASE ? this.trustedTop() : StorageMenu.GRID_Y - 1;
			int barH = this.tab == ServerActions.TAB_BASE ? this.trustedRows() * 11 + 1 : this.rows * 18 + 2;
			if (barH >= 17) {
				VanillaGui.scrollbar(graphics, x + SCROLLBAR_X, y + barY, barH, scrollMax == 0 ? 0 : (float)this.scroll / scrollMax, scrollMax > 0);
			}
		}
	}

	private void extractTab(GuiGraphicsExtractor graphics, int index, boolean selected) {
		int tabX = this.leftPos + index * TAB_WIDTH;
		int tabY = this.topPos - TAB_HEIGHT;
		Identifier sprite = Identifier.withDefaultNamespace("container/creative_inventory/tab_top_" + (selected ? "selected" : "unselected") + "_" + (index + 1));
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, tabX, tabY, 26, 32);
		graphics.item(this.tabIcons[index], tabX + 5, tabY + 9);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		int mx = xm - this.leftPos;
		int my = ym - this.topPos;
		this.hoveredItem = null;
		this.hoveredStack = ItemStack.EMPTY;
		this.hoverText = null;

		graphics.text(this.font, this.title, 8, 6, VanillaGui.TEXT_DARK, false);
		graphics.text(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, VanillaGui.TEXT_DARK, false);
		graphics.text(this.font, Component.translatable("jethsportable.gui.crafting"), StorageMenu.SIDE_GRID_X, 6, VanillaGui.TEXT_DARK, false);
		this.extractStatusLine(graphics);

		switch (this.tab) {
			case ServerActions.TAB_ITEMS -> this.extractItems(graphics, mx, my);
			case ServerActions.TAB_CHESTS -> this.extractLinks(graphics, mx, my);
			case ServerActions.TAB_MACHINES -> this.extractMachines(graphics, mx, my);
			default -> this.extractBase(graphics, mx, my);
		}

		for (int i = 0; i < TAB_NAMES.length; i++) {
			if (VanillaGui.inside(mx, my, i * TAB_WIDTH, -TAB_HEIGHT, 26, TAB_HEIGHT)) {
				this.hoverText = Component.translatable("jethsportable.gui.tab." + TAB_NAMES[i]);
			}
		}
	}

	private void extractStatusLine(GuiGraphicsExtractor graphics) {
		PortablePayloads.StatusPayload status = ClientStorage.status();
		if (status.totalSlots() <= 0) return;
		String text = Component.translatable("jethsportable.gui.status", status.chests(), status.usedSlots() * 100 / status.totalSlots()).getString();
		graphics.text(this.font, text, 170 - this.font.width(text), this.inventoryLabelY, VanillaGui.TEXT_DARK, false);
	}

	private void extractCentered(GuiGraphicsExtractor graphics, Component text) {
		int y = StorageMenu.GRID_Y + this.rows * 9 - 4;
		List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(text, LIST_WIDTH - 8);
		int lineY = y - (lines.size() - 1) * 5;
		for (net.minecraft.util.FormattedCharSequence line : lines) {
			graphics.text(this.font, line, GRID_X + (LIST_WIDTH - this.font.width(line)) / 2, lineY, VanillaGui.TEXT_LIGHT, true);
			lineY += 10;
		}
	}

	private void extractItems(GuiGraphicsExtractor graphics, int mx, int my) {
		int first = this.scroll * COLUMNS;
		for (int row = 0; row < this.rows; row++) {
			for (int column = 0; column < COLUMNS; column++) {
				int index = first + row * COLUMNS + column;
				int x = GRID_X + column * 18;
				int y = StorageMenu.GRID_Y + row * 18;
				boolean hovered = VanillaGui.inside(mx, my, x - 1, y - 1, 18, 18);
				if (hovered) VanillaGui.slotHighlightBack(graphics, x, y);
				if (index < this.itemView.size()) {
					ClientStorage.Item item = this.itemView.get(index);
					graphics.item(item.stack, x, y);
					graphics.itemDecorations(this.font, item.stack, x, y, null);
					this.extractCount(graphics, item.count, x, y);
					if (hovered) this.hoveredItem = item;
				}
				if (hovered) VanillaGui.slotHighlightFront(graphics, x, y);
			}
		}
		if (this.itemView.isEmpty()) {
			PortablePayloads.StatusPayload status = ClientStorage.status();
			if (!status.inRange()) {
				this.extractCentered(graphics, JethsPortableClient.outOfRangeMessage(this.minecraft));
			} else if (ClientStorage.items().isEmpty() && status.chests() == 0) {
				this.extractCentered(graphics, Component.translatable("jethsportable.gui.no_chests"));
			} else if (!this.search.isBlank()) {
				this.extractCentered(graphics, Component.translatable("jethsportable.gui.no_results"));
			} else {
				this.extractCentered(graphics, Component.translatable("jethsportable.gui.empty"));
			}
		}
	}

	/** Like a stack's own count, but past two digits it shrinks to stay inside the slot. */
	private void extractCount(GuiGraphicsExtractor graphics, long count, int x, int y) {
		if (count <= 1) return;
		String text = shortCount(count);
		if (text.length() <= 2) {
			graphics.text(this.font, text, x + 17 - this.font.width(text), y + 9, VanillaGui.TEXT_LIGHT, true);
			return;
		}
		float scale = 0.75F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 17, y + 11);
		graphics.pose().scale(scale, scale);
		graphics.text(this.font, text, -this.font.width(text), 0, VanillaGui.TEXT_LIGHT, true);
		graphics.pose().popMatrix();
	}

	/** Big numbers, short enough to fit on a slot: 1.2k, 34k, 5.6M. Tooltips show the exact count. */
	public static String shortCount(long count) {
		if (count < 1000) return Long.toString(count);
		if (count < 10_000) return String.format(Locale.ROOT, "%.1fk", Math.floor(count / 100.0) / 10.0);
		if (count < 1_000_000) return count / 1000 + "k";
		if (count < 10_000_000) return String.format(Locale.ROOT, "%.1fM", Math.floor(count / 100_000.0) / 10.0);
		if (count < 1_000_000_000) return count / 1_000_000 + "M";
		return count / 1_000_000_000 + "G";
	}

	private void extractLinks(GuiGraphicsExtractor graphics, int mx, int my) {
		List<PortablePayloads.LinkInfo> links = this.linkView();
		if (links.isEmpty()) {
			this.extractCentered(graphics, this.search.isBlank() ? Component.translatable("jethsportable.gui.no_chests") : Component.translatable("jethsportable.gui.no_results"));
			return;
		}
		int textWidth = LIST_WIDTH - 21 - 4 * (BUTTON_SIZE + 1) - 2;
		for (int i = 0; i < this.listRows() && this.scroll + i < links.size(); i++) {
			PortablePayloads.LinkInfo link = links.get(this.scroll + i);
			int y = StorageMenu.GRID_Y + i * ROW_HEIGHT;
			boolean rowHovered = VanillaGui.inside(mx, my, GRID_X - 1, y - 1, LIST_WIDTH + 1, ROW_HEIGHT - 1);
			if (rowHovered) graphics.fill(GRID_X - 1, y - 1, GRID_X + LIST_WIDTH, y + 18, 0x20000000);
			if (link.target()) graphics.outline(GRID_X - 1, y - 1, LIST_WIDTH + 1, 19, 0xFFB08A20);
			VanillaGui.slot(graphics, GRID_X - 1, y - 1);
			graphics.item(link.icon(), GRID_X, y);
			String name = this.font.plainSubstrByWidth(this.linkName(link), textWidth);
			graphics.text(this.font, name, GRID_X + 20, y, VanillaGui.TEXT_DARK, false);
			this.extractLinkState(graphics, link, GRID_X + 20, y + 11, textWidth);

			int buttonX = GRID_X + LIST_WIDTH - 4 * (BUTTON_SIZE + 1) + 1;
			ItemStack[] icons = {this.iconTarget, this.iconShow, this.iconRename, this.iconUnlink};
			String[] tips = {link.target() ? "target_off" : "target_on", "show", "rename", "unlink"};
			boolean onButton = false;
			for (int b = 0; b < 4; b++) {
				int bx = buttonX + b * (BUTTON_SIZE + 1);
				boolean hovered = VanillaGui.inside(mx, my, bx, y + 2, BUTTON_SIZE, BUTTON_SIZE);
				VanillaGui.smallIconButton(graphics, bx, y + 2, BUTTON_SIZE, icons[b], hovered, true);
				if (hovered) {
					this.hoverText = Component.translatable("jethsportable.gui.link." + tips[b]);
					onButton = true;
				}
			}
			if (rowHovered && !onButton) {
				this.hoverText = this.linkTooltip(link);
			}
		}
	}

	private void extractLinkState(GuiGraphicsExtractor graphics, PortablePayloads.LinkInfo link, int x, int y, int width) {
		switch (link.state()) {
			case PortablePayloads.STATE_OK -> {
				int barWidth = width - 2;
				graphics.fill(x, y + 1, x + barWidth, y + 4, 0xFF373737);
				if (link.total() > 0) {
					int fill = link.used() * barWidth / link.total();
					int percent = link.used() * 100 / link.total();
					int color = percent >= 90 ? 0xFFE04040 : percent >= 60 ? 0xFFE0C040 : 0xFF50C050;
					graphics.fill(x, y + 1, x + fill, y + 4, color);
				}
			}
			case PortablePayloads.STATE_LOADING -> graphics.text(this.font, Component.translatable("jethsportable.gui.loading"), x, y - 1, 0xFF505050, false);
			case PortablePayloads.STATE_MISSING -> graphics.text(this.font, Component.translatable("jethsportable.gui.missing"), x, y - 1, VanillaGui.TEXT_DARK_RED, false);
			default -> graphics.text(this.font, Component.translatable("jethsportable.gui.out_of_range"), x, y - 1, VanillaGui.TEXT_DARK_RED, false);
		}
	}

	private Component linkTooltip(PortablePayloads.LinkInfo link) {
		return Component.translatable("jethsportable.gui.link.tip", this.linkName(link), link.pos().pos().toShortString(), link.used(), link.total(), this.distanceTo(link.pos()));
	}

	private String distanceTo(GlobalPos pos) {
		if (this.minecraft.player == null || !pos.dimension().equals(this.minecraft.player.level().dimension())) {
			return Component.translatable("jethsportable.gui.other_dimension_short").getString();
		}
		return (int)Math.sqrt(pos.pos().distSqr(this.minecraft.player.blockPosition())) + "m";
	}

	private void extractMachines(GuiGraphicsExtractor graphics, int mx, int my) {
		List<PortablePayloads.MachineInfo> machines = this.machineView();
		if (machines.isEmpty()) {
			this.extractCentered(graphics, this.search.isBlank() ? Component.translatable("jethsportable.gui.no_machines") : Component.translatable("jethsportable.gui.no_results"));
			return;
		}
		for (int i = 0; i < this.listRows() && this.scroll + i < machines.size(); i++) {
			PortablePayloads.MachineInfo machine = machines.get(this.scroll + i);
			int y = StorageMenu.GRID_Y + i * ROW_HEIGHT;
			VanillaGui.slot(graphics, GRID_X - 1, y - 1);
			graphics.item(machine.icon(), GRID_X, y);
			boolean iconHovered = VanillaGui.inside(mx, my, GRID_X - 1, y - 1, 18, 18);
			if (iconHovered) {
				String name = machine.name().isEmpty() ? machine.icon().getHoverName().getString() : machine.name();
				this.hoverText = Component.translatable("jethsportable.gui.machine.tip", name, machine.pos().pos().toShortString(), this.distanceTo(machine.pos()));
			}
			if (machine.state() != PortablePayloads.STATE_OK) {
				Component state = Component.translatable(machine.state() == PortablePayloads.STATE_LOADING ? "jethsportable.gui.loading" : "jethsportable.gui.missing");
				graphics.text(this.font, state, GRID_X + 20, y + 5, VanillaGui.TEXT_DARK_RED, false);
				continue;
			}
			this.extractMachineSlot(graphics, machine.input(), GRID_X + 18, y, mx, my);
			this.extractMachineSlot(graphics, machine.fuel(), GRID_X + 36, y, mx, my);
			graphics.blit(RenderPipelines.GUI_TEXTURED, FURNACE_TEXTURE, GRID_X + 55, y + 1, 56.0F, 36.0F, 14, 14, 256, 256);
			if (machine.burn() > 0) {
				int height = Math.min(14, machine.burn() * 13 / 100 + 1);
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LIT_SPRITE, 14, 14, 0, 14 - height, GRID_X + 55, y + 1 + 14 - height, 14, height);
			}
			graphics.blit(RenderPipelines.GUI_TEXTURED, FURNACE_TEXTURE, GRID_X + 70, y, 79.0F, 34.0F, 24, 16, 256, 256);
			if (machine.progress() > 0) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BURN_SPRITE, 24, 16, 0, 0, GRID_X + 70, y, machine.progress() * 24 / 100, 16);
			}
			this.extractMachineSlot(graphics, machine.output(), GRID_X + 95, y, mx, my);

			int collectX = GRID_X + LIST_WIDTH - 2 * (BUTTON_SIZE + 1) + 1;
			boolean canCollect = !machine.output().isEmpty() && (!machine.brewing() || machine.progress() == 0);
			boolean collectHovered = VanillaGui.inside(mx, my, collectX, y + 2, BUTTON_SIZE, BUTTON_SIZE);
			boolean storageHovered = VanillaGui.inside(mx, my, collectX + BUTTON_SIZE + 1, y + 2, BUTTON_SIZE, BUTTON_SIZE);
			VanillaGui.smallIconButton(graphics, collectX, y + 2, BUTTON_SIZE, this.iconCollect, collectHovered, canCollect);
			VanillaGui.smallIconButton(graphics, collectX + BUTTON_SIZE + 1, y + 2, BUTTON_SIZE, this.iconToStorage, storageHovered, canCollect);
			if (collectHovered) this.hoverText = Component.translatable("jethsportable.gui.machine.collect");
			if (storageHovered) this.hoverText = Component.translatable("jethsportable.gui.machine.to_storage");
		}
	}

	private void extractMachineSlot(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int mx, int my) {
		VanillaGui.slot(graphics, x - 1, y - 1);
		if (stack.isEmpty()) return;
		graphics.item(stack, x, y);
		graphics.itemDecorations(this.font, stack, x, y, null);
		if (VanillaGui.inside(mx, my, x - 1, y - 1, 18, 18)) this.hoveredStack = stack;
	}

	private int trustedTop() {
		return StorageMenu.GRID_Y + 56;
	}

	private int trustedRows() {
		int bottom = StorageMenu.GRID_Y + this.rows * 18 - 16;
		return Math.max(0, (bottom - this.trustedTop()) / 11);
	}

	private void extractBase(GuiGraphicsExtractor graphics, int mx, int my) {
		PortablePayloads.BaseInfo base = this.currentBase();
		if (base == null) {
			this.extractCentered(graphics, Component.translatable("jethsportable.gui.no_base"));
			return;
		}
		int y = StorageMenu.GRID_Y;
		VanillaGui.slot(graphics, GRID_X - 1, y - 1);
		graphics.item(this.tabIcons[ServerActions.TAB_BASE], GRID_X, y);
		String name = base.name().isEmpty() ? Component.translatable("jethsportable.gui.base_default", base.ownerName()).getString() : base.name();
		int nameWidth = LIST_WIDTH - 21 - 2 * (BUTTON_SIZE + 1) - 16;
		graphics.text(this.font, this.font.plainSubstrByWidth(name, nameWidth), GRID_X + 20, y, VanillaGui.TEXT_DARK, false);
		graphics.text(this.font, Component.translatable("jethsportable.gui.level", base.level()), GRID_X + 20, y + 10, 0xFF505050, false);

		int renameX = GRID_X + LIST_WIDTH - BUTTON_SIZE;
		boolean renameHovered = VanillaGui.inside(mx, my, renameX, y + 2, BUTTON_SIZE, BUTTON_SIZE);
		VanillaGui.smallIconButton(graphics, renameX, y + 2, BUTTON_SIZE, this.iconRename, renameHovered, true);
		if (renameHovered) this.hoverText = Component.translatable("jethsportable.gui.base.rename");
		if (ClientStorage.bases().size() > 1) {
			int nextX = renameX - BUTTON_SIZE - 1;
			boolean nextHovered = VanillaGui.inside(mx, my, nextX, y + 2, BUTTON_SIZE, BUTTON_SIZE);
			VanillaGui.textButton(graphics, this.font, nextX, y + 2, BUTTON_SIZE, BUTTON_SIZE, Component.literal(">"), nextHovered, true);
			if (nextHovered) this.hoverText = Component.translatable("jethsportable.gui.base.next", ClientStorage.bases().size());
		}

		Component range = base.range() < 0 ? Component.translatable("jethsportable.gui.anywhere") : Component.translatable("jethsportable.gui.blocks", base.range());
		String links = base.maxLinks() < 0 ? Integer.toString(base.links().size()) : base.links().size() + "/" + base.maxLinks();
		y += 21;
		graphics.text(this.font, Component.translatable("jethsportable.gui.base.summary", range, links), GRID_X, y, VanillaGui.TEXT_DARK, false);
		y += 10;
		if (base.level() < 6) {
			ItemStack next = new ItemStack(PortableRegistry.upgradeForLevel(base.level() + 1));
			String label = Component.translatable("jethsportable.gui.base.next_upgrade", next.getHoverName()).getString();
			graphics.text(this.font, this.font.plainSubstrByWidth(label, LIST_WIDTH), GRID_X, y, VanillaGui.TEXT_DARK, false);
			if (VanillaGui.inside(mx, my, GRID_X, y, LIST_WIDTH, 9)) this.hoveredStack = next;
		} else {
			graphics.text(this.font, Component.translatable("jethsportable.gui.base.max_level"), GRID_X, y, VanillaGui.TEXT_DARK, false);
		}
		y += 10;
		Component trustedTitle = base.isOwner() ? Component.translatable("jethsportable.gui.base.trusted") : Component.translatable("jethsportable.gui.base.owner", base.ownerName());
		graphics.text(this.font, trustedTitle, GRID_X, y + 5, VanillaGui.TEXT_DARK, false);

		int top = this.trustedTop();
		List<PortablePayloads.TrustedInfo> trusted = base.trusted();
		for (int i = 0; i < this.trustedRows() && this.scroll + i < trusted.size(); i++) {
			PortablePayloads.TrustedInfo player = trusted.get(this.scroll + i);
			int rowY = top + i * 11;
			graphics.text(this.font, player.name(), GRID_X + 2, rowY + 2, 0xFF505050, false);
			boolean own = this.minecraft.player != null && player.id().equals(this.minecraft.player.getUUID());
			if (base.isOwner() || own) {
				int removeX = GRID_X + LIST_WIDTH - 10;
				boolean hovered = VanillaGui.inside(mx, my, removeX, rowY, 10, 10);
				VanillaGui.textButton(graphics, this.font, removeX, rowY, 10, 10, Component.literal("x"), hovered, true);
				if (hovered) this.hoverText = Component.translatable("jethsportable.gui.base.untrust", player.name());
			}
		}
		if (trusted.isEmpty() && base.isOwner() && this.trustedRows() > 0) {
			graphics.text(this.font, Component.translatable("jethsportable.gui.base.none").withStyle(ChatFormatting.ITALIC), GRID_X + 2, top + 2, 0xFF606060, false);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (this.hoveredItem != null) {
			List<Component> lines = new ArrayList<>(this.getTooltipFromContainerItem(this.hoveredItem.stack));
			lines.add(1, Component.translatable("jethsportable.gui.in_storage", String.format(Locale.ROOT, "%,d", this.hoveredItem.count)).withStyle(ChatFormatting.GOLD));
			graphics.setTooltipForNextFrame(this.font, lines, this.hoveredItem.stack.getTooltipImage(), mouseX, mouseY);
		} else if (!this.hoveredStack.isEmpty()) {
			graphics.setTooltipForNextFrame(this.font, this.getTooltipFromContainerItem(this.hoveredStack), this.hoveredStack.getTooltipImage(), mouseX, mouseY);
		} else if (this.hoverText != null) {
			graphics.setTooltipForNextFrame(this.font, this.font.split(this.hoverText, 180), mouseX, mouseY);
		}
	}

	// Input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mx = (int)Math.floor(event.x()) - this.leftPos;
		int my = (int)Math.floor(event.y()) - this.topPos;

		for (int i = 0; i < TAB_NAMES.length; i++) {
			if (VanillaGui.inside(mx, my, i * TAB_WIDTH, -TAB_HEIGHT, 26, TAB_HEIGHT)) {
				if (i != this.tab) this.selectTab(i, true);
				VanillaGui.clickSound(this.minecraft);
				return true;
			}
		}
		if (this.isOverScrollbar(mx, my) && this.maxScroll() > 0) {
			this.draggingScrollbar = true;
			this.scrollToMouse(my);
			return true;
		}
		boolean handled = switch (this.tab) {
			case ServerActions.TAB_ITEMS -> this.clickItems(event, mx, my);
			case ServerActions.TAB_CHESTS -> this.clickLinks(event, mx, my);
			case ServerActions.TAB_MACHINES -> this.clickMachines(event, mx, my);
			default -> this.clickBase(event, mx, my);
		};
		if (handled) {
			this.skipRelease = true;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean isOverScrollbar(int mx, int my) {
		return VanillaGui.inside(mx, my, SCROLLBAR_X, StorageMenu.GRID_Y - 1, 14, this.rows * 18 + 2);
	}

	private void scrollToMouse(int my) {
		float position = (my - (StorageMenu.GRID_Y - 1) - 7.5F) / (this.rows * 18 + 2 - 15.0F);
		this.scroll = Math.round(Math.max(0, Math.min(1, position)) * this.maxScroll());
	}

	private boolean clickItems(MouseButtonEvent event, int mx, int my) {
		if (!VanillaGui.inside(mx, my, GRID_X - 1, StorageMenu.GRID_Y - 1, COLUMNS * 18, this.rows * 18)) return false;
		int column = (mx - GRID_X + 1) / 18;
		int row = (my - StorageMenu.GRID_Y + 1) / 18;
		int index = (this.scroll + row) * COLUMNS + column;
		ClientStorage.Item item = index < this.itemView.size() ? this.itemView.get(index) : null;
		boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
		if (!this.menu.getCarried().isEmpty()) {
			ClientPlayNetworking.send(new PortablePayloads.ItemActionPayload(-1, right ? PortablePayloads.ITEM_STORE_ONE : PortablePayloads.ITEM_STORE_CARRIED));
		} else if (item != null) {
			int action = right ? PortablePayloads.ITEM_TAKE_HALF : this.minecraft.hasShiftDown() ? PortablePayloads.ITEM_TAKE_TO_INVENTORY : PortablePayloads.ITEM_TAKE_STACK;
			ClientPlayNetworking.send(new PortablePayloads.ItemActionPayload(item.id, action));
		}
		return true;
	}

	private boolean clickLinks(MouseButtonEvent event, int mx, int my) {
		if (!VanillaGui.inside(mx, my, GRID_X - 1, StorageMenu.GRID_Y - 1, LIST_WIDTH + 1, this.listRows() * ROW_HEIGHT)) return false;
		List<PortablePayloads.LinkInfo> links = this.linkView();
		int row = (my - StorageMenu.GRID_Y + 1) / ROW_HEIGHT;
		if (this.scroll + row >= links.size()) return true;
		PortablePayloads.LinkInfo link = links.get(this.scroll + row);
		int y = StorageMenu.GRID_Y + row * ROW_HEIGHT;
		int buttonX = GRID_X + LIST_WIDTH - 4 * (BUTTON_SIZE + 1) + 1;
		VanillaGui.clickSound(this.minecraft);
		for (int b = 0; b < 4; b++) {
			if (VanillaGui.inside(mx, my, buttonX + b * (BUTTON_SIZE + 1), y + 2, BUTTON_SIZE, BUTTON_SIZE)) {
				switch (b) {
					case 0 -> ClientPlayNetworking.send(new PortablePayloads.LinkActionPayload(link.pos(), PortablePayloads.LINK_TARGET, ""));
					case 1 -> {
						PortableHighlights.show(link.pos());
						this.onClose();
					}
					case 2 -> this.startRename(link.pos(), null, this.linkName(link));
					default -> ClientPlayNetworking.send(new PortablePayloads.LinkActionPayload(link.pos(), PortablePayloads.LINK_UNLINK, ""));
				}
				return true;
			}
		}
		if (link.state() == PortablePayloads.STATE_OK) {
			JethsPortableClient.expectRemoteScreen(this.tab);
			ClientPlayNetworking.send(new PortablePayloads.LinkActionPayload(link.pos(), PortablePayloads.LINK_OPEN, ""));
		}
		return true;
	}

	private boolean clickMachines(MouseButtonEvent event, int mx, int my) {
		if (!VanillaGui.inside(mx, my, GRID_X - 1, StorageMenu.GRID_Y - 1, LIST_WIDTH + 1, this.listRows() * ROW_HEIGHT)) return false;
		List<PortablePayloads.MachineInfo> machines = this.machineView();
		int row = (my - StorageMenu.GRID_Y + 1) / ROW_HEIGHT;
		if (this.scroll + row >= machines.size()) return true;
		PortablePayloads.MachineInfo machine = machines.get(this.scroll + row);
		int y = StorageMenu.GRID_Y + row * ROW_HEIGHT;
		int collectX = GRID_X + LIST_WIDTH - 2 * (BUTTON_SIZE + 1) + 1;
		if (VanillaGui.inside(mx, my, collectX, y + 2, BUTTON_SIZE, BUTTON_SIZE)) {
			VanillaGui.clickSound(this.minecraft);
			ClientPlayNetworking.send(new PortablePayloads.MachineActionPayload(machine.pos(), PortablePayloads.MACHINE_COLLECT));
		} else if (VanillaGui.inside(mx, my, collectX + BUTTON_SIZE + 1, y + 2, BUTTON_SIZE, BUTTON_SIZE)) {
			VanillaGui.clickSound(this.minecraft);
			ClientPlayNetworking.send(new PortablePayloads.MachineActionPayload(machine.pos(), PortablePayloads.MACHINE_COLLECT_TO_STORAGE));
		} else if (machine.state() == PortablePayloads.STATE_OK) {
			VanillaGui.clickSound(this.minecraft);
			JethsPortableClient.expectRemoteScreen(this.tab);
			ClientPlayNetworking.send(new PortablePayloads.MachineActionPayload(machine.pos(), PortablePayloads.MACHINE_OPEN));
		}
		return true;
	}

	private boolean clickBase(MouseButtonEvent event, int mx, int my) {
		PortablePayloads.BaseInfo base = this.currentBase();
		if (base == null) return false;
		int y = StorageMenu.GRID_Y;
		int renameX = GRID_X + LIST_WIDTH - BUTTON_SIZE;
		if (VanillaGui.inside(mx, my, renameX, y + 2, BUTTON_SIZE, BUTTON_SIZE)) {
			VanillaGui.clickSound(this.minecraft);
			this.startRename(null, base, base.name());
			return true;
		}
		if (ClientStorage.bases().size() > 1 && VanillaGui.inside(mx, my, renameX - BUTTON_SIZE - 1, y + 2, BUTTON_SIZE, BUTTON_SIZE)) {
			VanillaGui.clickSound(this.minecraft);
			this.baseIndex++;
			this.scroll = 0;
			this.applyTabVisibility();
			return true;
		}
		int top = this.trustedTop();
		List<PortablePayloads.TrustedInfo> trusted = base.trusted();
		for (int i = 0; i < this.trustedRows() && this.scroll + i < trusted.size(); i++) {
			PortablePayloads.TrustedInfo player = trusted.get(this.scroll + i);
			if (VanillaGui.inside(mx, my, GRID_X + LIST_WIDTH - 10, top + i * 11, 10, 10)) {
				boolean own = this.minecraft.player != null && player.id().equals(this.minecraft.player.getUUID());
				if (base.isOwner() || own) {
					VanillaGui.clickSound(this.minecraft);
					ClientPlayNetworking.send(new PortablePayloads.BaseActionPayload(base.id(), PortablePayloads.BASE_UNTRUST, player.id().toString()));
				}
				return true;
			}
		}
		return false;
	}

	private void addTrusted() {
		PortablePayloads.BaseInfo base = this.currentBase();
		String name = this.trustBox.getValue().trim();
		if (base != null && !name.isEmpty()) {
			ClientPlayNetworking.send(new PortablePayloads.BaseActionPayload(base.id(), PortablePayloads.BASE_TRUST, name));
			this.trustBox.setValue("");
		}
	}

	private void startRename(@Nullable GlobalPos link, PortablePayloads.@Nullable BaseInfo base, String current) {
		this.renamingLink = link;
		this.renamingBase = base;
		this.nameBox.setValue(current);
		this.nameBox.visible = true;
		this.searchBox.visible = false;
		this.setFocused(this.nameBox);
		this.nameBox.setFocused(true);
	}

	private void commitRename() {
		String name = this.nameBox.getValue();
		if (this.renamingLink != null) {
			ClientPlayNetworking.send(new PortablePayloads.LinkActionPayload(this.renamingLink, PortablePayloads.LINK_RENAME, name));
		} else if (this.renamingBase != null) {
			ClientPlayNetworking.send(new PortablePayloads.BaseActionPayload(this.renamingBase.id(), PortablePayloads.BASE_RENAME, name));
		}
		this.cancelRename();
	}

	private void cancelRename() {
		this.renamingLink = null;
		this.renamingBase = null;
		if (this.nameBox != null) {
			this.nameBox.visible = false;
			this.nameBox.setFocused(false);
		}
		if (this.getFocused() == this.nameBox) this.setFocused(null);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.draggingScrollbar) {
			this.scrollToMouse((int)event.y() - this.topPos);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.draggingScrollbar) {
			this.draggingScrollbar = false;
			return true;
		}
		if (this.skipRelease) {
			this.skipRelease = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int mx = (int)x - this.leftPos;
		int my = (int)y - this.topPos;
		if (VanillaGui.inside(mx, my, 0, 0, StorageMenu.MAIN_WIDTH, StorageMenu.GRID_Y + this.rows * 18)) {
			this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int)Math.signum(scrollY)));
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		boolean enter = event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER;
		if (this.nameBox.isFocused()) {
			if (enter) {
				this.commitRename();
				return true;
			}
			if (event.isEscape()) {
				this.cancelRename();
				return true;
			}
			this.nameBox.keyPressed(event);
			return true;
		}
		if (this.trustBox.isFocused()) {
			if (enter) {
				this.addTrusted();
				return true;
			}
			if (event.isEscape()) {
				this.trustBox.setFocused(false);
				this.setFocused(null);
				return true;
			}
			this.trustBox.keyPressed(event);
			return true;
		}
		if (this.searchBox.isFocused()) {
			if (event.isEscape() || enter) {
				this.searchBox.setFocused(false);
				this.setFocused(null);
				return true;
			}
			this.searchBox.keyPressed(event);
			return true;
		}
		if (event.key() == InputConstants.KEY_F && event.hasControlDown() && this.searchBox.visible) {
			this.setFocused(this.searchBox);
			this.searchBox.setFocused(true);
			return true;
		}
		if (PortableKeys.OPEN_STORAGE.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	public List<ClientStorage.Item> visibleItems() {
		return this.itemView;
	}

	public int currentTab() {
		return this.tab;
	}

	// For the automated in-game test

	public void setSearchForTest(String text) {
		this.searchBox.setValue(text);
	}

	public void selectTabForTest(int tab) {
		this.selectTab(tab, true);
	}

	public void refreshForTest() {
		this.refreshItemView();
	}

	public String searchTextForTest() {
		return this.searchBox.getValue();
	}

	public void setNameForTest(String name) {
		this.nameBox.setValue(name);
	}

	public int[] searchBoxCenter() {
		return new int[] {this.searchBox.getX() + 20, this.searchBox.getY() + 6};
	}

	public int[] linkButtonCenter(int row, int button) {
		int x = this.leftPos + GRID_X + LIST_WIDTH - 4 * (BUTTON_SIZE + 1) + 1 + button * (BUTTON_SIZE + 1) + BUTTON_SIZE / 2;
		return new int[] {x, this.topPos + StorageMenu.GRID_Y + row * ROW_HEIGHT + 2 + BUTTON_SIZE / 2};
	}

	public int cellCenterX(int column) {
		return this.leftPos + GRID_X + column * 18 + 8;
	}

	public int cellCenterY(int row) {
		return this.topPos + StorageMenu.GRID_Y + row * 18 + 8;
	}

	public int tabCenterX(int tab) {
		return this.leftPos + tab * TAB_WIDTH + 13;
	}

	public int tabCenterY() {
		return this.topPos - TAB_HEIGHT / 2;
	}
}
