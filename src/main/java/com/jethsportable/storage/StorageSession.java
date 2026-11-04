package com.jethsportable.storage;

import com.jethsportable.config.PortableServerConfig;
import com.jethsportable.data.PortableBase;
import com.jethsportable.data.PortableData;
import com.jethsportable.data.PortableLink;
import com.jethsportable.item.PortableCraftingTableItem;
import com.jethsportable.menu.StorageMenu;
import com.jethsportable.network.PortablePayloads;
import com.jethsportable.network.ServerActions;
import com.jethsportable.registry.PortableRegistry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The server side of one open storage window. Chests are only read while a window is open, a few each
 * tick, and the client only gets told what changed. Every move of items is a single transaction, so
 * nothing can be lost or duplicated even if a chest fills up or changes halfway.
 */
public final class StorageSession {
	private static final int ITEMS_PER_PACKET = 256;
	private static final int NEW_TICKETS_PER_TICK = 16;
	private static final int ALMOST_FULL_PERCENT = 90;

	private static final class Entry {
		final PortableBase base;
		final PortableLink link;
		final @Nullable ServerLevel level;
		final Object2LongOpenHashMap<ItemVariant> contents = new Object2LongOpenHashMap<>();
		ItemStack icon = ItemStack.EMPTY;
		int used;
		int total;
		int state = PortablePayloads.STATE_LOADING;
		boolean machine;
		boolean inRange;

		Entry(PortableBase base, PortableLink link, @Nullable ServerLevel level) {
			this.base = base;
			this.link = link;
			this.level = level;
		}

		boolean storesItems() {
			return this.inRange && !this.machine && this.state == PortablePayloads.STATE_OK;
		}
	}

	/** Items moved from storage to the inventory for a recipe book click. */
	public record Loan(Map<ItemVariant, Integer> lent, Map<ItemVariant, Integer> held) {
	}

	private static int lastSessionId;

	private final int id = ++lastSessionId;
	private final ServerPlayer player;
	private final StorageMenu menu;
	private final MinecraftServer server;
	private final Map<GlobalPos, Entry> entries = new LinkedHashMap<>();
	private final List<Entry> order = new ArrayList<>();
	private final Set<Entry> touched = new LinkedHashSet<>();
	private final Object2LongOpenHashMap<ItemVariant> totals = new Object2LongOpenHashMap<>();
	private final Object2IntOpenHashMap<ItemVariant> ids = new Object2IntOpenHashMap<>();
	private final Int2ObjectOpenHashMap<ItemVariant> byId = new Int2ObjectOpenHashMap<>();
	private final Set<ItemVariant> dirty = new LinkedHashSet<>();
	private final Set<ItemVariant> sent = new HashSet<>();
	private boolean inRange;
	private boolean closed;
	private boolean warnedAlmostFull;
	private int tab;
	private int nextId;
	private int age = -1;
	private long lastTick = -1;
	private int scanCursor;
	private int ticketsThisTick;
	private float actionBudget;
	private int lastLinksHash;
	private int lastStatusHash;

	public StorageSession(ServerPlayer player, StorageMenu menu) {
		this.player = player;
		this.menu = menu;
		this.server = player.level().getServer();
		this.actionBudget = PortableServerConfig.get().maxActionsPerSecond;
		this.ids.defaultReturnValue(-1);
	}

	public int id() {
		return this.id;
	}

	public void setTab(int tab) {
		this.tab = tab;
		this.lastLinksHash = 0;
		if (tab == ServerActions.TAB_CHESTS) this.sendLinks();
		if (tab == ServerActions.TAB_MACHINES) this.sendMachines();
	}

	/** A link was added, removed or renamed just now, so don't wait for the next rebuild to show it. */
	public void refreshLinks() {
		if (this.closed) return;
		this.rebuild();
		this.setTab(this.tab);
	}

