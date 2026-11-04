package com.jethsportable.item;

import com.jethsportable.data.CoreData;
import com.jethsportable.registry.PortableRegistry;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

public final class CoreBlockItem extends BlockItem {
	public CoreBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		CoreData data = stack.get(PortableRegistry.CORE_DATA);
		if (data != null) {
			builder.accept(Component.translatable("jethsportable.tooltip.core_saved", data.level(), data.links()).withStyle(ChatFormatting.GRAY));
		} else {
			builder.accept(Component.translatable("jethsportable.tooltip.core").withStyle(ChatFormatting.GRAY));
		}
	}
}
