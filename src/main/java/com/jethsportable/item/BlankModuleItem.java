package com.jethsportable.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Becomes a module: used on a placed station it links to it, crafted with a station block it builds one in. */
public final class BlankModuleItem extends Item {
	public BlankModuleItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("jethsportable.tooltip.blank_module.link").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("jethsportable.tooltip.blank_module.craft").withStyle(ChatFormatting.GRAY));
	}
}
