package com.jethsportable.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Used on a chest or furnace. The work happens in {@code LinkHandler}, ahead of the block's own interaction. */
public final class LinkItem extends Item {
	public LinkItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("jethsportable.tooltip.link").withStyle(ChatFormatting.GRAY));
	}
}
