package com.jethsportable.item;

import com.jethsportable.menu.PortableCraftingMenu;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** A crafting table you carry. Opens from your hand, from an inventory slot, a button or a key. */
public final class PortableCraftingTableItem extends Item {
	private static final Set<ServerPlayer> OPEN_AFTER_CLICK = new HashSet<>();

	public PortableCraftingTableItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			open(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	/** Right click on this item in an inventory slot, with nothing on the cursor. */
	@Override
	public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction, Player player, SlotAccess carriedItem) {
		if (clickAction != ClickAction.SECONDARY || !other.isEmpty() || !slot.allowModification(player)) {
			return false;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			// Not from inside the click: vanilla pauses syncing on the current menu until the click is
			// over, and swapping menus now would leave it paused for good.
			OPEN_AFTER_CLICK.add(serverPlayer);
		}
		return true;
	}

	/** Called at the end of every server tick. */
	public static void openPending() {
		if (OPEN_AFTER_CLICK.isEmpty()) return;
		for (ServerPlayer player : OPEN_AFTER_CLICK) {
			if (!player.isRemoved() && isCarriedBy(player)) open(player);
		}
		OPEN_AFTER_CLICK.clear();
	}

	public static void open(ServerPlayer player) {
		player.openMenu(new SimpleMenuProvider(
			(containerId, inventory, p) -> new PortableCraftingMenu(containerId, inventory), Component.translatable("item.jethsportable.portable_crafting_table")
		));
		player.level().playSound(null, player.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.4F, 1.3F);
	}

	public static boolean isCarriedBy(Player player) {
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.getItem() instanceof PortableCraftingTableItem) return true;
		}
		return player.getOffhandItem().getItem() instanceof PortableCraftingTableItem;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("jethsportable.tooltip.crafting").withStyle(ChatFormatting.GRAY));
	}
}
