package com.jethsportable.registry;

import com.jethsportable.JethsPortable;
import com.jethsportable.block.PortableCoreBlock;
import com.jethsportable.block.PortableCoreBlockEntity;
import com.jethsportable.data.CoreData;
import com.jethsportable.item.CoreBlockItem;
import com.jethsportable.item.LinkItem;
import com.jethsportable.item.PortableCraftingTableItem;
import com.jethsportable.item.RangeUpgradeItem;
import com.jethsportable.menu.StorageMenu;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class PortableRegistry {
	public static final DataComponentType<CoreData> CORE_DATA = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		JethsPortable.id("core_data"),
		DataComponentType.<CoreData>builder().persistent(CoreData.CODEC).networkSynchronized(CoreData.STREAM_CODEC).build()
	);

	public static final Block CORE_BLOCK = block("portable_core_block", PortableCoreBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.METAL)
		.strength(3.5F, 1200.0F)
		.sound(SoundType.LODESTONE)
		.pushReaction(PushReaction.IMMOVEABLE)
		.lightLevel(state -> state.getValue(PortableCoreBlock.LEVEL) + 3));

	public static final Item CORE_ITEM = item("portable_core_block", p -> new CoreBlockItem(CORE_BLOCK, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item PORTABLE_CRAFTING_TABLE = item("portable_crafting_table", PortableCraftingTableItem::new, new Item.Properties().stacksTo(1));
	public static final Item PORTABLE_LINK = item("portable_link", LinkItem::new, new Item.Properties());
	public static final Item UPGRADE_IRON = upgrade("iron_range_upgrade", 2, Rarity.COMMON);
	public static final Item UPGRADE_GOLD = upgrade("gold_range_upgrade", 3, Rarity.COMMON);
	public static final Item UPGRADE_DIAMOND = upgrade("diamond_range_upgrade", 4, Rarity.UNCOMMON);
	public static final Item UPGRADE_NETHERITE = upgrade("netherite_range_upgrade", 5, Rarity.RARE);
	public static final Item UPGRADE_STAR = upgrade("nether_star_range_upgrade", 6, Rarity.EPIC);

	public static final BlockEntityType<PortableCoreBlockEntity> CORE_BLOCK_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		JethsPortable.id("portable_core_block"),
		FabricBlockEntityTypeBuilder.<PortableCoreBlockEntity>create(PortableCoreBlockEntity::new, CORE_BLOCK).build()
	);

	public static final MenuType<StorageMenu> STORAGE_MENU = Registry.register(
		BuiltInRegistries.MENU, JethsPortable.id("storage"), new MenuType<>(StorageMenu::new, FeatureFlags.VANILLA_SET)
	);

	/** Holds a linked block's chunk in memory while a storage window needs it. Doesn't tick the chunk. */
	public static final TicketType READ_TICKET = Registry.register(
		BuiltInRegistries.TICKET_TYPE,
		JethsPortable.id("storage_window"),
		new TicketType(100L, TicketType.FLAG_LOADING | TicketType.FLAG_CAN_EXPIRE_IF_UNLOADED)
	);

	/** Keeps a linked machine's chunk running while it has work. Refreshed by {@code MachineWatcher}. */
	public static final TicketType MACHINE_TICKET = Registry.register(
		BuiltInRegistries.TICKET_TYPE,
		JethsPortable.id("linked_machine"),
		new TicketType(60L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE)
	);

	/** Blocks that can't be linked, for pack makers. */
	public static final TagKey<Block> NOT_LINKABLE = TagKey.create(Registries.BLOCK, JethsPortable.id("not_linkable"));

	public static final CreativeModeTab TAB = Registry.register(
		BuiltInRegistries.CREATIVE_MODE_TAB,
		JethsPortable.id("items"),
		FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.jethsportable"))
			.icon(() -> new ItemStack(CORE_ITEM))
			.displayItems((parameters, output) -> {
				output.accept(PORTABLE_CRAFTING_TABLE);
				output.accept(CORE_ITEM);
				output.accept(PORTABLE_LINK);
				output.accept(UPGRADE_IRON);
				output.accept(UPGRADE_GOLD);
				output.accept(UPGRADE_DIAMOND);
				output.accept(UPGRADE_NETHERITE);
				output.accept(UPGRADE_STAR);
			})
			.build()
	);

	private PortableRegistry() {
	}

	/** Runs the static initializers above. */
	public static void register() {
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JethsPortable.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JethsPortable.id(name));
		Item item = factory.apply(properties.setId(key));
		if (item instanceof BlockItem blockItem) {
			blockItem.registerBlocks(Item.BY_BLOCK, item);
		}
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private static Item upgrade(String name, int level, Rarity rarity) {
		return item(name, p -> new RangeUpgradeItem(level, p), new Item.Properties().stacksTo(16).rarity(rarity));
	}

	public static Item upgradeForLevel(int level) {
		return switch (level) {
			case 2 -> UPGRADE_IRON;
			case 3 -> UPGRADE_GOLD;
			case 4 -> UPGRADE_DIAMOND;
			case 5 -> UPGRADE_NETHERITE;
			default -> UPGRADE_STAR;
		};
	}
}
