package com.jethsportable.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Right click a Core with it. Upgrades go in order; {@link #level} is the level this one reaches. */
public final class RangeUpgradeItem extends Item {
	private final int level;

	public RangeUpgradeItem(int level, Properties properties) {
		super(properties);
		this.level = level;
	}

	public int level() {
		return this.level;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("jethsportable.tooltip.upgrade", this.level).withStyle(ChatFormatting.GRAY));
	}
}
