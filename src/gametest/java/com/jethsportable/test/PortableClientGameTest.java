package com.jethsportable.test;

import com.jethsportable.JethsPortable;
import com.jethsportable.block.PortableCoreBlock;
import com.jethsportable.client.ClientStorage;
import com.jethsportable.client.PortableHighlights;
import com.jethsportable.client.PortableKeys;
import com.jethsportable.client.config.PortableClientConfig;
import com.jethsportable.client.ui.PortableToast;
import com.jethsportable.client.ui.StorageScreen;
import com.jethsportable.data.CoreData;
import com.jethsportable.data.PortableBase;
import com.jethsportable.data.PortableData;
import com.jethsportable.data.PortableLink;
import com.jethsportable.menu.PortableCraftingMenu;
import com.jethsportable.menu.StorageMenu;
import com.jethsportable.module.ModuleSlot;
import com.jethsportable.module.Modules;
import com.jethsportable.network.PortablePayloads;
import com.jethsportable.network.ServerActions;
import com.jethsportable.registry.PortableRegistry;
import com.jethsportable.storage.StorageSession;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Plays the mod in the real game: singleplayer, a dedicated server and a reload. Screenshots go to
 * build/run/clientGameTest/screenshots and results to build/run/clientGameTest/jethsportable-test-report.txt.
 */
public class PortableClientGameTest implements FabricClientGameTest {
	private static final int Y = 180;
	private static final BlockPos CORE = new BlockPos(2, Y, 0);
	private static final BlockPos CHEST_A = new BlockPos(-3, Y, 3);
	private static final BlockPos CHEST_B = new BlockPos(-1, Y, 3);
	private static final BlockPos CHEST_C = new BlockPos(1, Y, 3);
	private static final BlockPos DOUBLE_R = new BlockPos(3, Y, 3);
	private static final BlockPos DOUBLE_L = new BlockPos(4, Y, 3);
	private static final BlockPos BARREL = new BlockPos(-3, Y, -3);
	private static final BlockPos SHULKER = new BlockPos(-1, Y, -3);
	private static final BlockPos COPPER = new BlockPos(1, Y, -3);
	private static final BlockPos BLAST_FURNACE = new BlockPos(3, Y, -3);
	private static final BlockPos BREWING = new BlockPos(5, Y, -3);
	private static final BlockPos HAND_FURNACE = new BlockPos(7, Y, -3);