	/** Called whenever the menu syncs, which can be several times a tick after clicks. */
	public void tick() {
		if (this.closed) return;
		long now = this.server.getTickCount();
		if (now != this.lastTick) {
			this.lastTick = now;
			this.age++;
			this.ticketsThisTick = 0;
			int max = PortableServerConfig.get().maxActionsPerSecond;
			this.actionBudget = Math.min(max, this.actionBudget + max / 20.0F);
			if (this.age % 20 == 0) this.rebuild();
			if (this.age % 40 == 0) this.keepChunksLoaded();
			this.scanSome();
		}
		if (!this.touched.isEmpty()) {
			for (Entry entry : this.touched) this.scan(entry);
			this.touched.clear();
		}
		this.flushItems();
		if (now == this.lastTick && this.age % 20 == 5) {
			this.sendStatus();
			if (this.tab == ServerActions.TAB_CHESTS) this.sendLinks();
		}
		if (this.tab == ServerActions.TAB_MACHINES && this.age % 10 == 5) this.sendMachines();
	}

	public void close() {
		this.closed = true;
	}

	/** Spends one action from the player's per-second allowance. */
	public boolean spendAction() {
		if (this.actionBudget < 1.0F) return false;
		this.actionBudget--;
		return true;
	}

	// Keeping up with the world

	/** Works out what the player can reach right now. Moves one way or the other only change the list. */
	private void rebuild() {
		PortableData data = PortableData.get(this.server);
		List<PortableBase> usable = data.usableBy(this.player);
		this.inRange = !usable.isEmpty();
		Set<GlobalPos> seen = new HashSet<>();
		for (PortableBase base : usable) {
			this.checkCore(data, base);
			for (PortableLink link : base.links()) {
				GlobalPos pos = link.pos();
				seen.add(pos);
				Entry entry = this.entries.get(pos);
				if (entry == null || entry.link != link) {
					if (entry != null) this.forget(entry);
					entry = new Entry(base, link, this.server.getLevel(pos.dimension()));
					this.entries.put(pos, entry);
				}
				entry.inRange = base.inRange(pos);
			}
		}
		boolean changed = this.entries.keySet().retainAll(seen) || this.order.size() != this.entries.size();
		if (changed) {
			this.recountAfterRemoval();
		}
		this.order.clear();
		this.order.addAll(this.entries.values());
		if (this.scanCursor >= this.order.size()) this.scanCursor = 0;
	}

	private void recountAfterRemoval() {
		Set<Entry> kept = new HashSet<>(this.entries.values());
		for (Entry entry : this.order) {
			if (!kept.contains(entry)) this.forget(entry);
		}
	}

	private void forget(Entry entry) {
		this.replaceContents(entry, new Object2LongOpenHashMap<>());
		entry.used = 0;
		entry.total = 0;
	}

	private void checkCore(PortableData data, PortableBase base) {
		GlobalPos core = base.core();
		if (core == null) return;
		ServerLevel level = this.server.getLevel(core.dimension());
		if (level != null && level.isLoaded(core.pos()) && !level.getBlockState(core.pos()).is(PortableRegistry.CORE_BLOCK)) {
			data.coreRemoved(base.id(), core);
		}
	}

	private void keepChunksLoaded() {
		for (Entry entry : this.order) {
			if (entry.level != null && entry.inRange) {
				entry.level.getChunkSource().addTicketWithRadius(PortableRegistry.READ_TICKET, ChunkPos.containing(entry.link.pos().pos()), 1);
			}
		}
	}

	private void scanSome() {
		int size = this.order.size();
		if (size == 0) return;
		int budget = Math.max(4, size / 10);
		for (int i = 0; i < budget && i < size; i++) {
			if (this.scanCursor >= size) this.scanCursor = 0;
			this.scan(this.order.get(this.scanCursor++));
		}
	}

