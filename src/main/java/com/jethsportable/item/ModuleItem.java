package com.jethsportable.item;

import com.jethsportable.module.ModuleKind;
import com.jethsportable.module.ModuleSlot;
import com.jethsportable.module.Modules;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** A station for a Core's module slots. Which station, and whether it's built in or linked, are in its components. */
public final class ModuleItem extends Item {
	public ModuleItem(Properties properties) {
		super(properties);
	}

	@Override
	public Component getName(ItemStack stack) {
		ModuleSlot module = ModuleSlot.of(stack);
		return module == null ? super.getName(stack) : Component.translatable("item.jethsportable.module.named", Modules.stationName(module.station()));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		ModuleSlot module = ModuleSlot.of(stack);
		if (module == null) return;
		ModuleKind kind = module.kind();
		if (kind == ModuleKind.FURNACE) {
			builder.accept(Component.translatable("jethsportable.tooltip.module.furnace").withStyle(ChatFormatting.GRAY));
		} else if (kind == ModuleKind.BREWING) {
			builder.accept(Component.translatable("jethsportable.tooltip.module.brewing").withStyle(ChatFormatting.GRAY));
		} else if (module.linked()) {
			GlobalPos pos = module.link().orElseThrow();
			builder.accept(Component.translatable("jethsportable.tooltip.module.linked", pos.pos().getX(), pos.pos().getY(), pos.pos().getZ()).withStyle(ChatFormatting.GRAY));
			if (!pos.dimension().equals(Level.OVERWORLD)) {
				builder.accept(Component.literal(pos.dimension().identifier().toString()).withStyle(ChatFormatting.DARK_GRAY));
			}
		} else {
			builder.accept(Component.translatable(kind == ModuleKind.ENCHANTING ? "jethsportable.tooltip.module.built_in_enchanting" : "jethsportable.tooltip.module.built_in").withStyle(ChatFormatting.GRAY));
			if (Modules.isAnvil(module.station())) {
				builder.accept(Component.translatable("jethsportable.tooltip.module.health", healthName(module.damage())).withStyle(ChatFormatting.GRAY));
			}
		}
		builder.accept(Component.translatable("jethsportable.tooltip.module.install").withStyle(ChatFormatting.DARK_GRAY));
	}

	public static Component healthName(int damage) {
		return switch (damage) {
			case 0 -> Component.translatable("jethsportable.health.intact").withStyle(ChatFormatting.GREEN);
			case 1 -> Component.translatable("jethsportable.health.chipped").withStyle(ChatFormatting.YELLOW);
			default -> Component.translatable("jethsportable.health.damaged").withStyle(ChatFormatting.RED);
		};
	}

	// A built-in anvil shows its wear as a durability bar, like a tool's.

	@Override
	public boolean isBarVisible(ItemStack stack) {
		ModuleSlot module = ModuleSlot.of(stack);
		return module != null && !module.linked() && Modules.isAnvil(module.station());
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		ModuleSlot module = ModuleSlot.of(stack);
		int left = Modules.ANVIL_BREAKS_AT - (module == null ? 0 : module.damage());
		return Math.round(13.0F * left / Modules.ANVIL_BREAKS_AT);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		ModuleSlot module = ModuleSlot.of(stack);
		float left = (Modules.ANVIL_BREAKS_AT - (module == null ? 0 : module.damage())) / (float)Modules.ANVIL_BREAKS_AT;
		return Mth.hsvToRgb(left / 3.0F, 1.0F, 1.0F);
	}
}