	private final List<String> report = new ArrayList<>();
	private int failures;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try {
			ctx.runOnClient(mc -> {
				mc.options.pauseOnLostFocus = false;
				PortableClientConfig.get();
			});
			TestWorldSave save;
			try (TestSingleplayerContext sp = ctx.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(s -> {
					s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
					s.setSeed("jethsportable");
				})
				.create()) {
				try {
					sp.getConnection().waitForChunksRender();
					this.prepareArea(ctx, sp.getServer());
					this.portableCrafting(ctx, sp);
					this.coreAndLinks(ctx, sp);
					this.recipesExist(ctx, sp);
					this.inventoryButtons(ctx, sp);
					this.storageWindow(ctx, sp);
					this.craftingFromStorage(ctx, sp);
					this.duplicationCheck(ctx, sp);
					this.chestsTab(ctx, sp);
					this.machines(ctx, sp);
					this.modules(ctx, sp);
					this.levels(ctx, sp);
					this.breakingTheCore(ctx, sp);
					this.keepingChunksLoaded(ctx, sp);
					this.sharing(ctx, sp);
					this.largeWindow(ctx);
					this.gallery(ctx, sp);
					this.stress(ctx, sp);
					save = sp.getWorldSave();
				} catch (Throwable t) {
					// Logged here because closing the world afterwards can hang with some mods installed.
					JethsPortable.LOGGER.error("[portable-test] stopped by an error", t);
					this.report.add("ERROR " + t);
					this.writeReport();
					ctx.takeScreenshot("jp_error");
					throw t;
				}
			}
			this.reload(ctx, save);
			this.dedicated(ctx);
			if (this.failures > 0) {
				throw new AssertionError(this.failures + " checks failed, see jethsportable-test-report.txt");
			}
		} finally {
			this.writeReport();
		}
	}

	private void prepareArea(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("gamerule advance_time false");
		// The test teleports around a platform in the sky; falling off it shouldn't end the run.
		server.runCommand("effect give @a minecraft:resistance infinite 255 true");
		server.runCommand("effect give @a minecraft:saturation infinite 0 true");
		server.runCommand("time set day");
		server.runCommand("weather clear");
		server.runCommand("tp @a 0 " + Y + " 0");
		ctx.waitTicks(10);
		server.runCommand("fill -24 " + (Y - 1) + " -24 24 " + (Y - 1) + " 24 smooth_stone");
		// Again, since the player started falling while the platform wasn't there yet.
		server.runCommand("tp @a 0 " + Y + " 0");
		ctx.waitTicks(10);
	}

	private void portableCrafting(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		TestServerContext server = sp.getServer();
		server.runCommand("clear @a");
		server.runCommand("give @a jethsportable:portable_crafting_table");
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.getInput().lookAt(0, 0);
		ctx.waitTicks(2);

		// Right click in hand opens the vanilla crafting screen.
		ctx.getInput().pressKey(o -> o.keyUse);
		ctx.waitFor(mc -> mc.gui.screen() instanceof CraftingScreen, 100);
		this.check("portable table opens the crafting screen from the hand", true);
		boolean realMenu = server.computeOnServer(s -> player(s).containerMenu instanceof PortableCraftingMenu);
		this.check("server uses the portable crafting menu", realMenu);
		server.runOnServer(s -> player(s).containerMenu.getSlot(1).set(new ItemStack(Items.OAK_LOG, 3)));
		ctx.waitTicks(3);
		boolean crafted = server.computeOnServer(s -> player(s).containerMenu.getSlot(0).getItem().is(Items.OAK_PLANKS));
		this.check("the grid crafts like a real table", crafted);
		ctx.takeScreenshot("jp_01_portable_crafting");

		// Walking away doesn't close it, the way it would at a table.
		server.runCommand("tp @a 40 " + Y + " 40");
		ctx.waitTicks(30);
		this.check("stays open after moving away", ctx.computeOnClient(mc -> mc.gui.screen() instanceof CraftingScreen));
		server.runCommand("tp @a 0 " + Y + " 0");
		ctx.waitTicks(5);

		this.closeScreen(ctx);
		ctx.waitTicks(5);
		int logs = server.computeOnServer(s -> player(s).getInventory().countItem(Items.OAK_LOG));
		this.check("closing returns the grid to the inventory (" + logs + " logs)", logs == 3);

		// Right click on the item in an inventory slot.
		ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> {
			int hotbarSlot = 36;
			mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, hotbarSlot, 1, ContainerInput.PICKUP, mc.player);
		});
		ctx.waitFor(mc -> mc.gui.screen() instanceof CraftingScreen, 100);
		this.check("right click in an inventory slot opens it", true);
		this.closeScreen(ctx);
		ctx.waitTicks(5);

		// The key opens it too.
		ctx.getInput().pressKey(PortableKeys.OPEN_CRAFTING);
		ctx.waitFor(mc -> mc.gui.screen() instanceof CraftingScreen, 100);
		this.check("the key opens it", true);
		this.closeScreen(ctx);
		ctx.waitTicks(5);

		// Without the item, the server refuses.
		server.runCommand("clear @a");
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new PortablePayloads.OpenCraftingPayload()));
		ctx.waitTicks(10);
		this.check("the server refuses without the item", ctx.computeOnClient(mc -> mc.gui.screen() == null));
	}

	private void coreAndLinks(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		TestServerContext server = sp.getServer();
		server.runCommand("give @a jethsportable:portable_core_block");
		ctx.waitTicks(3);
		ctx.getInput().lookAt(new BlockPos(3, Y - 1, 0));
		ctx.waitTicks(3);
		ctx.getInput().pressKey(o -> o.keyUse);
		ctx.waitTicks(10);
		boolean placed = server.computeOnServer(s -> s.overworld().getBlockState(CORE).is(PortableRegistry.CORE_BLOCK));
		this.check("the Core places where you look", placed);
		boolean hasBase = server.computeOnServer(s -> base(s) != null && CORE.equals(base(s).core().pos()) && base(s).isMember(player(s)));
		this.check("placing it starts a base owned by the player", hasBase);
		ctx.waitFor(mc -> !ClientStorage.bases().isEmpty(), 40);
		this.check("the client hears about the base", true);

		String[] blocks = {
			"chest", "chest", "chest", "chest[type=right,facing=south]", "chest[type=left,facing=south]", "barrel",
			"shulker_box", "copper_chest", "blast_furnace", "brewing_stand", "furnace"
		};
		BlockPos[] positions = {CHEST_A, CHEST_B, CHEST_C, DOUBLE_R, DOUBLE_L, BARREL, SHULKER, COPPER, BLAST_FURNACE, BREWING, HAND_FURNACE};
		for (int i = 0; i < blocks.length; i++) {
			BlockPos pos = positions[i];
			server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:" + blocks[i]);
		}
		ctx.waitTicks(3);

		// One link with the real right click, on a chest that would normally just open.
		server.runCommand("give @a jethsportable:portable_link 8");
		ctx.waitTicks(3);
		ctx.getInput().lookAt(CHEST_B);
		ctx.waitTicks(3);
		ctx.getInput().pressKey(o -> o.keyUse);
		ctx.waitTicks(10);
		this.check("linking doesn't open the chest", ctx.computeOnClient(mc -> mc.gui.screen() == null));
		int links = server.computeOnServer(s -> base(s).links().size());
		this.check("right clicking with a Link links the chest (" + links + ")", links == 1);
		int linksLeft = server.computeOnServer(s -> player(s).getInventory().countItem(PortableRegistry.PORTABLE_LINK));
		this.check("the Link is used up (" + linksLeft + " left)", linksLeft == 7);
		boolean sameChestStays = server.computeOnServer(s -> s.overworld().getBlockState(CHEST_B).is(net.minecraft.world.level.block.Blocks.CHEST));
		this.check("the chest stays a vanilla chest", sameChestStays);
		ctx.waitTicks(2);
		ctx.takeScreenshot("jp_02_link_outline");

		// Using it again unlinks and gives it back.
		ctx.getInput().pressKey(o -> o.keyUse);
		ctx.waitTicks(10);
		int afterUnlink = server.computeOnServer(s -> base(s).links().size());
		int backAgain = server.computeOnServer(s -> player(s).getInventory().countItem(PortableRegistry.PORTABLE_LINK));
		this.check("using it again unlinks and returns the Link", afterUnlink == 0 && backAgain == 8);

		server.runOnServer(s -> {
			for (BlockPos pos : List.of(CHEST_A, CHEST_B, CHEST_C, DOUBLE_R, BARREL, SHULKER, COPPER, BLAST_FURNACE, BREWING)) {
				link(s, pos);
			}
		});
		int linked = server.computeOnServer(s -> base(s).links().size());
		this.check("all kinds of container link (" + linked + ")", linked == 9);

		// A double chest is one link, whichever half you use.
		InteractionResult otherHalf = server.computeOnServer(s -> link(s, DOUBLE_L));
		int stillLinked = server.computeOnServer(s -> base(s).links().size());
		this.check("using the other half of a double chest unlinks the same one", stillLinked == 8);
		server.runOnServer(s -> link(s, DOUBLE_L));
		this.log("other half result: " + otherHalf);

		// Blocks with no inventory are refused, and a Core won't link to itself.
		server.runCommand("setblock 9 " + Y + " 3 minecraft:jukebox");
		ctx.waitTicks(2);
		InteractionResult jukebox = server.computeOnServer(s -> link(s, new BlockPos(9, Y, 3)));
		this.check("a jukebox can't be linked (" + jukebox + ")", server.computeOnServer(s -> base(s).links().size()) == 9);
		server.runCommand("setblock 9 " + Y + " 3 air");

		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			put(level, CHEST_A, new ItemStack(Items.STONE, 64), new ItemStack(Items.STONE, 64), new ItemStack(Items.DIAMOND, 10));
			put(level, CHEST_B, new ItemStack(Items.STONE, 32), new ItemStack(Items.OAK_LOG, 40), new ItemStack(Items.IRON_INGOT, 25));
			put(level, CHEST_C, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.BREAD, 12), new ItemStack(Items.TORCH, 48));
			put(level, BARREL, new ItemStack(Items.COAL, 30), new ItemStack(Items.REDSTONE, 50));
			put(level, SHULKER, new ItemStack(Items.ARROW, 20));
			put(level, COPPER, new ItemStack(Items.COPPER_INGOT, 15));
		});
	}

	/** Every recipe and its recipe book unlock has to have loaded. A typo in the JSON would only log a warning. */
	private void recipesExist(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		String[] names = {"portable_crafting_table", "portable_core_block", "portable_link", "iron_range_upgrade", "gold_range_upgrade", "diamond_range_upgrade", "netherite_range_upgrade", "nether_star_range_upgrade"};
		for (String name : names) {
			boolean found = sp.getServer().computeOnServer(s -> {
				Identifier id = JethsPortable.id(name);
				var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id);
				return s.getRecipeManager().byKey(key).isPresent() && s.getAdvancements().get(JethsPortable.id("recipes/" + name)) != null;
			});
			this.check("recipe and unlock load for " + name, found);
		}
	}

	private void inventoryButtons(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		TestServerContext server = sp.getServer();
		server.runCommand("clear @a");
		server.runCommand("give @a jethsportable:portable_crafting_table");
		ctx.waitTicks(3);

		// The storage button, with the mouse.
		ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		ctx.waitTicks(5);
		int[] storageAt = ctx.computeOnClient(mc -> this.inventoryButtonCenter(mc, 126, 61));
		this.moveToGui(ctx, storageAt[0], storageAt[1]);
		ctx.waitTicks(2);
		ctx.takeScreenshot("jp_03_inventory_button");
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitFor(mc -> mc.gui.screen() instanceof StorageScreen, 60);
		this.check("the inventory's storage button opens the window", true);
		this.closeScreen(ctx);
		ctx.waitTicks(5);

		// The crafting button next to it.
		ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		ctx.waitTicks(5);
		int[] craftingAt = ctx.computeOnClient(mc -> this.inventoryButtonCenter(mc, 148, 61));
		this.moveToGui(ctx, craftingAt[0], craftingAt[1]);
		ctx.waitTicks(2);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitFor(mc -> mc.gui.screen() instanceof CraftingScreen, 60);
		this.check("the inventory's crafting button opens the table", true);

		// From the crafting screen, the storage button again.
		ctx.waitTicks(5);
		int[] fromCrafting = ctx.computeOnClient(mc -> this.inventoryButtonCenter(mc, 5, 54));
		this.moveToGui(ctx, fromCrafting[0], fromCrafting[1]);
		ctx.waitTicks(2);
		ctx.takeScreenshot("jp_04_crafting_button");
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitFor(mc -> mc.gui.screen() instanceof StorageScreen, 60);
		this.check("the crafting screen has the storage button too", true);
		this.closeScreen(ctx);
		ctx.waitTicks(5);

		// The key works from inside the inventory.
		ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		ctx.waitTicks(5);
		ctx.getInput().pressKey(PortableKeys.OPEN_STORAGE);
		ctx.waitFor(mc -> mc.gui.screen() instanceof StorageScreen, 60);
		this.check("the key works from the inventory", true);
		ctx.getInput().pressKey(PortableKeys.OPEN_STORAGE);
		ctx.waitFor(mc -> mc.gui.screen() == null, 60);
		this.check("and closes the window when pressed again", true);
		ctx.waitTicks(5);

		// Out of range the button stays, greyed out.
		server.runCommand("tp @a 60 " + Y + " 60");
		ctx.waitTicks(10);
		ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		ctx.waitTicks(5);
		int[] greyAt = ctx.computeOnClient(mc -> this.inventoryButtonCenter(mc, 126, 61));
		this.moveToGui(ctx, greyAt[0], greyAt[1]);
		ctx.waitTicks(3);
		ctx.takeScreenshot("jp_05_inventory_button_out_of_range");
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(5);
		this.check("out of range the button does nothing", ctx.computeOnClient(mc -> mc.gui.screen() instanceof InventoryScreen));
		this.closeScreen(ctx);
		server.runCommand("tp @a 0 " + Y + " 0");
		ctx.waitTicks(10);
	}

	private int[] inventoryButtonCenter(Minecraft mc, int x, int y) {
		var access = (com.jethsportable.client.mixin.AbstractContainerScreenAccessor)mc.gui.screen();
		return new int[] {access.jethsportable$getLeftPos() + x + 10, access.jethsportable$getTopPos() + y + 9};
	}

	private void storageWindow(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		TestServerContext server = sp.getServer();
		server.runCommand("clear @a");
		ctx.waitTicks(3);

		ctx.getInput().pressKey(PortableKeys.OPEN_STORAGE);
		ctx.waitFor(mc -> mc.gui.screen() instanceof StorageScreen, 100);
		this.check("the key opens the storage window", true);
		ctx.waitFor(mc -> this.clientCount(Items.STONE) == 160, 100);
		this.check("the window merges every chest (160 stone)", true);
		this.check("every kind of container is read", this.clientCount(ctx, Items.COAL) == 30 && this.clientCount(ctx, Items.ARROW) == 20 && this.clientCount(ctx, Items.COPPER_INGOT) == 15);
		ctx.waitFor(mc -> this.statusChests() == 7 && ClientStorage.status().machines() == 2, 60);
		this.check("furnaces aren't counted as storage", true);
		ctx.waitTicks(6);
		ctx.takeScreenshot("jp_10_items");

		// Real mouse clicks. Stone sorts first, by count.
		this.moveToCell(ctx, 0, 0);
		ctx.waitTicks(2);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitFor(mc -> mc.player.containerMenu.getCarried().is(Items.STONE), 40);
		int carried = ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().getCount());
		this.check("left click takes a stack (" + carried + ")", carried == 64);
		ctx.waitFor(mc -> this.clientCount(Items.STONE) == 96, 40);
		this.check("the count drops when you take some", true);
		ctx.takeScreenshot("jp_11_taking");

		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitFor(mc -> mc.player.containerMenu.getCarried().isEmpty(), 40);
		ctx.waitFor(mc -> this.clientCount(Items.STONE) == 160, 40);
		this.check("clicking with something on the cursor stores it", true);

		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		ctx.waitFor(mc -> mc.player.containerMenu.getCarried().is(Items.STONE), 40);
		int half = ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().getCount());
		this.check("right click takes half a stack (" + half + ")", half == 32);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		ctx.waitTicks(5);
		int oneBack = ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().getCount());
		this.check("right click with something on the cursor stores one (" + oneBack + ")", oneBack == 31);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitFor(mc -> mc.player.containerMenu.getCarried().isEmpty(), 40);

		ctx.getInput().holdShift();
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.getInput().releaseShift();
		ctx.waitFor(mc -> mc.player.getInventory().countItem(Items.STONE) == 64, 40);
		this.check("shift click sends a stack straight to the inventory", true);

		// Shift clicking an inventory slot stores it.
		ctx.runOnClient(mc -> {
			int slot = this.slotHolding(mc, Items.STONE);
			mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
		});
		ctx.waitFor(mc -> mc.player.getInventory().countItem(Items.STONE) == 0, 40);
		ctx.waitFor(mc -> this.clientCount(Items.STONE) == 160, 40);
		this.check("shift clicking your inventory stores into the network", true);

		// Store all, matching.
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().setItem(9, new ItemStack(Items.STONE, 10));
			p.getInventory().setItem(10, new ItemStack(Items.DIRT, 10));
			p.getInventory().setItem(0, new ItemStack(Items.STONE, 5));
		});
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new PortablePayloads.GridActionPayload(PortablePayloads.GRID_STORE_MATCHING)));
		ctx.waitTicks(5);
		boolean matchingOk = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			return p.getInventory().countItem(Items.DIRT) == 10 && p.getInventory().getItem(9).isEmpty() && p.getInventory().getItem(0).getCount() == 5;
		});
		this.check("Matching moves only what's already stored, and leaves the hotbar", matchingOk);
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new PortablePayloads.GridActionPayload(PortablePayloads.GRID_STORE_ALL)));
		ctx.waitTicks(5);
		boolean allOk = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			return p.getInventory().countItem(Items.DIRT) == 0 && p.getInventory().getItem(0).getCount() == 5;
		});
		this.check("Store all moves the rest, and leaves the hotbar", allOk);
		ctx.waitFor(mc -> this.clientCount(Items.DIRT) == 10, 40);

		// Items keep their components: an enchanted or named item isn't merged with a plain one.
		server.runOnServer(s -> {
			ItemStack named = new ItemStack(Items.STONE, 3);
			named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Special stone"));
			Container chest = (Container)s.overworld().getBlockEntity(CHEST_C);
			chest.setItem(10, named);
		});
		ctx.waitTicks(25);
		boolean separate = ctx.computeOnClient(mc -> ClientStorage.items().stream().filter(i -> i.stack.is(Items.STONE) && i.count > 0).count() == 2);
		this.check("a renamed stack stays separate from the plain ones", separate);
		server.runOnServer(s -> ((Container)s.overworld().getBlockEntity(CHEST_C)).setItem(10, ItemStack.EMPTY));
		ctx.waitTicks(25);

		// Typing in the search box must not trigger keys like E, which closes menus.
		int[] searchAt = ctx.computeOnClient(mc -> ((StorageScreen)mc.gui.screen()).searchBoxCenter());
		this.moveToGui(ctx, searchAt[0], searchAt[1]);
		ctx.waitTicks(2);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
		ctx.getInput().pressKey(InputConstants.KEY_E);
		ctx.getInput().typeChars("e");
		ctx.waitTicks(2);
		this.check("typing E in the search box types, and doesn't close the window", ctx.computeOnClient(mc -> mc.gui.screen() instanceof StorageScreen s && s.searchTextForTest().contains("e")));
		ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		ctx.waitTicks(2);
		this.check("Esc only leaves the search box", ctx.computeOnClient(mc -> mc.gui.screen() instanceof StorageScreen));

		// The settings page and back again, with the window still working.
		ctx.runOnClient(mc -> mc.gui.setScreen(new com.jethsportable.client.config.PortableConfigScreen(mc.gui.screen())));
		ctx.waitTicks(5);
		ctx.takeScreenshot("jp_14_settings");
		ctx.runOnClient(mc -> mc.gui.screen().onClose());
		ctx.waitFor(mc -> mc.gui.screen() instanceof StorageScreen, 40);
		ctx.waitTicks(5);
		this.check("coming back from settings keeps the items", this.clientCount(ctx, Items.STONE) > 0 && server.computeOnServer(s -> player(s).containerMenu instanceof StorageMenu));

		// Search.
		ctx.runOnClient(mc -> ((StorageScreen)mc.gui.screen()).setSearchForTest("iron"));
		ctx.waitTicks(8);
		int found = ctx.computeOnClient(mc -> ((StorageScreen)mc.gui.screen()).visibleItems().size());
		this.check("search finds by name (" + found + ")", found == 2);
		ctx.takeScreenshot("jp_12_search");
		ctx.runOnClient(mc -> ((StorageScreen)mc.gui.screen()).setSearchForTest("#logs"));
		ctx.waitTicks(8);
		int tagged = ctx.computeOnClient(mc -> ((StorageScreen)mc.gui.screen()).visibleItems().size());
		this.check("search finds by tag (" + tagged + ")", tagged == 1);
		ctx.runOnClient(mc -> ((StorageScreen)mc.gui.screen()).setSearchForTest("-stone"));
		ctx.waitTicks(8);
		boolean noStone = ctx.computeOnClient(mc -> ((StorageScreen)mc.gui.screen()).visibleItems().stream().noneMatch(i -> i.stack.is(Items.STONE)));
		this.check("search can exclude a word", noStone);
		ctx.runOnClient(mc -> ((StorageScreen)mc.gui.screen()).setSearchForTest(""));
		ctx.waitTicks(5);
		this.closeScreen(ctx);
		ctx.waitTicks(5);
		boolean closed = server.computeOnServer(s -> player(s).containerMenu == player(s).inventoryMenu);
		this.check("closing the window closes the menu on the server", closed);
	}

	private void craftingFromStorage(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		TestServerContext server = sp.getServer();
		this.openStorage(ctx, ServerActions.TAB_ITEMS);

		// Shift clicking the result keeps crafting from storage, not just what was in the grid.
		long logsBefore = server.computeOnServer(s -> networkTotal(s, Items.OAK_LOG));
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().clearContent();
			StorageMenu menu = (StorageMenu)p.containerMenu;
			int got = menu.session().extract(new ItemStack(Items.OAK_LOG), 1);
			menu.getSlot(StorageMenu.GRID_START).set(new ItemStack(Items.OAK_LOG, got));
		});
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, StorageMenu.RESULT_SLOT, 0, ContainerInput.QUICK_MOVE, mc.player));
		ctx.waitTicks(10);
		long logsAfter = server.computeOnServer(s -> networkTotal(s, Items.OAK_LOG));
		int planks = server.computeOnServer(s -> player(s).getInventory().countItem(Items.OAK_PLANKS));
		int inGrid = server.computeOnServer(s -> {
			ItemStack stack = ((StorageMenu)player(s).containerMenu).getSlot(StorageMenu.GRID_START).getItem();
			return stack.is(Items.OAK_LOG) ? stack.getCount() : 0;
		});
		this.log("crafted planks=" + planks + " logs in storage " + logsBefore + " -> " + logsAfter + ", in grid " + inGrid);
		this.check("shift crafting draws from storage", planks > 4 && logsAfter < logsBefore - 1);
		this.check("no logs vanish or appear while crafting", logsBefore == logsAfter + inGrid + planks / 4);

		// Clear grid.
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new PortablePayloads.GridActionPayload(PortablePayloads.GRID_CLEAR)));
		ctx.waitTicks(5);
		boolean gridEmpty = server.computeOnServer(s -> ((StorageMenu)player(s).containerMenu).getSlot(StorageMenu.GRID_START).getItem().isEmpty());
		this.check("Clear grid empties it", gridEmpty);

		// The recipe book pulls from storage: a chest crafted with no planks in the inventory.
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().clearContent();
			((Container)s.overworld().getBlockEntity(CHEST_C)).setItem(11, new ItemStack(Items.OAK_PLANKS, 16));
		});
		ctx.waitTicks(25);
		long plankTotal = server.computeOnServer(s -> networkTotal(s, Items.OAK_PLANKS) + player(s).getInventory().countItem(Items.OAK_PLANKS));
		String placed = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			StorageMenu menu = (StorageMenu)p.containerMenu;
			RecipeHolder<?> recipe = s.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.withDefaultNamespace("chest"))).orElseThrow();
			menu.handlePlacement(false, false, recipe, p.level(), p.getInventory());
			menu.broadcastChanges();
			return menu.getSlot(StorageMenu.RESULT_SLOT).getItem().toString();
		});
		ctx.waitTicks(3);
		int inGridPlanks = server.computeOnServer(s -> {
			int count = 0;
			StorageMenu menu = (StorageMenu)player(s).containerMenu;
			for (int i = StorageMenu.GRID_START; i < StorageMenu.GRID_END; i++) {
				if (menu.getSlot(i).getItem().is(Items.OAK_PLANKS)) count += menu.getSlot(i).getItem().getCount();
			}
			return count;
		});
		long plankTotalAfter = server.computeOnServer(s -> networkTotal(s, Items.OAK_PLANKS) + player(s).getInventory().countItem(Items.OAK_PLANKS));
		this.log("recipe book result: " + placed + ", grid planks " + inGridPlanks + ", elsewhere " + plankTotal + " -> " + plankTotalAfter);
		this.check("the recipe book fills the grid from storage", inGridPlanks == 8 && placed.contains("chest"));
		this.check("the recipe book moves items, it doesn't make them", plankTotal == plankTotalAfter + inGridPlanks);
		ctx.takeScreenshot("jp_13_recipe_from_storage");

		ctx.runOnClient(mc -> ClientPlayNetworking.send(new PortablePayloads.GridActionPayload(PortablePayloads.GRID_CLEAR)));
		ctx.waitTicks(5);
		this.closeScreen(ctx);
		ctx.waitTicks(5);
	}

	/** Thousands of random actions: the total of every item has to stay exactly the same. */
	private void duplicationCheck(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		TestServerContext server = sp.getServer();
		this.openStorage(ctx, ServerActions.TAB_ITEMS);
		ctx.waitTicks(25);
		Item[] tracked = {Items.STONE, Items.DIAMOND, Items.OAK_LOG, Items.IRON_INGOT, Items.IRON_SWORD, Items.BREAD, Items.TORCH, Items.COAL, Items.REDSTONE, Items.ARROW, Items.COPPER_INGOT, Items.DIRT};
		long[] before = new long[tracked.length];
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().clearContent();
			for (int i = 0; i < tracked.length; i++) {
				before[i] = everywhere(s, tracked[i]);
			}
		});
		int rounds = 4000;
		int batches = 20;
		for (int batch = 0; batch < batches; batch++) {
			final int seed = batch;
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				StorageMenu menu = (StorageMenu)p.containerMenu;
				StorageSession session = menu.session();
				Random random = new Random(seed);
				for (int r = 0; r < rounds / batches; r++) {
					Item item = tracked[random.nextInt(tracked.length)];
					int id = session.idFor(new ItemStack(item));
					switch (random.nextInt(11)) {
						case 0 -> session.takeToCursor(id, false);
						case 1 -> session.takeToCursor(id, true);
						case 2 -> session.takeToInventory(id);
						case 3 -> session.storeCarried(false);
						case 4 -> session.storeCarried(true);
						case 5 -> session.storeInventory(random.nextBoolean());
						case 6 -> menu.clicked(StorageMenu.INV_START + random.nextInt(36), 0, ContainerInput.QUICK_MOVE, p);
						case 7 -> menu.clicked(StorageMenu.INV_START + random.nextInt(36), random.nextInt(2), ContainerInput.PICKUP, p);
						case 8 -> menu.clicked(StorageMenu.GRID_START + random.nextInt(9), random.nextInt(2), ContainerInput.PICKUP, p);
						case 9 -> menu.clearGrid();
						default -> menu.clicked(StorageMenu.GRID_START + random.nextInt(9), 0, ContainerInput.QUICK_MOVE, p);
					}
					if (r % 25 == 0) menu.broadcastChanges();
				}
			});
			ctx.waitTicks(1);
		}
		ctx.waitTicks(5);
		server.runOnServer(s -> {
			for (int i = 0; i < tracked.length; i++) {
				long after = everywhere(s, tracked[i]);
				this.check("no duplication or loss of " + BuiltInRegistries.ITEM.getKey(tracked[i]).getPath() + " (" + before[i] + " -> " + after + ")", before[i] == after);
			}
		});
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new PortablePayloads.GridActionPayload(PortablePayloads.GRID_CLEAR)));
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new PortablePayloads.GridActionPayload(PortablePayloads.GRID_STORE_ALL)));
		server.runOnServer(s -> {
			StorageSession session = ((StorageMenu)player(s).containerMenu).session();
			session.storeCarried(false);
		});
		ctx.waitTicks(10);
		this.closeScreen(ctx);
		ctx.waitTicks(5);
	}

	private void chestsTab(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		TestServerContext server = sp.getServer();
		this.openStorage(ctx, ServerActions.TAB_ITEMS);
		this.moveToGui(ctx, ctx.computeOnClient(mc -> ((StorageScreen)mc.gui.screen()).tabCenterX(ServerActions.TAB_CHESTS)), ctx.computeOnClient(mc -> ((StorageScreen)mc.gui.screen()).tabCenterY()));
		ctx.waitTicks(2);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitFor(mc -> ClientStorage.links().size() == 7, 60);
		this.check("the chests tab lists the linked chests", ctx.computeOnClient(mc -> ((StorageScreen)mc.gui.screen()).currentTab() == ServerActions.TAB_CHESTS));
		ctx.waitTicks(3);
		ctx.takeScreenshot("jp_20_chests");

		GlobalPos chestPos = this.overworld(ctx, CHEST_B);
		// The rename button on the second row, then typing and Enter.
		int[] renameAt = ctx.computeOnClient(mc -> ((StorageScreen)mc.gui.screen()).linkButtonCenter(1, 2));
		this.moveToGui(ctx, renameAt[0], renameAt[1]);
		ctx.waitTicks(2);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
		ctx.getInput().pressKey(InputConstants.KEY_A);
		ctx.runOnClient(mc -> ((StorageScreen)mc.gui.screen()).setNameForTest("Mining stuff"));
		ctx.getInput().pressKey(InputConstants.KEY_RETURN);
		ctx.waitFor(mc -> ClientStorage.links().stream().anyMatch(l -> l.name().equals("Mining stuff")), 60);
		this.check("chests can be renamed", true);

		// A chosen target gets new items first.
		GlobalPos target = this.overworld(ctx, COPPER);
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new PortablePayloads.LinkActionPayload(target, PortablePayloads.LINK_TARGET, "")));
		ctx.waitFor(mc -> ClientStorage.links().stream().anyMatch(PortablePayloads.LinkInfo::target), 60);
		ctx.waitTicks(3);
		ctx.takeScreenshot("jp_21_target_chest");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			StorageSession session = ((StorageMenu)p.containerMenu).session();
			session.insert(new ItemStack(Items.CACTUS, 7));
		});
		ctx.waitTicks(2);
		int inTarget = server.computeOnServer(s -> countIn((Container)s.overworld().getBlockEntity(COPPER), Items.CACTUS));
		this.check("Store here sends new items to the chosen chest (" + inTarget + ")", inTarget == 7);
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new PortablePayloads.LinkActionPayload(target, PortablePayloads.LINK_TARGET, "")));
		ctx.waitFor(mc -> ClientStorage.links().stream().noneMatch(PortablePayloads.LinkInfo::target), 60);

		// Without a target, an item goes where the same item already is.
		server.runOnServer(s -> ((Container)s.overworld().getBlockEntity(CHEST_C)).setItem(20, new ItemStack(Items.FLINT)));
		ctx.waitTicks(15);
		server.runOnServer(s -> {
			StorageSession session = ((StorageMenu)player(s).containerMenu).session();
			session.insert(new ItemStack(Items.FLINT, 2));
		});
		ctx.waitTicks(2);
		int stacked = server.computeOnServer(s -> countIn((Container)s.overworld().getBlockEntity(CHEST_C), Items.FLINT));
		this.check("items go to a chest that already has them (" + stacked + ")", stacked == 3);

		// Remote opening: far enough that vanilla would close the menu, inside the Core's range.
		server.runOnServer(s -> ((Container)s.overworld().getBlockEntity(CHEST_B)).setItem(5, new ItemStack(Items.APPLE, 3)));
		server.runCommand("tp @a 18 " + Y + " 8");
		ctx.waitTicks(25);
		ctx.waitFor(mc -> ClientStorage.status().inRange(), 40);
		ctx.runOnClient(mc -> {
			com.jethsportable.client.JethsPortableClient.expectRemoteScreen(ServerActions.TAB_CHESTS);
			ClientPlayNetworking.send(new PortablePayloads.LinkActionPayload(chestPos, PortablePayloads.LINK_OPEN, ""));
		});
		ctx.waitFor(mc -> mc.gui.screen() instanceof ContainerScreen, 60);
		this.check("a linked chest opens from far away", true);
		ctx.waitTicks(60);
		boolean stillOpen = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen);
		boolean serverMenu = server.computeOnServer(s -> player(s).containerMenu instanceof ChestMenu);
		this.check("it stays open past vanilla's distance check", stillOpen && serverMenu);
		ctx.takeScreenshot("jp_22_remote_chest");
		int chestBefore = server.computeOnServer(s -> usedSlots((Container)s.overworld().getBlockEntity(CHEST_B)));
		ctx.runOnClient(mc -> {
			var menu = mc.player.containerMenu;
			for (int slot = 0; slot < 27; slot++) {
				if (menu.getSlot(slot).hasItem()) {
					mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
					break;
				}
			}
		});
		ctx.waitTicks(5);
		int chestAfter = server.computeOnServer(s -> usedSlots((Container)s.overworld().getBlockEntity(CHEST_B)));
		this.check("clicks work in a remote chest (" + chestBefore + " -> " + chestAfter + " used slots)", chestAfter == chestBefore - 1);

		ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		ctx.waitFor(mc -> mc.gui.screen() instanceof StorageScreen, 60);
		this.check("closing it goes back to the chest list", true);
		ctx.waitTicks(5);
		this.closeScreen(ctx);
		ctx.waitTicks(5);

		// Moving out of range closes a remote menu.
		this.openStorage(ctx, ServerActions.TAB_CHESTS);
		ctx.runOnClient(mc -> {
			ClientPlayNetworking.send(new PortablePayloads.LinkActionPayload(chestPos, PortablePayloads.LINK_OPEN, ""));
		});
		ctx.waitFor(mc -> mc.gui.screen() instanceof ContainerScreen, 60);
		server.runCommand("tp @a 60 " + Y + " 60");
		ctx.waitFor(mc -> mc.gui.screen() == null, 100);
		this.check("walking out of the Core's range closes it", true);
		ctx.waitTicks(10);

		// Out of range: the key says so, and requests are refused.
		ctx.getInput().pressKey(PortableKeys.OPEN_STORAGE);
		ctx.waitTicks(10);
		this.check("out of range the key doesn't open the window", ctx.computeOnClient(mc -> mc.gui.screen() == null));
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new PortablePayloads.OpenStoragePayload(ServerActions.TAB_ITEMS)));
		ctx.waitTicks(10);
		this.check("the server refuses too", ctx.computeOnClient(mc -> mc.gui.screen() == null));
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new PortablePayloads.LinkActionPayload(chestPos, PortablePayloads.LINK_OPEN, "")));
		ctx.waitTicks(10);
		this.check("a forged open request is refused", ctx.computeOnClient(mc -> mc.gui.screen() == null));

		server.runCommand("tp @a 0 " + Y + " 0");
		ctx.waitTicks(20);
		server.runCommand("clear @a");
		ctx.waitTicks(5);
		ctx.getInput().lookAt(0, 0);
	}

	private void machines(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		TestServerContext server = sp.getServer();
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			Container blast = (Container)level.getBlockEntity(BLAST_FURNACE);
			blast.setItem(0, new ItemStack(Items.RAW_IRON, 2));
			blast.setItem(1, new ItemStack(Items.COAL, 2));
		});
		this.openStorage(ctx, ServerActions.TAB_MACHINES);
		ctx.waitTicks(25);
		boolean hidden = ctx.computeOnClient(mc -> ClientStorage.machines().isEmpty() && !ClientStorage.hasModuleFlag(PortablePayloads.MODULES_FURNACE));
		this.check("without a Furnace Module the machines tab stays empty", hidden);
		ctx.takeScreenshot("jp_29_machines_need_module");
		server.runOnServer(s -> installModules(s, Blocks.FURNACE, Blocks.BREWING_STAND));
		ctx.waitFor(mc -> !ClientStorage.machines().isEmpty(), 60);
		this.check("the machines tab lists linked machines (" + ctx.computeOnClient(mc -> ClientStorage.machines().size()) + ")", ctx.computeOnClient(mc -> ClientStorage.machines().size() == 2));
		ctx.waitTicks(60);
		boolean moving = ctx.computeOnClient(mc -> ClientStorage.machines().stream().anyMatch(m -> m.progress() > 0 && m.burn() > 0));
		this.check("progress and fuel update live", moving);
		ctx.takeScreenshot("jp_30_machines");

		// Only one notification, after the whole batch, not per item.
		int toastsBefore = ctx.computeOnClient(mc -> PortableToast.shownForTest);
		ctx.waitFor(mc -> PortableToast.shownForTest > toastsBefore, 20 * 20);
		int toastsAfter = ctx.computeOnClient(mc -> PortableToast.shownForTest);
		int output = server.computeOnServer(s -> ((Container)s.overworld().getBlockEntity(BLAST_FURNACE)).getItem(2).getCount());
		this.check("one notification per batch (" + (toastsAfter - toastsBefore) + "), when all " + output + " are done", toastsAfter - toastsBefore == 1 && output == 2);
		ctx.waitTicks(5);
		ctx.takeScreenshot("jp_31_batch_toast");

		// Collect button: output lands in the inventory and pays out XP like a furnace.
		GlobalPos blastPos = this.overworld(ctx, BLAST_FURNACE);
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new PortablePayloads.MachineActionPayload(blastPos, PortablePayloads.MACHINE_COLLECT)));
		ctx.waitTicks(5);
		int ingots = server.computeOnServer(s -> player(s).getInventory().countItem(Items.IRON_INGOT));
		this.check("Collect moves the output to the inventory (" + ingots + ")", ingots == 2);
		ctx.waitTicks(20);
		int experience = server.computeOnServer(s -> player(s).totalExperience);
		this.check("and pays out the smelting XP (" + experience + ")", experience > 0);

		// Opening it remotely.
		server.runCommand("tp @a 18 " + Y + " -8");
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> {
			com.jethsportable.client.JethsPortableClient.expectRemoteScreen(ServerActions.TAB_MACHINES);
			ClientPlayNetworking.send(new PortablePayloads.MachineActionPayload(blastPos, PortablePayloads.MACHINE_OPEN));
		});
		ctx.waitFor(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen, 60);
		ctx.waitTicks(30);
		this.check("a furnace opens remotely and stays open", ctx.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen));
		ctx.takeScreenshot("jp_32_remote_furnace");
		ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		ctx.waitFor(mc -> mc.gui.screen() instanceof StorageScreen, 60);
		this.closeScreen(ctx);
		ctx.waitTicks(5);
		server.runCommand("tp @a 0 " + Y + " 0");
		ctx.waitTicks(10);
		server.runCommand("clear @a");

		// A furnace you loaded by hand, with no Link, still tells you when it's done.
		server.runOnServer(s -> {
			Container furnace = (Container)s.overworld().getBlockEntity(HAND_FURNACE);
			furnace.setItem(0, new ItemStack(Items.RAW_GOLD, 1));
			furnace.setItem(1, new ItemStack(Items.COAL, 1));
			ServerPlayer p = player(s);
			p.gameMode.useItemOn(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(HAND_FURNACE), Direction.UP, HAND_FURNACE, false));
		});
		ctx.waitFor(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.FurnaceScreen, 60);
		this.closeScreen(ctx);
		int beforeHand = ctx.computeOnClient(mc -> PortableToast.shownForTest);
		ctx.waitFor(mc -> PortableToast.shownForTest > beforeHand, 20 * 15);
		this.check("a hand-loaded furnace notifies without a Link", true);

		// Out of fuel, with work waiting.
		server.runOnServer(s -> {
			Container blast = (Container)s.overworld().getBlockEntity(BLAST_FURNACE);
			blast.setItem(0, new ItemStack(Items.RAW_COPPER, 4));
			blast.setItem(1, ItemStack.EMPTY);
		});
		int beforeFuel = ctx.computeOnClient(mc -> PortableToast.shownForTest);
		ctx.waitFor(mc -> PortableToast.shownForTest > beforeFuel, 20 * 25);
		this.check("out of fuel with work waiting notifies once", true);
		ctx.waitTicks(3);
		ctx.takeScreenshot("jp_33_no_fuel_toast");
		server.runOnServer(s -> ((Container)s.overworld().getBlockEntity(BLAST_FURNACE)).setItem(0, ItemStack.EMPTY));
		ctx.waitTicks(30);
	}

	private void modules(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		TestServerContext server = sp.getServer();
		server.runCommand("tp @a -4 " + Y + " 0");
		ctx.waitTicks(5);

		// Crafting: Blank Module + station, and back apart.
		ItemStack stonecutterModule = server.computeOnServer(s -> craft(s, new ItemStack(PortableRegistry.BLANK_MODULE), new ItemStack(Items.STONECUTTER)));
		this.check("a Blank Module and a stonecutter craft a Stonecutter Module (" + stonecutterModule.getHoverName().getString() + ")",
			stonecutterModule.is(PortableRegistry.MODULE) && Identifier.withDefaultNamespace("stonecutter").equals(stonecutterModule.get(PortableRegistry.STATION)));
		List<ItemStack> apart = server.computeOnServer(s -> takeApart(s, stonecutterModule));
		this.check("a module alone in the grid gives back the Blank Module and the block", apart.get(0).is(PortableRegistry.BLANK_MODULE) && apart.stream().anyMatch(i -> i.is(Items.STONECUTTER)));
		ItemStack table = server.computeOnServer(s -> craft(s, new ItemStack(PortableRegistry.BLANK_MODULE), new ItemStack(Items.CRAFTING_TABLE)));
		ItemStack chest = server.computeOnServer(s -> craft(s, new ItemStack(PortableRegistry.BLANK_MODULE), new ItemStack(Items.CHEST)));
		this.check("crafting tables and chests can't become modules", table.isEmpty() && chest.isEmpty());
		ItemStack chipped = server.computeOnServer(s -> craft(s, new ItemStack(PortableRegistry.BLANK_MODULE), new ItemStack(Items.CHIPPED_ANVIL)));
		this.check("a chipped anvil makes a chipped Anvil Module", Integer.valueOf(1).equals(chipped.get(PortableRegistry.MODULE_DAMAGE)));

		// Installing from the cursor, six at most.
		server.runOnServer(s -> {
			PortableBase base = base(s);
			for (int i = 0; i < Modules.SLOTS; i++) base.setModule(i, null);
			PortableData.get(s).changed(base);
		});
		this.openStorage(ctx, ServerActions.TAB_MODULES);
		net.minecraft.world.level.block.Block[] stations = {Blocks.STONECUTTER, Blocks.ANVIL, Blocks.ENCHANTING_TABLE, Blocks.GRINDSTONE, Blocks.ENDER_CHEST, Blocks.SMITHING_TABLE};
		for (int i = 0; i < stations.length; i++) {
			final var station = stations[i];
			final int slot = i;
			server.runOnServer(s -> {
				StorageMenu menu = (StorageMenu)player(s).containerMenu;
				menu.setCarried(Modules.create(Modules.stationId(station), 0, null));
				menu.broadcastChanges();
			});
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> ClientPlayNetworking.send(new PortablePayloads.ModuleActionPayload(slot, PortablePayloads.MODULE_PLACE)));
			ctx.waitTicks(3);
		}
		int installed = server.computeOnServer(s -> base(s).moduleCount());
		this.check("modules install from the cursor (" + installed + ")", installed == 6);
		ctx.waitFor(mc -> ClientStorage.module(5) != null, 40);
		ctx.waitTicks(3);
		ctx.takeScreenshot("jp_70_modules");
		InteractionResult seventh = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.closeContainer();
			ItemStack previous = p.getMainHandItem();
			p.setItemInHand(InteractionHand.MAIN_HAND, Modules.create(Identifier.withDefaultNamespace("loom"), 0, null));
			InteractionResult result = p.gameMode.useItemOn(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(CORE), Direction.UP, CORE, false));
			p.setItemInHand(InteractionHand.MAIN_HAND, previous);
			return result;
		});
		this.check("a seventh module is refused (" + seventh + ")", server.computeOnServer(s -> base(s).moduleCount()) == 6);
		ctx.waitTicks(3);

		// Built-in stonecutter: opens, stays open away from any stonecutter, and cuts.
		this.openStorage(ctx, ServerActions.TAB_MODULES);
		this.openModule(ctx, 0);
		ctx.waitFor(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.StonecutterScreen, 60);
		server.runCommand("tp @a -16 " + Y + " 6");
		ctx.waitTicks(40);
		this.check("the built-in stonecutter opens and stays open", ctx.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.StonecutterScreen));
		boolean cut = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			var menu = p.containerMenu;
			menu.getSlot(0).set(new ItemStack(Items.STONE, 4));
			menu.slotsChanged(menu.getSlot(0).container);
			menu.clickMenuButton(p, 0);
			return !menu.getSlot(1).getItem().isEmpty();
		});
		this.check("and it cuts", cut);
		ctx.takeScreenshot("jp_71_stonecutter_module");
		ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		ctx.waitFor(mc -> mc.gui.screen() instanceof StorageScreen, 60);
		this.check("closing a module goes back to the window", true);
		server.runCommand("tp @a -4 " + Y + " 0");
		ctx.waitTicks(10);

		// Built-in enchanting counts bookshelves around the Core.
		int bare = this.enchantingCost(ctx, server, 2);
		server.runCommand("fill 0 " + Y + " -2 4 " + (Y + 1) + " 2 minecraft:bookshelf replace minecraft:air");
		server.runCommand("fill 1 " + Y + " -1 3 " + (Y + 1) + " 1 minecraft:air replace minecraft:bookshelf");
		ctx.waitTicks(3);
		int shelved = this.enchantingCost(ctx, server, 2);
		this.check("the built-in enchanting table counts bookshelves around the Core (" + bare + " -> " + shelved + ")", bare <= 8 && shelved == 30);
		server.runCommand("fill 0 " + Y + " -2 4 " + (Y + 1) + " 2 minecraft:air replace minecraft:bookshelf");

		// A linked enchanting table uses its own bookshelves.
		BlockPos tablePos = new BlockPos(-8, Y, -8);
		server.runCommand("fill -10 " + Y + " -10 -6 " + (Y + 1) + " -6 minecraft:bookshelf");
		server.runCommand("fill -9 " + Y + " -9 -7 " + (Y + 1) + " -7 minecraft:air");
		server.runCommand("setblock -8 " + Y + " -8 minecraft:enchanting_table");
		ctx.waitTicks(3);
		InteractionResult linkResult = server.computeOnServer(s -> useWith(s, new ItemStack(PortableRegistry.BLANK_MODULE), tablePos));
		boolean gotLinked = server.computeOnServer(s -> findModule(player(s), Identifier.withDefaultNamespace("enchanting_table"), true) >= 0);
		this.check("a Blank Module on a placed enchanting table makes a linked module (" + linkResult + ")", gotLinked);
		this.openStorage(ctx, ServerActions.TAB_MODULES);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			int index = findModule(p, Identifier.withDefaultNamespace("enchanting_table"), true);
			((StorageMenu)p.containerMenu).setCarried(p.getInventory().removeItemNoUpdate(index));
			p.containerMenu.broadcastChanges();
		});
		ctx.waitTicks(2);
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new PortablePayloads.ModuleActionPayload(2, PortablePayloads.MODULE_PLACE)));
		ctx.waitTicks(5);
		boolean swapped = server.computeOnServer(s -> base(s).module(2) != null && base(s).module(2).linked()
			&& ModuleSlot.of(player(s).containerMenu.getCarried()) != null && !ModuleSlot.of(player(s).containerMenu.getCarried()).linked());
		this.check("placing onto a used slot swaps the modules", swapped);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().placeItemBackInInventory(p.containerMenu.getCarried().copy(), net.minecraft.util.Prediction.SERVER_ONLY);
			p.containerMenu.setCarried(ItemStack.EMPTY);
		});
		int linkedCost = this.enchantingCost(ctx, server, 2);
		this.check("the linked enchanting table uses its own bookshelves (" + linkedCost + ")", linkedCost == 30);

		// Built-in anvil: wears down like a placed anvil, then breaks.
		int toastsBefore = ctx.computeOnClient(mc -> PortableToast.shownForTest);
		this.openStorage(ctx, ServerActions.TAB_MODULES);
		this.openModule(ctx, 1);
		ctx.waitFor(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AnvilScreen, 60);
		int[] wear = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.giveExperienceLevels(1000);
			int uses = 0;
			int sawDamaged = 0;
			while (uses < 400 && base(s).module(1) != null) {
				if (!(p.containerMenu instanceof net.minecraft.world.inventory.AnvilMenu anvil)) break;
				p.getInventory().clearContent();
				anvil.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
				anvil.setItemName("Sword " + uses);
				anvil.clicked(2, 0, ContainerInput.QUICK_MOVE, p);
				uses++;
				ModuleSlot module = base(s).module(1);
				if (module != null && module.damage() == 2) sawDamaged = 1;
			}
			return new int[] {uses, sawDamaged, base(s).module(1) == null ? 1 : 0};
		});
		this.log("anvil module broke after " + wear[0] + " uses");
		this.check("the anvil module wears through chipped and damaged, then breaks (" + wear[0] + " uses)", wear[1] == 1 && wear[2] == 1);
		ctx.waitFor(mc -> !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AnvilScreen), 60);
		int toastsAfter = ctx.computeOnClient(mc -> PortableToast.shownForTest);
		this.check("it warns when damaged and when it breaks (" + (toastsAfter - toastsBefore) + " pop-ups)", toastsAfter - toastsBefore >= 2);
		this.closeScreen(ctx);
		ctx.waitTicks(5);

		// Built-in grindstone: the XP lands on the player, not somewhere at the Core.
		this.openStorage(ctx, ServerActions.TAB_MODULES);
		this.openModule(ctx, 3);
		ctx.waitFor(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.GrindstoneScreen, 60);
		int xpBefore = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.setExperienceLevels(0);
			p.setExperiencePoints(0);
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.enchant(s.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 5);
			p.containerMenu.getSlot(0).set(sword);
			p.containerMenu.clicked(2, 0, ContainerInput.QUICK_MOVE, p);
			return p.totalExperience;
		});
		ctx.waitTicks(30);
		int xpAfter = server.computeOnServer(s -> player(s).totalExperience);
		this.check("the built-in grindstone gives its XP to the player (" + xpBefore + " -> " + xpAfter + ")", xpAfter > xpBefore);
		this.closeScreen(ctx);
		ctx.waitTicks(5);

		// Ender chest module.
		server.runOnServer(s -> player(s).getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND, 5)));
		this.openStorage(ctx, ServerActions.TAB_MODULES);
		this.openModule(ctx, 4);
		ctx.waitFor(mc -> mc.gui.screen() instanceof ContainerScreen, 60);
		ctx.waitTicks(3);
		boolean ender = ctx.computeOnClient(mc -> mc.player.containerMenu.getSlot(0).getItem().getCount() == 5 && mc.player.containerMenu.getSlot(0).getItem().is(Items.DIAMOND));
		this.check("the Ender Chest Module opens your ender chest", ender);
		this.closeScreen(ctx);
		ctx.waitTicks(5);

		// A linked block that's gone shows as gone.
		server.runCommand("setblock -8 " + Y + " -8 minecraft:air");
		this.openStorage(ctx, ServerActions.TAB_MODULES);
		ctx.waitFor(mc -> ClientStorage.module(2) != null && ClientStorage.module(2).state() == PortablePayloads.STATE_MISSING, 60);
		this.check("a linked module whose block was broken says so", true);
		ctx.takeScreenshot("jp_72_module_block_gone");

		// Shift-click takes a module out.
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new PortablePayloads.ModuleActionPayload(5, PortablePayloads.MODULE_TAKE_TO_INVENTORY)));
		ctx.waitTicks(5);
		boolean takenOut = server.computeOnServer(s -> base(s).module(5) == null && findModule(player(s), Identifier.withDefaultNamespace("smithing_table"), false) >= 0);
		this.check("taking a module out puts it in the inventory", takenOut);

		// A modded station, when the mod is installed. Nemo's Woodcutter opens its screen straight from a
		// right click instead of declaring it, so it can't be built in, but a placed one can be linked.
		var woodcutter = BuiltInRegistries.BLOCK.keySet().stream().filter(id -> id.getNamespace().equals("nemos_woodcutter") && id.getPath().endsWith("woodcutter")).findFirst();
		if (woodcutter.isPresent()) {
			var block = BuiltInRegistries.BLOCK.getValue(woodcutter.get());
			ItemStack builtIn = server.computeOnServer(s -> craft(s, new ItemStack(PortableRegistry.BLANK_MODULE), new ItemStack(block)));
			this.log("woodcutter " + woodcutter.get() + (builtIn.isEmpty() ? " can't be built in" : " crafts into " + builtIn.getHoverName().getString()));
			BlockPos cutterPos = new BlockPos(-6, Y, 6);
			server.runCommand("setblock -6 " + Y + " 6 " + woodcutter.get());
			ctx.waitTicks(3);
			server.runOnServer(s -> useWith(s, new ItemStack(PortableRegistry.BLANK_MODULE), cutterPos));
			ctx.waitTicks(3);
			boolean linked = server.computeOnServer(s -> findModule(player(s), woodcutter.get(), true) >= 0);
			this.check("a Blank Module links to a placed woodcutter", linked);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				base(s).setModule(5, ModuleSlot.of(p.getInventory().getItem(findModule(p, woodcutter.get(), true))));
				PortableData.get(s).changed(base(s));
			});
			server.runCommand("tp @a -16 " + Y + " -6");
			ctx.waitTicks(10);
			this.openStorage(ctx, ServerActions.TAB_MODULES);
			this.openModule(ctx, 5);
			ctx.waitFor(mc -> mc.gui.screen() != null && !(mc.gui.screen() instanceof StorageScreen), 60);
			ctx.waitTicks(40);
			String screen = ctx.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
			this.check("the woodcutter module opens the woodcutter's own screen from afar and stays open (" + screen + ")", screen.contains("Woodcutter"));
			ctx.takeScreenshot("jp_73_woodcutter_module");
			this.closeScreen(ctx);
			server.runCommand("tp @a -4 " + Y + " 0");
			ctx.waitTicks(5);
		} else {
			this.log("Nemo's Woodcutter isn't installed in this run; skipped the modded station check");
		}
		this.closeScreen(ctx);
		ctx.waitTicks(5);
	}

	/** Opens a module from the Modules tab the way a click does. */
	private void openModule(ClientGameTestContext ctx, int slot) {
		ctx.runOnClient(mc -> {
			com.jethsportable.client.JethsPortableClient.expectRemoteScreen(ServerActions.TAB_MODULES);
			ClientPlayNetworking.send(new PortablePayloads.ModuleActionPayload(slot, PortablePayloads.MODULE_OPEN));
		});
	}

	/** Opens an enchanting module, puts a sword in, and reads the top offer's level cost. */
	private int enchantingCost(ClientGameTestContext ctx, TestServerContext server, int slot) {
		this.openStorage(ctx, ServerActions.TAB_MODULES);
		this.openModule(ctx, slot);
		ctx.waitFor(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.EnchantmentScreen, 60);
		server.runOnServer(s -> player(s).containerMenu.getSlot(0).set(new ItemStack(Items.IRON_SWORD)));
		ctx.waitTicks(5);
		int cost = server.computeOnServer(s -> ((net.minecraft.world.inventory.EnchantmentMenu)player(s).containerMenu).costs[2]);
		this.closeScreen(ctx);
		ctx.waitTicks(5);
		return cost;
	}

	private void levels(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		TestServerContext server = sp.getServer();
		int range = server.computeOnServer(s -> base(s).range());
		int maxLinks = server.computeOnServer(s -> base(s).maxLinks());
		this.check("level 1 reaches 24 blocks with 16 links (" + range + ", " + maxLinks + ")", range == 24 && maxLinks == 16);

		InteractionResult wrong = server.computeOnServer(s -> useOnCore(s, PortableRegistry.UPGRADE_GOLD));
		int stillOne = server.computeOnServer(s -> base(s).level());
		this.check("upgrades have to go in order (" + wrong + ")", stillOne == 1);

		Item[] order = {PortableRegistry.UPGRADE_IRON, PortableRegistry.UPGRADE_GOLD, PortableRegistry.UPGRADE_DIAMOND, PortableRegistry.UPGRADE_NETHERITE, PortableRegistry.UPGRADE_STAR};
		int[] expectedRange = {48, 96, 192, 512, -1};
		for (int i = 0; i < order.length; i++) {
			final Item upgrade = order[i];
			server.runOnServer(s -> useOnCore(s, upgrade));
			final int level = i + 2;
			int reached = server.computeOnServer(s -> base(s).level());
			int reach = server.computeOnServer(s -> base(s).range());
			int state = server.computeOnServer(s -> s.overworld().getBlockState(CORE).getValue(PortableCoreBlock.LEVEL));
			this.check("upgrade to level " + level + " (range " + reach + ", block state " + state + ")", reached == level && reach == expectedRange[i] && state == level);
		}
		InteractionResult tooMany = server.computeOnServer(s -> useOnCore(s, PortableRegistry.UPGRADE_STAR));
		this.check("no use past level 6 (" + tooMany + ")", server.computeOnServer(s -> base(s).level()) == 6);

		// Level 6 works from anywhere, even another dimension.
		server.runCommand("execute in minecraft:the_nether run tp @a 0 100 0");
		ctx.waitTicks(40);
		ctx.waitFor(mc -> mc.player != null && mc.player.level().dimension() == net.minecraft.world.level.Level.NETHER, 200);
		ctx.waitTicks(20);
		ctx.getInput().pressKey(PortableKeys.OPEN_STORAGE);
		ctx.waitFor(mc -> mc.gui.screen() instanceof StorageScreen, 60);
		ctx.waitFor(mc -> this.clientCount(Items.FLINT) == 3, 100);
		this.check("level 6 opens from the Nether and reads overworld chests", true);
		GlobalPos overworldChest = this.overworld(ctx, CHEST_B);
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new PortablePayloads.LinkActionPayload(overworldChest, PortablePayloads.LINK_OPEN, "")));
		ctx.waitFor(mc -> mc.gui.screen() instanceof ContainerScreen, 100);
		ctx.waitTicks(150);
		this.check("a chest in another dimension opens and stays open", ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen));
		this.closeScreen(ctx);
		server.runCommand("execute in minecraft:overworld run tp @a 0 " + Y + " 0");
		ctx.waitFor(mc -> mc.player != null && mc.player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD, 200);
		ctx.waitTicks(30);
		sp.getConnection().waitForChunksRender();
	}

	private void breakingTheCore(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		TestServerContext server = sp.getServer();
		int links = server.computeOnServer(s -> base(s).links().size());
		UUID id = server.computeOnServer(s -> base(s).id());
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().clearContent();
			p.gameMode.destroyBlock(CORE);
		});
		ctx.waitTicks(5);
		boolean detached = server.computeOnServer(s -> base(s).core() == null);
		this.check("breaking the Core detaches it from its base", detached);
		CoreData dropped = server.computeOnServer(s -> {
			for (ItemEntity entity : s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(CORE).inflate(4))) {
				if (entity.getItem().is(PortableRegistry.CORE_ITEM)) return entity.getItem().get(PortableRegistry.CORE_DATA);
			}
			return null;
		});
		this.check("the dropped item remembers its base and level (" + dropped + ")", dropped != null && dropped.baseId().equals(id) && dropped.level() == 6 && dropped.links() == links);
		ctx.waitTicks(30);
		ItemStack hover = ctx.computeOnClient(mc -> mc.player.getInventory().getItem(0).copy());
		this.log("picked up: " + hover);

		// Back down elsewhere: same base.
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			for (ItemEntity entity : s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(CORE).inflate(4))) {
				if (entity.getItem().is(PortableRegistry.CORE_ITEM)) {
					p.setItemInHand(InteractionHand.MAIN_HAND, entity.getItem().copy());
					entity.discard();
				}
			}
			if (!p.getMainHandItem().is(PortableRegistry.CORE_ITEM)) {
				int slot = p.getInventory().findSlotMatchingItem(new ItemStack(PortableRegistry.CORE_ITEM));
				for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
					if (p.getInventory().getItem(i).is(PortableRegistry.CORE_ITEM)) {
						p.setItemInHand(InteractionHand.MAIN_HAND, p.getInventory().removeItemNoUpdate(i));
						break;
					}
				}
				this.log("core in slot " + slot);
			}
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(CORE.below()).add(0, 0.5, 0), Direction.UP, CORE.below(), false);
			p.gameMode.useItemOn(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
		});
		ctx.waitTicks(5);
		boolean back = server.computeOnServer(s -> {
			PortableBase base = base(s);
			return base.id().equals(id) && CORE.equals(base.core() == null ? null : base.core().pos()) && base.level() == 6 && base.links().size() == links;
		});
		int state = server.computeOnServer(s -> s.overworld().getBlockState(CORE).getValue(PortableCoreBlock.LEVEL));
		this.check("placing it again restores the level and links (state " + state + ")", back && state == 6);

		// Breaking a linked chest drops its Link and frees the slot.
		server.runCommand("setblock 1 " + Y + " 8 minecraft:chest");
		ctx.waitTicks(2);
		BlockPos temp = new BlockPos(1, Y, 8);
		server.runOnServer(s -> link(s, temp));
		int beforeBreak = server.computeOnServer(s -> base(s).links().size());
		server.runOnServer(s -> player(s).gameMode.destroyBlock(temp));
		ctx.waitTicks(3);
		int afterBreak = server.computeOnServer(s -> base(s).links().size());
		boolean linkDropped = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(temp).inflate(2)).stream().anyMatch(e -> e.getItem().is(PortableRegistry.PORTABLE_LINK)));
		this.check("breaking a linked chest ends the link and drops the Link", afterBreak == beforeBreak - 1 && linkDropped);
	}

	private void keepingChunksLoaded(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		TestServerContext server = sp.getServer();
		BlockPos far = new BlockPos(3000, Y, 3000);
		server.runCommand("forceload add 3000 3000");
		ctx.waitTicks(10);
		server.runCommand("setblock 3000 " + Y + " 3000 minecraft:blast_furnace");
		ctx.waitTicks(2);
		server.runOnServer(s -> {
			Container furnace = (Container)s.overworld().getBlockEntity(far);
			furnace.setItem(0, new ItemStack(Items.RAW_IRON, 3));
			furnace.setItem(1, new ItemStack(Items.COAL, 1));
			PortableBase base = base(s);
			PortableData data = PortableData.get(s);
			PortableLink link = data.addLink(base, GlobalPos.of(s.overworld().dimension(), far));
			link.setBusy(true);
		});
		ctx.waitTicks(2);
		server.runCommand("forceload remove 3000 3000");
		// The Core is level 6, so its ticket should take over from the forceload and finish the job.
		int done = 0;
		for (int i = 0; i < 40 && done < 3; i++) {
			ctx.waitTicks(10);
			done = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				if (!level.isLoaded(far)) return 0;
				return level.getBlockEntity(far) instanceof AbstractFurnaceBlockEntity furnace ? furnace.getItem(2).getCount() : 0;
			});
		}
		this.check("a level 3+ Core keeps a far furnace running until done (" + done + " of 3)", done == 3);

		// Then the chunk is free to unload again.
		boolean released = false;
		for (int i = 0; i < 40 && !released; i++) {
			ctx.waitTicks(10);
			released = server.computeOnServer(s -> !s.overworld().isLoaded(far));
		}
		this.check("the ticket goes away once the work is done", released);
		server.runOnServer(s -> PortableData.get(s).removeLink(base(s), GlobalPos.of(s.overworld().dimension(), far)));
	}

	private void sharing(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		TestServerContext server = sp.getServer();
		boolean strangerBlocked = server.computeOnServer(s -> {
			FakePlayer stranger = FakePlayer.get(s.overworld(), new GameProfile(UUID.nameUUIDFromBytes("stranger".getBytes()), "Stranger"));
			stranger.setPos(0, Y, 0);
			return PortableData.get(s).usableBy(stranger).isEmpty();
		});
		this.check("a stranger can't use your base", strangerBlocked);
		boolean breakBlocked = server.computeOnServer(s -> {
			FakePlayer stranger = FakePlayer.get(s.overworld(), new GameProfile(UUID.nameUUIDFromBytes("stranger".getBytes()), "Stranger"));
			stranger.setPos(2, Y + 1, 0);
			return !stranger.gameMode.destroyBlock(CORE) && s.overworld().getBlockState(CORE).is(PortableRegistry.CORE_BLOCK);
		});
		this.check("a stranger can't break your Core", breakBlocked);
		boolean linkBlocked = server.computeOnServer(s -> {
			FakePlayer stranger = FakePlayer.get(s.overworld(), new GameProfile(UUID.nameUUIDFromBytes("stranger".getBytes()), "Stranger"));
			stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PortableRegistry.PORTABLE_LINK));
			int before = base(s).links().size();
			stranger.gameMode.useItemOn(stranger, s.overworld(), stranger.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(CHEST_A), Direction.UP, CHEST_A, false));
			return base(s).links().size() == before;
		});
		this.check("a stranger can't unlink your chests", linkBlocked);

		boolean trustedOk = server.computeOnServer(s -> {
			UUID friendId = UUID.nameUUIDFromBytes("friend".getBytes());
			FakePlayer friend = FakePlayer.get(s.overworld(), new GameProfile(friendId, "Friend"));
			friend.setPos(0, Y, 0);
			PortableBase base = base(s);
			base.trusted().put(friendId, "Friend");
			PortableData.get(s).changed(base);
			return !PortableData.get(s).usableBy(friend).isEmpty();
		});
		this.check("a trusted player can use it", trustedOk);
		this.openStorage(ctx, ServerActions.TAB_BASE);
		ctx.waitTicks(5);
		ctx.takeScreenshot("jp_40_base_page");
		ctx.runOnClient(mc -> {
			PortablePayloads.BaseInfo base = ClientStorage.bases().get(0);
			ClientPlayNetworking.send(new PortablePayloads.BaseActionPayload(base.id(), PortablePayloads.BASE_UNTRUST, base.trusted().get(0).id().toString()));
		});
		ctx.waitFor(mc -> ClientStorage.bases().get(0).trusted().isEmpty(), 40);
		this.check("the owner can remove them again", true);
		this.closeScreen(ctx);
		ctx.waitTicks(5);
	}

	/** On a big window the item grid gets more rows, and other mods' side panels have room next to it. */
	private void largeWindow(ClientGameTestContext ctx) {
		ctx.getInput().resizeWindow(1600, 900);
		ctx.waitTicks(5);
		this.openStorage(ctx, ServerActions.TAB_ITEMS);
		ctx.waitTicks(10);
		int rows = ctx.computeOnClient(mc -> ((StorageMenu)mc.player.containerMenu).rows());
		int guiHeight = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
		this.check("a taller window shows more rows (" + rows + " at height " + guiHeight + ")", rows > StorageMenu.MIN_ROWS);
		boolean fits = ctx.computeOnClient(mc -> {
			var access = (com.jethsportable.client.mixin.AbstractContainerScreenAccessor)mc.gui.screen();
			return access.jethsportable$getTopPos() + StorageMenu.imageHeight(rows) <= mc.getWindow().getGuiScaledHeight();
		});
		this.check("and still fits on screen", fits);
		ctx.takeScreenshot("jp_15_large_window");
		this.closeScreen(ctx);
		ctx.waitTicks(3);
		ctx.getInput().resizeWindow(854, 480);
		ctx.waitTicks(5);
	}

	/** Pictures for the Modrinth page, from a tidier scene. */
	private void gallery(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		TestServerContext server = sp.getServer();
		server.runCommand("clear @a");
		server.runOnServer(s -> {
			PortableBase base = base(s);
			for (int i = 0; i < Modules.SLOTS; i++) base.setModule(i, null);
			installModules(s, Blocks.FURNACE, Blocks.BREWING_STAND, Blocks.STONECUTTER, Blocks.ENCHANTING_TABLE, Blocks.SMITHING_TABLE, Blocks.ENDER_CHEST);
			base.setModule(4, ModuleSlot.of(Modules.create(Modules.stationId(Blocks.ANVIL), 1, null)));
			PortableData.get(s).changed(base);
		});
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			var inventory = p.getInventory();
			inventory.setItem(0, new ItemStack(Items.IRON_PICKAXE));
			inventory.setItem(1, new ItemStack(Items.TORCH, 48));
			inventory.setItem(2, new ItemStack(Items.BREAD, 12));
			inventory.setItem(3, new ItemStack(PortableRegistry.PORTABLE_LINK, 6));
			inventory.setItem(9, new ItemStack(PortableRegistry.PORTABLE_CRAFTING_TABLE));
			inventory.setItem(10, new ItemStack(Items.COBBLESTONE, 64));
			inventory.setItem(11, new ItemStack(Items.OAK_LOG, 24));
			ServerLevel level = s.overworld();
			put(level, CHEST_C, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.BREAD, 64), new ItemStack(Items.TORCH, 64),
				new ItemStack(Items.GOLD_INGOT, 37), new ItemStack(Items.EMERALD, 12), new ItemStack(Items.LAPIS_LAZULI, 45),
				new ItemStack(Items.BOOK, 22), new ItemStack(Items.LEATHER, 18), new ItemStack(Items.STRING, 41),
				new ItemStack(Items.BONE, 27), new ItemStack(Items.ROTTEN_FLESH, 64), new ItemStack(Items.GUNPOWDER, 19),
				new ItemStack(Items.SPIDER_EYE, 7), new ItemStack(Items.ENDER_PEARL, 9), new ItemStack(Items.WHEAT, 64),
				new ItemStack(Items.CARROT, 33), new ItemStack(Items.POTATO, 21), new ItemStack(Items.COOKED_BEEF, 16));
			Container brewing = (Container)level.getBlockEntity(BREWING);
			ItemStack water = net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER);
			brewing.setItem(0, water.copy());
			brewing.setItem(1, water.copy());
			brewing.setItem(2, water.copy());
			brewing.setItem(3, new ItemStack(Items.NETHER_WART, 4));
			brewing.setItem(4, new ItemStack(Items.BLAZE_POWDER, 2));
			Container blast = (Container)level.getBlockEntity(BLAST_FURNACE);
			blast.setItem(0, new ItemStack(Items.RAW_IRON, 24));
			blast.setItem(1, new ItemStack(Items.COAL, 16));
			blast.setItem(2, new ItemStack(Items.IRON_INGOT, 18));
		});
		server.runCommand("execute as @a run tp @s 0 " + Y + " -1 0 10");
		server.runCommand("effect clear @a");
		ctx.waitTicks(30);

		ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		ctx.waitTicks(8);
		this.galleryShot(ctx, "03_inventory_button");
		this.closeScreen(ctx);
		ctx.waitTicks(3);

		this.openStorage(ctx, ServerActions.TAB_ITEMS);
		ctx.waitTicks(20);
		this.moveToCell(ctx, 3, 1);
		ctx.waitTicks(3);
		this.galleryShot(ctx, "01_storage_window");

		ctx.getInput().setCursorPos(0, 0);
		ctx.runOnClient(mc -> ((StorageScreen)mc.gui.screen()).selectTabForTest(ServerActions.TAB_CHESTS));
		ctx.waitTicks(10);
		this.galleryShot(ctx, "04_chest_list");

		ctx.runOnClient(mc -> ((StorageScreen)mc.gui.screen()).selectTabForTest(ServerActions.TAB_MACHINES));
		ctx.waitTicks(60);
		this.galleryShot(ctx, "05_remote_furnaces");

		ctx.runOnClient(mc -> ((StorageScreen)mc.gui.screen()).selectTabForTest(ServerActions.TAB_MODULES));
		ctx.waitTicks(10);
		this.galleryShot(ctx, "09_modules");

		ctx.runOnClient(mc -> ((StorageScreen)mc.gui.screen()).selectTabForTest(ServerActions.TAB_BASE));
		ctx.waitTicks(10);
		this.galleryShot(ctx, "06_base_page");
		ctx.runOnClient(mc -> ((StorageScreen)mc.gui.screen()).selectTabForTest(ServerActions.TAB_ITEMS));
		ctx.waitTicks(3);
		this.closeScreen(ctx);
		ctx.waitTicks(5);

		ctx.runOnClient(mc -> {
			mc.player.getInventory().setSelectedSlot(3);
			PortableHighlights.clear();
		});
		ctx.getInput().lookAt(CHEST_C);
		ctx.waitTicks(5);
		this.galleryShot(ctx, "02_linked_chests");

		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().setItem(0, new ItemStack(PortableRegistry.PORTABLE_CRAFTING_TABLE));
			p.getInventory().setSelectedSlot(0);
		});
		ctx.waitTicks(3);
		ctx.getInput().lookAt(0, 0);
		ctx.waitTicks(3);
		ctx.getInput().pressKey(o -> o.keyUse);
		ctx.waitFor(mc -> mc.gui.screen() instanceof CraftingScreen, 60);
		ctx.waitTicks(5);
		this.galleryShot(ctx, "07_portable_crafting");
		this.closeScreen(ctx);
		ctx.waitTicks(5);
		ctx.getInput().lookAt(CORE);
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
			PortableToast.show(mc, new PortablePayloads.NotifyPayload(PortablePayloads.NOTIFY_BATCH_DONE, new ItemStack(Items.SMOKER).getHoverName(), new ItemStack(Items.COOKED_BEEF), 32));
		});
		ctx.waitTicks(25);
		ctx.takeScreenshot(TestScreenshotOptions.of("08_batch_notification").disableCounterPrefix().withDestinationDir(FabricLoader.getInstance().getGameDir().resolve("gallery")));
		server.runCommand("clear @a");
		server.runCommand("effect give @a minecraft:resistance infinite 255 true");
		server.runCommand("effect give @a minecraft:saturation infinite 0 true");

		server.runOnServer(s -> {
			Container blast = (Container)s.overworld().getBlockEntity(BLAST_FURNACE);
			blast.setItem(0, ItemStack.EMPTY);
			blast.setItem(2, ItemStack.EMPTY);
			Container brewing = (Container)s.overworld().getBlockEntity(BREWING);
			brewing.clearContent();
		});
	}

	private void stress(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		TestServerContext server = sp.getServer();
		server.runCommand("forceload add -8 400 300 800");
		ctx.waitTicks(20);
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			PortableData data = PortableData.get(s);
			PortableBase base = base(s);
			Random random = new Random(7);
			List<Item> items = new ArrayList<>(BuiltInRegistries.ITEM.stream().filter(i -> i != Items.AIR).toList());
			for (int i = 0; i < 200; i++) {
				BlockPos pos = new BlockPos(i % 14 * 20, Y, 400 + i / 14 * 25);
				level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
				Container chest = (Container)level.getBlockEntity(pos);
				for (int slot = 0; slot < 27; slot++) {
					Item item = items.get(random.nextInt(items.size()));
					chest.setItem(slot, new ItemStack(item, 1 + random.nextInt(item.getDefaultMaxStackSize())));
				}
				data.addLink(base, GlobalPos.of(level.dimension(), pos));
			}
		});
		ctx.waitTicks(5);
		this.openStorage(ctx, ServerActions.TAB_ITEMS);
		ctx.waitFor(mc -> ClientStorage.status().chests() > 200, 200);
		ctx.waitTicks(60);
		int distinct = ctx.computeOnClient(mc -> ClientStorage.items().size());
		this.check("200 chests all show up (" + distinct + " different items)", distinct > 1000);
		long[] worst = new long[1];
		long[] total = new long[1];
		int samples = 200;
		server.runOnServer(s -> {
			StorageSession session = ((StorageMenu)player(s).containerMenu).session();
			for (int i = 0; i < samples; i++) {
				long start = System.nanoTime();
				session.forceTickForTest();
				long took = System.nanoTime() - start;
				total[0] += took;
				worst[0] = Math.max(worst[0], took);
			}
		});
		double average = total[0] / (double)samples / 1_000_000.0;
		this.log(String.format("session tick with 200 chests: average %.3f ms, worst %.3f ms", average, worst[0] / 1_000_000.0));
		this.check("the window costs under 2 ms a tick with 200 chests", average < 2.0);
		long clientStart = System.nanoTime();
		ctx.runOnClient(mc -> ((StorageScreen)mc.gui.screen()).refreshForTest());
		this.log(String.format("sorting %d items on the client took %.2f ms", distinct, (System.nanoTime() - clientStart) / 1_000_000.0));
		ctx.waitTicks(3);
		ctx.takeScreenshot("jp_50_stress_200_chests");
		this.closeScreen(ctx);
		ctx.waitTicks(5);

		// Tidy up, so the reload test below isn't reading 200 extra chests.
		server.runOnServer(s -> {
			PortableData data = PortableData.get(s);
			PortableBase base = base(s);
			for (PortableLink link : new ArrayList<>(base.links())) {
				if (link.pos().pos().getZ() >= 400) data.removeLink(base, link.pos());
			}
		});
		server.runCommand("forceload remove all");
	}

	private void reload(ClientGameTestContext ctx, TestWorldSave save) {
		try (TestSingleplayerContext sp = save.open()) {
			sp.getConnection().waitForChunksRender();
			boolean kept = sp.getServer().computeOnServer(s -> {
				PortableBase base = base(s);
				return base != null && base.level() == 6 && base.links().size() == 9 && CORE.equals(base.core() == null ? null : base.core().pos());
			});
			this.check("a saved world keeps its base, level and links", kept);
			ctx.waitFor(mc -> !ClientStorage.bases().isEmpty(), 60);
			this.check("the client gets its bases again on joining", true);
			String name = sp.getServer().computeOnServer(s -> {
				PortableLink link = base(s).link(GlobalPos.of(s.overworld().dimension(), CHEST_B));
				return link == null ? "" : link.name();
			});
			this.check("chest names survive a reload (" + name + ")", "Mining stuff".equals(name));
			int modules = sp.getServer().computeOnServer(s -> base(s).moduleCount());
			this.check("modules survive a reload (" + modules + ")", modules == 6);
		}
	}

	/** Everything again across a real network connection. */
	private void dedicated(ClientGameTestContext ctx) {
		Properties properties = new Properties();
		properties.setProperty("spawn-protection", "0");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(properties);
			var connection = server.connect()) {
			connection.waitForChunksRender();
			ctx.runOnClient(mc -> this.log("connected to a dedicated server: " + !mc.isLocalServer()));
			this.prepareArea(ctx, server);
			server.runCommand("setblock 2 " + Y + " 0 jethsportable:portable_core_block");
			server.runCommand("setblock -3 " + Y + " 3 minecraft:chest");
			server.runCommand("setblock -1 " + Y + " 3 minecraft:chest");
			server.runCommand("clear @a");
			server.runCommand("give @a jethsportable:portable_link 4");
			ctx.waitTicks(10);
			server.runOnServer(s -> {
				put(s.overworld(), CHEST_A, new ItemStack(Items.GOLD_INGOT, 64), new ItemStack(Items.GOLD_INGOT, 5));
				// Lots of different items, to exercise splitting across packets.
				Container chest = (Container)s.overworld().getBlockEntity(CHEST_B);
				List<Item> items = BuiltInRegistries.ITEM.stream().filter(i -> i != Items.AIR && i != Items.GOLD_INGOT).limit(27).toList();
				for (int i = 0; i < 27; i++) {
					chest.setItem(i, new ItemStack(items.get(i)));
				}
			});

			// A Core placed by command belongs to whoever uses it first.
			ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(1));
			ctx.getInput().lookAt(CORE);
			ctx.waitTicks(3);
			ctx.getInput().pressKey(o -> o.keyUse);
			ctx.waitFor(mc -> mc.gui.screen() instanceof StorageScreen, 100);
			this.check("right clicking a Core opens its page, over the network", true);
			this.closeScreen(ctx);
			ctx.waitTicks(5);
			ctx.waitFor(mc -> !ClientStorage.bases().isEmpty(), 60);
			ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			ctx.getInput().lookAt(CHEST_A);
			ctx.waitTicks(3);
			ctx.getInput().pressKey(o -> o.keyUse);
			ctx.waitTicks(10);
			ctx.getInput().lookAt(CHEST_B);
			ctx.waitTicks(3);
			ctx.getInput().pressKey(o -> o.keyUse);
			ctx.waitTicks(10);
			ctx.waitFor(mc -> ClientStorage.bases().get(0).links().size() == 2, 60);
			this.check("links work over the network", true);

			ctx.getInput().pressKey(PortableKeys.OPEN_STORAGE);
			ctx.waitFor(mc -> mc.gui.screen() instanceof StorageScreen, 100);
			ctx.waitFor(mc -> this.clientCount(Items.GOLD_INGOT) == 69, 100);
			this.check("items show over the network", true);
			ctx.waitTicks(10);
			ctx.takeScreenshot("jp_60_dedicated_items");
			this.moveToCell(ctx, 0, 0);
			ctx.waitTicks(2);
			ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitFor(mc -> mc.player.containerMenu.getCarried().is(Items.GOLD_INGOT), 60);
			ctx.waitFor(mc -> this.clientCount(Items.GOLD_INGOT) == 5, 60);
			int carried = ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().getCount());
			this.check("taking items works over the network (" + carried + ")", carried == 64);
			int inChest = server.computeOnServer(s -> countIn((Container)s.overworld().getBlockEntity(CHEST_A), Items.GOLD_INGOT));
			this.check("and the chest on the server really lost them (" + inChest + " left)", inChest == 5);
			ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitFor(mc -> this.clientCount(Items.GOLD_INGOT) == 69, 60);
			this.closeScreen(ctx);
			ctx.waitTicks(5);
			server.runCommand("give @a jethsportable:module[jethsportable:station=\"minecraft:stonecutter\"]");
			ctx.waitTicks(5);
			ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(this.hotbarSlotOf(mc, PortableRegistry.MODULE)));
			ctx.getInput().lookAt(CORE);
			ctx.waitTicks(3);
			ctx.getInput().pressKey(o -> o.keyUse);
			ctx.waitTicks(10);
			this.openStorage(ctx, ServerActions.TAB_MODULES);
			ctx.waitFor(mc -> ClientStorage.module(0) != null, 60);
			this.check("a module installs with a right click on the Core, over the network", true);
			this.openModule(ctx, 0);
			ctx.waitFor(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.StonecutterScreen, 60);
			ctx.waitTicks(20);
			this.check("and opens over the network", ctx.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.StonecutterScreen));
			this.closeScreen(ctx);
			ctx.waitTicks(5);
			server.runOnServer(s -> {
				PortableData data = PortableData.get(s);
				for (PortableBase base : new ArrayList<>(data.bases())) {
					for (PortableLink link : new ArrayList<>(base.links())) {
						data.removeLink(base, link.pos());
					}
				}
			});
		}
	}

	// Helpers

	/** Closes the way the player would, so the server's menu closes too. */
	private void closeScreen(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> mc.player.closeContainer());
	}

	private void openStorage(ClientGameTestContext ctx, int tab) {
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new PortablePayloads.OpenStoragePayload(tab)));
		ctx.waitFor(mc -> mc.gui.screen() instanceof StorageScreen, 100);
		ctx.waitTicks(5);
	}

	private void moveToCell(ClientGameTestContext ctx, int column, int row) {
		int x = ctx.computeOnClient(mc -> ((StorageScreen)mc.gui.screen()).cellCenterX(column));
		int y = ctx.computeOnClient(mc -> ((StorageScreen)mc.gui.screen()).cellCenterY(row));
		this.moveToGui(ctx, x, y);
	}

	private void moveToGui(ClientGameTestContext ctx, int guiX, int guiY) {
		double scale = ctx.computeOnClient(mc -> (double)mc.getWindow().getGuiScale());
		ctx.getInput().setCursorPos(guiX * scale, guiY * scale);
	}

	private long clientCount(Item item) {
		long total = 0;
		for (ClientStorage.Item entry : ClientStorage.items()) {
			if (entry.stack.is(item)) total += entry.count;
		}
		return total;
	}

	private long clientCount(ClientGameTestContext ctx, Item item) {
		return ctx.computeOnClient(mc -> this.clientCount(item));
	}

	private int statusChests() {
		return ClientStorage.status().chests();
	}

	private int hotbarSlotOf(Minecraft mc, Item item) {
		for (int i = 0; i < 9; i++) {
			if (mc.player.getInventory().getItem(i).is(item)) return i;
		}
		return 0;
	}

	private int slotHolding(Minecraft mc, Item item) {
		for (int i = StorageMenu.INV_START; i < StorageMenu.HOTBAR_END; i++) {
			if (mc.player.containerMenu.getSlot(i).getItem().is(item)) return i;
		}
		throw new AssertionError("no " + item + " in the inventory");
	}

	private GlobalPos overworld(ClientGameTestContext ctx, BlockPos pos) {
		return GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD, pos);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private static PortableBase base(MinecraftServer server) {
		for (PortableBase base : PortableData.get(server).bases()) {
			return base;
		}
		return null;
	}

	private static InteractionResult link(MinecraftServer server, BlockPos pos) {
		ServerPlayer player = player(server);
		ItemStack previous = player.getMainHandItem();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PortableRegistry.PORTABLE_LINK));
		InteractionResult result = player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		player.setItemInHand(InteractionHand.MAIN_HAND, previous);
		return result;
	}

	private static InteractionResult useWith(MinecraftServer server, ItemStack stack, BlockPos pos) {
		ServerPlayer player = player(server);
		ItemStack previous = player.getMainHandItem();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		InteractionResult result = player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		if (player.getMainHandItem().isEmpty()) player.setItemInHand(InteractionHand.MAIN_HAND, previous);
		return result;
	}

	private static int findModule(ServerPlayer player, Identifier station, boolean linked) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ModuleSlot module = ModuleSlot.of(player.getInventory().getItem(i));
			if (module != null && module.station().equals(station) && module.linked() == linked) return i;
		}
		return -1;
	}

	private static void installModules(MinecraftServer server, net.minecraft.world.level.block.Block... stations) {
		PortableBase base = base(server);
		for (net.minecraft.world.level.block.Block station : stations) {
			int slot = base.freeModuleSlot();
			if (slot >= 0) base.setModule(slot, ModuleSlot.of(Modules.create(Modules.stationId(station), 0, null)));
		}
		PortableData.get(server).changed(base);
	}

	private static net.minecraft.world.item.crafting.CraftingInput grid(ItemStack... items) {
		List<ItemStack> all = new ArrayList<>(List.of(items));
		while (all.size() < 9) all.add(ItemStack.EMPTY);
		return net.minecraft.world.item.crafting.CraftingInput.of(3, 3, all);
	}

	private static ItemStack craft(MinecraftServer server, ItemStack... items) {
		var input = grid(items);
		return server.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, server.overworld())
			.map(recipe -> recipe.value().assemble(input)).orElse(ItemStack.EMPTY);
	}

	/** The result first, then whatever stays in the grid. */
	private static List<ItemStack> takeApart(MinecraftServer server, ItemStack module) {
		var input = grid(module);
		var recipe = server.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, server.overworld()).orElseThrow();
		List<ItemStack> result = new ArrayList<>();
		result.add(recipe.value().assemble(input));
		result.addAll(recipe.value().getRemainingItems(input));
		return result;
	}

	private static InteractionResult useOnCore(MinecraftServer server, Item item) {
		ServerPlayer player = player(server);
		ItemStack previous = player.getMainHandItem();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
		InteractionResult result = player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(CORE), Direction.UP, CORE, false));
		player.setItemInHand(InteractionHand.MAIN_HAND, previous);
		return result;
	}

	private static void put(ServerLevel level, BlockPos pos, ItemStack... stacks) {
		Container container = (Container)level.getBlockEntity(pos);
		container.clearContent();
		for (int i = 0; i < stacks.length; i++) {
			container.setItem(i, stacks[i]);
		}
	}

	private static int usedSlots(Container container) {
		int used = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (!container.getItem(i).isEmpty()) used++;
		}
		return used;
	}

	private static int countIn(Container container, Item item) {
		int count = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) count += container.getItem(i).getCount();
		}
		return count;
	}

	/** Counts an item in every linked container, without going through the session being tested. */
	private static long networkTotal(MinecraftServer server, Item item) {
		long total = 0;
		for (PortableLink link : base(server).links()) {
			ServerLevel level = server.getLevel(link.pos().dimension());
			if (level == null) continue;
			var entity = level.getBlockEntity(link.pos().pos());
			if (entity instanceof AbstractFurnaceBlockEntity) continue;
			if (entity instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity) continue;
			if (entity instanceof Container container) {
				total += countIn(container, item);
			}
		}
		// the other half of the double chest
		if (server.overworld().getBlockEntity(DOUBLE_L) instanceof Container other) {
			total += countIn(other, item);
		}
		return total;
	}

	/** Storage, the player's inventory, the cursor and the crafting grid. */
	private static long everywhere(MinecraftServer server, Item item) {
		long total = networkTotal(server, item);
		ServerPlayer player = player(server);
		total += player.getInventory().countItem(item);
		if (player.containerMenu.getCarried().is(item)) total += player.containerMenu.getCarried().getCount();
		if (player.containerMenu instanceof StorageMenu) {
			for (int i = StorageMenu.GRID_START; i < StorageMenu.GRID_END; i++) {
				ItemStack stack = player.containerMenu.getSlot(i).getItem();
				if (stack.is(item)) total += stack.getCount();
			}
		}
		// Things that don't fit get dropped by vanilla code; those still exist.
		for (ItemEntity entity : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(16))) {
			if (entity.getItem().is(item)) total += entity.getItem().getCount();
		}
		return total;
	}

	private void galleryShot(ClientGameTestContext ctx, String name) {
		ctx.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
		});
		ctx.waitTicks(3);
		Path dir = FabricLoader.getInstance().getGameDir().resolve("gallery");
		ctx.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(dir));
	}

	private void check(String name, boolean ok) {
		if (!ok) this.failures++;
		this.log((ok ? "PASS " : "FAIL ") + name);
	}

	private void log(String line) {
		this.report.add(line);
		JethsPortable.LOGGER.info("[portable-test] {}", line);
	}

	private void writeReport() {
		try {
			Files.write(FabricLoader.getInstance().getGameDir().resolve("jethsportable-test-report.txt"), this.report);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