	private void scan(Entry entry) {
		BlockPos pos = entry.link.pos().pos();
		if (!entry.inRange) {
			entry.state = PortablePayloads.STATE_OUT_OF_RANGE;
			this.forget(entry);
			return;
		}
		if (entry.level == null) {
			entry.state = PortablePayloads.STATE_MISSING;
			this.forget(entry);
			return;
		}
		if (!entry.level.isLoaded(pos)) {
			entry.state = PortablePayloads.STATE_LOADING;
			if (this.ticketsThisTick < NEW_TICKETS_PER_TICK) {
				this.ticketsThisTick++;
				entry.level.getChunkSource().addTicketWithRadius(PortableRegistry.READ_TICKET, ChunkPos.containing(pos), 1);
			}
			return;
		}
		BlockState state = entry.level.getBlockState(pos);
		BlockEntity blockEntity = entry.level.getBlockEntity(pos);
		entry.icon = new ItemStack(state.getBlock());
		entry.machine = MachineView.isMachine(blockEntity);
		if (entry.machine) {
			entry.state = PortablePayloads.STATE_OK;
			this.forget(entry);
			return;
		}
		Storage<ItemVariant> storage = ItemStorage.SIDED.find(entry.level, pos, state, blockEntity, null);
		if (storage == null) {
			entry.state = PortablePayloads.STATE_MISSING;
			this.forget(entry);
			return;
		}
		Object2LongOpenHashMap<ItemVariant> next = new Object2LongOpenHashMap<>();
		int used = 0;
		int total = 0;
		for (StorageView<ItemVariant> view : storage) {
			total++;
			if (view.isResourceBlank() || view.getAmount() <= 0) continue;
			used++;
			next.addTo(view.getResource(), view.getAmount());
		}
		entry.state = PortablePayloads.STATE_OK;
		entry.used = used;
		entry.total = total;
		this.replaceContents(entry, next);
	}

	private void replaceContents(Entry entry, Object2LongOpenHashMap<ItemVariant> next) {
		for (Object2LongMap.Entry<ItemVariant> old : entry.contents.object2LongEntrySet()) {
			ItemVariant variant = old.getKey();
			if (!next.containsKey(variant)) {
				this.addTotal(variant, -old.getLongValue());
			}
		}
		for (Object2LongMap.Entry<ItemVariant> now : next.object2LongEntrySet()) {
			long delta = now.getLongValue() - entry.contents.getLong(now.getKey());
			if (delta != 0) this.addTotal(now.getKey(), delta);
		}
		entry.contents.clear();
		entry.contents.putAll(next);
	}

	private void addTotal(ItemVariant variant, long delta) {
		this.totals.addTo(variant, delta);
		this.dirty.add(variant);
	}

	private int idOf(ItemVariant variant) {
		int id = this.ids.getInt(variant);
		if (id < 0) {
			id = this.nextId++;
			this.ids.put(variant, id);
			this.byId.put(id, variant);
		}
		return id;
	}

	// Sending to the client

	private void flushItems() {
		if (this.dirty.isEmpty()) return;
		List<PortablePayloads.NewItem> added = new ArrayList<>();
		List<PortablePayloads.ItemCount> changed = new ArrayList<>();
		for (ItemVariant variant : this.dirty) {
			long count = this.totals.getLong(variant);
			int id = this.idOf(variant);
			if (this.sent.add(variant)) {
				added.add(new PortablePayloads.NewItem(id, variant, count));
			} else {
				changed.add(new PortablePayloads.ItemCount(id, count));
			}
			if (added.size() + changed.size() >= ITEMS_PER_PACKET) {
				ServerPlayNetworking.send(this.player, new PortablePayloads.ItemsPayload(this.id, added, changed));
				added = new ArrayList<>();
				changed = new ArrayList<>();
			}
		}
		this.dirty.clear();
		if (!added.isEmpty() || !changed.isEmpty()) {
			ServerPlayNetworking.send(this.player, new PortablePayloads.ItemsPayload(this.id, added, changed));
		}
	}

