package com.jethsportable.client;

import com.jethsportable.network.PortablePayloads;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** What the server has told this client about its bases and the open storage window. */
public final class ClientStorage {
	public static final class Item {
		public final int id;
		public final ItemStack stack;
		public long count;
		private @Nullable String lowerName;
		private @Nullable String mod;

		Item(int id, ItemStack stack, long count) {
			this.id = id;
			this.stack = stack;
			this.count = count;
		}

		public String lowerName() {
			if (this.lowerName == null) {
				this.lowerName = this.stack.getHoverName().getString().toLowerCase(Locale.ROOT);
			}
			return this.lowerName;
		}

		public String mod() {
			if (this.mod == null) {
				this.mod = BuiltInRegistries.ITEM.getKey(this.stack.getItem()).getNamespace();
			}
			return this.mod;
		}
	}

	private static final Int2ObjectOpenHashMap<Item> ITEMS = new Int2ObjectOpenHashMap<>();
	private static List<PortablePayloads.BaseInfo> bases = List.of();
	private static List<PortablePayloads.LinkInfo> links = List.of();
	private static List<PortablePayloads.MachineInfo> machines = List.of();
	private static final PortablePayloads.ModuleInfo[] MODULES = new PortablePayloads.ModuleInfo[6];
	private static int moduleFlags;
	private static PortablePayloads.StatusPayload status = new PortablePayloads.StatusPayload(0, true, 0, 0, 0, 0);
	private static int session;
	/** Goes up whenever the item list changes, so screens know to re-sort. */
	private static int version;

	private ClientStorage() {
	}

	public static void reset() {
		ITEMS.clear();
		links = List.of();
		machines = List.of();
		java.util.Arrays.fill(MODULES, null);
		moduleFlags = 0;
		status = new PortablePayloads.StatusPayload(0, true, 0, 0, 0, 0);
		version++;
	}

	/**
	 * Called with the session number of anything received about a window. A new number means a new
	 * window, so the old data goes. Returns false for leftovers from a window that's already gone.
	 */
	public static boolean enterSession(int number) {
		if (number < session) return false;
		if (number > session) {
			session = number;
			reset();
		}
		return true;
	}

	public static void clearAll() {
		reset();
		session = 0;
		bases = List.of();
	}

	public static void apply(PortablePayloads.ItemsPayload payload) {
		for (PortablePayloads.NewItem added : payload.added()) {
			ITEMS.put(added.id(), new Item(added.id(), added.variant().toStack(), added.count()));
		}
		for (PortablePayloads.ItemCount changed : payload.changed()) {
			Item item = ITEMS.get(changed.id());
			if (item != null) item.count = changed.count();
		}
		version++;
	}

	public static Collection<Item> items() {
		return ITEMS.values();
	}

	public static int version() {
		return version;
	}

	public static void fillStackedContents(StackedItemContents contents) {
		for (Item item : ITEMS.values()) {
			if (item.count > 0) {
				contents.accountStack(item.stack, (int)Math.min(item.count, Integer.MAX_VALUE / 2));
			}
		}
	}

	public static List<PortablePayloads.BaseInfo> bases() {
		return bases;
	}

	public static void setBases(List<PortablePayloads.BaseInfo> value) {
		bases = value;
	}

	public static List<PortablePayloads.LinkInfo> links() {
		return links;
	}

	public static void setLinks(List<PortablePayloads.LinkInfo> value) {
		links = value;
	}

	public static List<PortablePayloads.MachineInfo> machines() {
		return machines;
	}

	public static void setMachines(List<PortablePayloads.MachineInfo> value) {
		machines = value;
	}

	public static void setModules(PortablePayloads.ModulesPayload payload) {
		java.util.Arrays.fill(MODULES, null);
		for (PortablePayloads.ModuleInfo info : payload.modules()) {
			if (info.slot() >= 0 && info.slot() < MODULES.length) MODULES[info.slot()] = info;
		}
		moduleFlags = payload.flags();
	}

	public static PortablePayloads.@Nullable ModuleInfo module(int slot) {
		return MODULES[slot];
	}

	public static boolean hasModuleFlag(int flag) {
		return (moduleFlags & flag) != 0;
	}

	public static PortablePayloads.StatusPayload status() {
		return status;
	}

	public static void setStatus(PortablePayloads.StatusPayload value) {
		status = value;
	}

	public static boolean inRangeOf(Player player, PortablePayloads.BaseInfo base) {
		if (base.core().isEmpty()) return false;
		if (base.range() < 0) return true;
		GlobalPos core = base.core().get();
		if (!core.dimension().equals(player.level().dimension())) return false;
		double dx = player.getX() - core.pos().getX();
		double dz = player.getZ() - core.pos().getZ();
		return dx * dx + dz * dz <= (double)base.range() * base.range();
	}

	public static boolean anyUsable(Player player) {
		for (PortablePayloads.BaseInfo base : bases) {
			if (inRangeOf(player, base)) return true;
		}
		return false;
	}

	/** Bases the player can use right now come first, so the base page opens on one of them. */
	public static List<PortablePayloads.BaseInfo> basesForPage(Player player) {
		List<PortablePayloads.BaseInfo> result = new ArrayList<>();
		for (PortablePayloads.BaseInfo base : bases) {
			if (inRangeOf(player, base)) result.add(base);
		}
		for (PortablePayloads.BaseInfo base : bases) {
			if (!result.contains(base)) result.add(base);
		}
		return result;
	}

	/** Blocks past the edge of the nearest Core's range in this dimension, or -1 if there's none here. */
	public static int blocksOutsideRange(Player player) {
		int best = -1;
		for (PortablePayloads.BaseInfo base : bases) {
			if (base.core().isEmpty() || base.range() < 0) continue;
			GlobalPos core = base.core().get();
			if (!core.dimension().equals(player.level().dimension())) continue;
			double dx = player.getX() - core.pos().getX();
			double dz = player.getZ() - core.pos().getZ();
			int outside = (int)Math.ceil(Math.sqrt(dx * dx + dz * dz)) - base.range();
			if (best < 0 || outside < best) best = outside;
		}
		return best;
	}

	public static boolean isLinked(GlobalPos pos) {
		for (PortablePayloads.BaseInfo base : bases) {
			if (base.links().contains(pos)) return true;
		}
		return false;
	}
}
