package com.jethsportable.client;

import com.jethsportable.item.LinkItem;
import com.jethsportable.network.PortablePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.GlobalPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/** World outlines: every linked block while you hold a Link, and "Show me" for half a minute. */
public final class PortableHighlights {
	private static final long SHOW_MS = 30_000;
	private static final double LINK_OUTLINE_DISTANCE_SQR = 64 * 64;
	private static final GizmoStyle LINK_STYLE = GizmoStyle.strokeAndFill(0xFF5FE0C8, 2.0F, 0x205FE0C8);
	private static final GizmoStyle SHOW_STYLE = GizmoStyle.strokeAndFill(0xFFFFD84A, 2.5F, 0x38FFD84A);
	private static @Nullable GlobalPos shown;
	private static long shownUntil;

	private PortableHighlights() {
	}

	public static void show(GlobalPos pos) {
		shown = pos;
		shownUntil = Util.getMillis() + SHOW_MS;
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) return;
		if (!pos.dimension().equals(player.level().dimension())) {
			player.sendOverlayMessage(Component.translatable("jethsportable.gui.other_dimension"));
		} else {
			int distance = (int)Math.sqrt(pos.pos().distSqr(player.blockPosition()));
			player.sendOverlayMessage(Component.translatable("jethsportable.gui.show_me", pos.pos().getX(), pos.pos().getY(), pos.pos().getZ(), distance));
		}
	}

	public static void clear() {
		shown = null;
	}

	/** Called every client tick, inside the game's per-tick gizmo collection. */
	public static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) return;
		if (shown != null) {
			if (Util.getMillis() > shownUntil) {
				shown = null;
			} else if (shown.dimension().equals(player.level().dimension())) {
				Gizmos.cuboid(shown.pos(), 0.02F, SHOW_STYLE).setAlwaysOnTop();
			}
		}
		if (player.getMainHandItem().getItem() instanceof LinkItem || player.getOffhandItem().getItem() instanceof LinkItem) {
			for (PortablePayloads.BaseInfo base : ClientStorage.bases()) {
				for (GlobalPos pos : base.links()) {
					if (pos.dimension().equals(player.level().dimension()) && pos.pos().distToCenterSqr(player.position()) < LINK_OUTLINE_DISTANCE_SQR) {
						Gizmos.cuboid(pos.pos(), 0.02F, LINK_STYLE);
					}
				}
			}
		}
	}
}