	private void sendStatus() {
		int chests = 0;
		int machines = 0;
		int used = 0;
		int total = 0;
		for (Entry entry : this.order) {
			if (entry.machine) {
				machines++;
			} else {
				chests++;
				used += entry.used;
				total += entry.total;
			}
		}
		int hash = Objects.hash(this.inRange, chests, machines, used, total);
		if (hash != this.lastStatusHash) {
			this.lastStatusHash = hash;
			ServerPlayNetworking.send(this.player, new PortablePayloads.StatusPayload(this.id, this.inRange, chests, machines, used, total));
		}
		if (total > 0 && used * 100 >= total * ALMOST_FULL_PERCENT) {
			if (!this.warnedAlmostFull) {
				this.warnedAlmostFull = true;
				ServerActions.notify(this.player, PortablePayloads.NOTIFY_STORAGE_FULL, Component.empty(), new ItemStack(PortableRegistry.CORE_ITEM), used * 100 / total);
			}
		} else {
			this.warnedAlmostFull = false;
		}
	}

	private void sendLinks() {
		GlobalPos target = PortableData.get(this.server).target(this.player.getUUID());
		List<PortablePayloads.LinkInfo> links = new ArrayList<>();
		int hash = 1;
		for (Entry entry : this.order) {
			if (entry.machine) continue;
			boolean isTarget = entry.link.pos().equals(target);
			links.add(new PortablePayloads.LinkInfo(entry.link.pos(), entry.link.name(), entry.icon, entry.used, entry.total, entry.state, isTarget));
			hash = 31 * hash + Objects.hash(entry.link.pos(), entry.link.name(), entry.icon.getItem(), entry.used, entry.total, entry.state, isTarget);
		}
		if (hash != this.lastLinksHash) {
			this.lastLinksHash = hash;
			ServerPlayNetworking.send(this.player, new PortablePayloads.LinksPayload(this.id, links));
		}
	}

	private void sendMachines() {
		List<PortablePayloads.MachineInfo> machines = new ArrayList<>();
		for (Entry entry : this.order) {
			if (!entry.machine) continue;
			BlockEntity blockEntity = entry.state == PortablePayloads.STATE_OK && entry.level != null && entry.level.isLoaded(entry.link.pos().pos())
				? entry.level.getBlockEntity(entry.link.pos().pos()) : null;
			if (MachineView.isMachine(blockEntity)) {
				machines.add(MachineView.describe(entry.link.pos(), entry.link.name(), entry.icon, blockEntity));
			} else {
				machines.add(new PortablePayloads.MachineInfo(entry.link.pos(), entry.link.name(), entry.icon, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, 0, 0, entry.state, false));
			}
		}
		ServerPlayNetworking.send(this.player, new PortablePayloads.MachinesPayload(this.id, machines));
	}

	// Moving items

	private @Nullable Storage<ItemVariant> storageOf(Entry entry) {
		BlockPos pos = entry.link.pos().pos();
		if (entry.level == null || !entry.level.isLoaded(pos)) return null;
		return ItemStorage.SIDED.find(entry.level, pos, null);
	}

	private long insertAll(ItemVariant variant, long amount, TransactionContext tx) {
		long left = amount;
		Set<Entry> tried = new HashSet<>();
		// The chest chosen with "Store here" comes first.
		GlobalPos targetPos = PortableData.get(this.server).target(this.player.getUUID());
		Entry target = targetPos == null ? null : this.entries.get(targetPos);
		if (target != null && target.storesItems()) {
			left -= this.insertInto(target, variant, left, tx);
			tried.add(target);
		}
		// Then chests already holding this item, so things stay together.
		for (Entry entry : this.order) {
			if (left <= 0) break;
			if (entry.storesItems() && entry.contents.containsKey(variant) && tried.add(entry)) {
				left -= this.insertInto(entry, variant, left, tx);
			}
		}
		// Then whichever has room.
		for (Entry entry : this.order) {
			if (left <= 0) break;
			if (entry.storesItems() && tried.add(entry)) {
				left -= this.insertInto(entry, variant, left, tx);
			}
		}
		return amount - left;
	}

	private long insertInto(Entry entry, ItemVariant variant, long amount, TransactionContext tx) {
		Storage<ItemVariant> storage = this.storageOf(entry);
		if (storage == null) return 0;
		long moved = storage.insert(variant, amount, tx);
		if (moved > 0) this.touched.add(entry);
		return moved;
	}

	private long extractAll(ItemVariant variant, long amount, TransactionContext tx) {
		long left = amount;
		for (Entry entry : this.order) {
			if (left <= 0) break;
			if (!entry.storesItems() || !entry.contents.containsKey(variant)) continue;
			Storage<ItemVariant> storage = this.storageOf(entry);
			if (storage == null) continue;
			long moved = storage.extract(variant, left, tx);
			if (moved > 0) {
				left -= moved;
				this.touched.add(entry);
			}
		}
		return amount - left;
	}

	/** Puts a stack into storage and returns how many didn't fit. The caller keeps or gives back those. */
	public int insert(ItemStack stack) {
		if (stack.isEmpty() || !this.inRange) return stack.getCount();
		ItemVariant variant = ItemVariant.of(stack);
		long moved;
		try (Transaction tx = Transaction.openOuter()) {
			moved = this.insertAll(variant, stack.getCount(), tx);
			tx.commit();
		}
		int left = stack.getCount() - (int)moved;
		if (left > 0) {
			ServerActions.notify(this.player, PortablePayloads.NOTIFY_DIDNT_FIT, Component.empty(), stack.copyWithCount(1), left);
		}
		return left;
	}

	/** Takes up to {@code count} of an item out of storage and returns how many came. */
	public int extract(ItemStack like, int count) {
		if (!this.inRange || count <= 0) return 0;
		try (Transaction tx = Transaction.openOuter()) {
			int got = (int)this.extractAll(ItemVariant.of(like), count, tx);
			tx.commit();
			return got;
		}
	}

	public void takeToCursor(int id, boolean half) {
		ItemVariant variant = this.byId.get(id);
		if (variant == null || !this.inRange) return;
		long available = this.totals.getLong(variant);
		if (available <= 0) return;
		ItemStack carried = this.menu.getCarried();
		ItemStack sample = variant.toStack();
		int max = sample.getMaxStackSize();
		int want;
		if (carried.isEmpty()) {
			int full = (int)Math.min(available, max);
			want = half ? (full + 1) / 2 : full;
		} else if (variant.matches(carried)) {
			int room = max - carried.getCount();
			want = half ? Math.min(1, room) : room;
		} else {
			return;
		}
		int got = this.extract(sample, want);
		if (got <= 0) return;
		if (carried.isEmpty()) {
			this.menu.setCarried(sample.copyWithCount(got));
		} else {
			carried.grow(got);
		}
		this.menu.broadcastChanges();
	}

	public void takeToInventory(int id) {
		ItemVariant variant = this.byId.get(id);
		if (variant == null || !this.inRange) return;
		ItemStack sample = variant.toStack();
		int room = 0;
		for (int i = StorageMenu.INV_START; i < StorageMenu.HOTBAR_END; i++) {
			ItemStack inSlot = this.menu.getSlot(i).getItem();
			if (inSlot.isEmpty()) {
				room += sample.getMaxStackSize();
			} else if (ItemStack.isSameItemSameComponents(inSlot, sample)) {
				room += Math.max(0, inSlot.getMaxStackSize() - inSlot.getCount());
			}
		}
		int want = Math.min(room, sample.getMaxStackSize());
		int got = this.extract(sample, want);
		if (got <= 0) return;
		this.menu.moveToInventory(sample.copyWithCount(got));
		this.menu.broadcastChanges();
	}

	public void storeCarried(boolean one) {
		ItemStack carried = this.menu.getCarried();
		if (carried.isEmpty()) return;
		int count = one ? 1 : carried.getCount();
		int left = this.insert(carried.copyWithCount(count));
		carried.shrink(count - left);
		this.menu.broadcastChanges();
	}

	/** "Store all" and "Matching": the 27 main slots, never the hotbar or a portable crafting table. */
	public void storeInventory(boolean matchingOnly) {
		if (!this.inRange) return;
		for (int i = StorageMenu.INV_START; i < StorageMenu.INV_END; i++) {
			Slot slot = this.menu.getSlot(i);
			ItemStack stack = slot.getItem();
			if (stack.isEmpty() || stack.getItem() instanceof PortableCraftingTableItem) continue;
			if (matchingOnly && this.totals.getLong(ItemVariant.of(stack)) <= 0) continue;
			int left = this.insert(stack.copy());
			if (left == stack.getCount()) continue;
			if (left == 0) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				stack.setCount(left);
				slot.setChanged();
			}
		}
		this.menu.broadcastChanges();
	}

	/** Moves what a recipe needs into the inventory, where vanilla's placement code will look for it. */
	public Loan lendForRecipe(RecipeHolder<?> holder, boolean useMax, Inventory inventory) {
		Map<ItemVariant, Integer> lent = new LinkedHashMap<>();
		Map<ItemVariant, Integer> held = new LinkedHashMap<>();
		if (!this.inRange) return new Loan(lent, held);
		for (Ingredient ingredient : holder.value().placementInfo().ingredients()) {
			ItemVariant best = null;
			long bestCount = 0;
			for (Object2LongMap.Entry<ItemVariant> total : this.totals.object2LongEntrySet()) {
				if (total.getLongValue() > bestCount && ingredient.test(total.getKey().toStack())) {
					best = total.getKey();
					bestCount = total.getLongValue();
				}
			}
			if (best == null) continue;
			ItemStack sample = best.toStack();
			int want = useMax ? sample.getMaxStackSize() : 1;
			held.computeIfAbsent(best, v -> countInInventory(inventory, v));
			int got = this.extract(sample, want);
			if (got <= 0) continue;
			ItemStack lending = sample.copyWithCount(got);
			inventory.add(lending);
			if (!lending.isEmpty()) {
				// Inventory full: this part goes straight back.
				int stillOut = this.insert(lending.copy());
				if (stillOut > 0) {
					inventory.placeItemBackInInventory(lending.copyWithCount(stillOut), Prediction.SERVER_ONLY);
				}
				got -= lending.getCount();
			}
			lent.merge(best, got, Integer::sum);
		}
		return new Loan(lent, held);
	}

	/** Returns whatever the placement didn't use, leaving the inventory as it was. */
	public void settle(Loan loan, Inventory inventory) {
		for (Map.Entry<ItemVariant, Integer> entry : loan.lent().entrySet()) {
			ItemVariant variant = entry.getKey();
			int had = loan.held().getOrDefault(variant, 0);
			int extra = Math.min(entry.getValue(), countInInventory(inventory, variant) - had);
			if (extra <= 0) continue;
			int remaining = extra;
			var items = inventory.getNonEquipmentItems();
			for (int i = items.size() - 1; i >= 0 && remaining > 0; i--) {
				ItemStack stack = items.get(i);
				if (!variant.matches(stack)) continue;
				int taken = Math.min(remaining, stack.getCount());
				stack.shrink(taken);
				remaining -= taken;
			}
			int left = this.insert(variant.toStack(extra));
			if (left > 0) {
				inventory.placeItemBackInInventory(variant.toStack(left), Prediction.SERVER_ONLY);
			}
		}
		inventory.setChanged();
	}

	private static int countInInventory(Inventory inventory, ItemVariant variant) {
		int count = 0;
		for (ItemStack stack : inventory.getNonEquipmentItems()) {
			if (variant.matches(stack)) count += stack.getCount();
		}
		return count;
	}

	/** The id the client would use for an item, even if none is stored right now. */
	public int idFor(ItemStack like) {
		return this.idOf(ItemVariant.of(like));
	}

	/** Runs a tick's work right now, to measure it. */
	public void forceTickForTest() {
		this.lastTick = -1;
		this.tick();
	}
}
