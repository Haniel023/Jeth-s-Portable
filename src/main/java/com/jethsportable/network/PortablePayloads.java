package com.jethsportable.network;

import com.jethsportable.JethsPortable;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public final class PortablePayloads {
	private static final int MAX_LARGE_PACKET = 8 * 1024 * 1024;
	private static final StreamCodec<ByteBuf, GlobalPos> POS = GlobalPos.STREAM_CODEC;
	private static final StreamCodec<ByteBuf, UUID> ID = UUIDUtil.STREAM_CODEC;
	private static final StreamCodec<ByteBuf, String> TEXT = ByteBufCodecs.stringUtf8(128);

	private PortablePayloads() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(BasesPayload.TYPE, BasesPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenedPayload.TYPE, OpenedPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().registerLarge(ItemsPayload.TYPE, ItemsPayload.CODEC, MAX_LARGE_PACKET);
		PayloadTypeRegistry.clientboundPlay().register(StatusPayload.TYPE, StatusPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().registerLarge(LinksPayload.TYPE, LinksPayload.CODEC, MAX_LARGE_PACKET);
		PayloadTypeRegistry.clientboundPlay().registerLarge(MachinesPayload.TYPE, MachinesPayload.CODEC, MAX_LARGE_PACKET);
		PayloadTypeRegistry.clientboundPlay().register(NotifyPayload.TYPE, NotifyPayload.CODEC);

		PayloadTypeRegistry.serverboundPlay().register(OpenStoragePayload.TYPE, OpenStoragePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(OpenCraftingPayload.TYPE, OpenCraftingPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SetTabPayload.TYPE, SetTabPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ItemActionPayload.TYPE, ItemActionPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(GridActionPayload.TYPE, GridActionPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LinkActionPayload.TYPE, LinkActionPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MachineActionPayload.TYPE, MachineActionPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BaseActionPayload.TYPE, BaseActionPayload.CODEC);
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
		return new CustomPacketPayload.Type<>(JethsPortable.id(name));
	}

	// Data sent to the client

	public record TrustedInfo(UUID id, String name) {
		static final StreamCodec<ByteBuf, TrustedInfo> CODEC = StreamCodec.composite(ID, TrustedInfo::id, TEXT, TrustedInfo::name, TrustedInfo::new);
	}

	/** One base the player belongs to. Enough for the client to work out range by itself. */
	public record BaseInfo(UUID id, String name, String ownerName, boolean isOwner, int level, int range, int maxLinks, Optional<GlobalPos> core, List<TrustedInfo> trusted, List<GlobalPos> links) {
		static final StreamCodec<ByteBuf, BaseInfo> CODEC = new StreamCodec<>() {
			@Override
			public BaseInfo decode(ByteBuf input) {
				return new BaseInfo(
					ID.decode(input), TEXT.decode(input), TEXT.decode(input), input.readBoolean(),
					ByteBufCodecs.VAR_INT.decode(input), ByteBufCodecs.VAR_INT.decode(input), ByteBufCodecs.VAR_INT.decode(input),
					ByteBufCodecs.optional(POS).decode(input),
					TrustedInfo.CODEC.apply(ByteBufCodecs.list()).decode(input),
					POS.apply(ByteBufCodecs.list()).decode(input)
				);
			}

			@Override
			public void encode(ByteBuf output, BaseInfo value) {
				ID.encode(output, value.id());
				TEXT.encode(output, value.name());
				TEXT.encode(output, value.ownerName());
				output.writeBoolean(value.isOwner());
				ByteBufCodecs.VAR_INT.encode(output, value.level());
				ByteBufCodecs.VAR_INT.encode(output, value.range());
				ByteBufCodecs.VAR_INT.encode(output, value.maxLinks());
				ByteBufCodecs.optional(POS).encode(output, value.core());
				TrustedInfo.CODEC.apply(ByteBufCodecs.list()).encode(output, value.trusted());
				POS.apply(ByteBufCodecs.list()).encode(output, value.links());
			}
		};
	}

	/** Every base the player belongs to, sent on join and whenever one changes. */
	public record BasesPayload(List<BaseInfo> bases) implements CustomPacketPayload {
		public static final Type<BasesPayload> TYPE = payloadType("bases");
		public static final StreamCodec<RegistryFriendlyByteBuf, BasesPayload> CODEC = BaseInfo.CODEC.apply(ByteBufCodecs.<ByteBuf, BaseInfo>list()).map(BasesPayload::new, BasesPayload::bases).cast();

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * A storage window was just opened on this tab. Everything about a window carries its session
	 * number, because large packets can overtake small ones on the way to the client.
	 */
	public record OpenedPayload(int session, int tab) implements CustomPacketPayload {
		public static final Type<OpenedPayload> TYPE = payloadType("opened");
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenedPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, OpenedPayload::session, ByteBufCodecs.VAR_INT, OpenedPayload::tab, OpenedPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record NewItem(int id, ItemVariant variant, long count) {
		static final StreamCodec<RegistryFriendlyByteBuf, NewItem> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, NewItem::id, ItemVariant.PACKET_CODEC, NewItem::variant, ByteBufCodecs.VAR_LONG, NewItem::count, NewItem::new
		);
	}

	public record ItemCount(int id, long count) {
		static final StreamCodec<ByteBuf, ItemCount> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, ItemCount::id, ByteBufCodecs.VAR_LONG, ItemCount::count, ItemCount::new);
	}

	/** Changes to the merged item list. Ids stay the same for the life of a window. */
	public record ItemsPayload(int session, List<NewItem> added, List<ItemCount> changed) implements CustomPacketPayload {
		public static final Type<ItemsPayload> TYPE = payloadType("items");
		public static final StreamCodec<RegistryFriendlyByteBuf, ItemsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ItemsPayload::session,
			NewItem.CODEC.apply(ByteBufCodecs.list()), ItemsPayload::added,
			ItemCount.CODEC.<RegistryFriendlyByteBuf>cast().apply(ByteBufCodecs.list()), ItemsPayload::changed,
			ItemsPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Summary line of the window: how many chests are reachable and how full they are. */
	public record StatusPayload(int session, boolean inRange, int chests, int machines, int usedSlots, int totalSlots) implements CustomPacketPayload {
		public static final Type<StatusPayload> TYPE = payloadType("status");
		public static final StreamCodec<RegistryFriendlyByteBuf, StatusPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StatusPayload::session,
			ByteBufCodecs.BOOL, StatusPayload::inRange,
			ByteBufCodecs.VAR_INT, StatusPayload::chests,
			ByteBufCodecs.VAR_INT, StatusPayload::machines,
			ByteBufCodecs.VAR_INT, StatusPayload::usedSlots,
			ByteBufCodecs.VAR_INT, StatusPayload::totalSlots,
			StatusPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static final int STATE_OK = 0;
	public static final int STATE_LOADING = 1;
	public static final int STATE_MISSING = 2;
	public static final int STATE_OUT_OF_RANGE = 3;

	public record LinkInfo(GlobalPos pos, String name, ItemStack icon, int used, int total, int state, boolean target) {
		static final StreamCodec<RegistryFriendlyByteBuf, LinkInfo> CODEC = StreamCodec.composite(
			POS, LinkInfo::pos,
			TEXT, LinkInfo::name,
			ItemStack.OPTIONAL_STREAM_CODEC, LinkInfo::icon,
			ByteBufCodecs.VAR_INT, LinkInfo::used,
			ByteBufCodecs.VAR_INT, LinkInfo::total,
			ByteBufCodecs.VAR_INT, LinkInfo::state,
			ByteBufCodecs.BOOL, LinkInfo::target,
			LinkInfo::new
		);
	}

	public record LinksPayload(int session, List<LinkInfo> links) implements CustomPacketPayload {
		public static final Type<LinksPayload> TYPE = payloadType("links");
		public static final StreamCodec<RegistryFriendlyByteBuf, LinksPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, LinksPayload::session, LinkInfo.CODEC.apply(ByteBufCodecs.list()), LinksPayload::links, LinksPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A furnace-like or brewing machine: progress values run from 0 to 100. */
	public record MachineInfo(GlobalPos pos, String name, ItemStack icon, ItemStack input, ItemStack fuel, ItemStack output, int progress, int burn, int state, boolean brewing) {
		static final StreamCodec<RegistryFriendlyByteBuf, MachineInfo> CODEC = new StreamCodec<>() {
			@Override
			public MachineInfo decode(RegistryFriendlyByteBuf input) {
				return new MachineInfo(
					POS.decode(input), TEXT.decode(input),
					ItemStack.OPTIONAL_STREAM_CODEC.decode(input), ItemStack.OPTIONAL_STREAM_CODEC.decode(input),
					ItemStack.OPTIONAL_STREAM_CODEC.decode(input), ItemStack.OPTIONAL_STREAM_CODEC.decode(input),
					input.readByte(), input.readByte(), input.readByte(), input.readBoolean()
				);
			}

			@Override
			public void encode(RegistryFriendlyByteBuf output, MachineInfo value) {
				POS.encode(output, value.pos());
				TEXT.encode(output, value.name());
				ItemStack.OPTIONAL_STREAM_CODEC.encode(output, value.icon());
				ItemStack.OPTIONAL_STREAM_CODEC.encode(output, value.input());
				ItemStack.OPTIONAL_STREAM_CODEC.encode(output, value.fuel());
				ItemStack.OPTIONAL_STREAM_CODEC.encode(output, value.output());
				output.writeByte(value.progress());
				output.writeByte(value.burn());
				output.writeByte(value.state());
				output.writeBoolean(value.brewing());
			}
		};
	}

	public record MachinesPayload(int session, List<MachineInfo> machines) implements CustomPacketPayload {
		public static final Type<MachinesPayload> TYPE = payloadType("machines");
		public static final StreamCodec<RegistryFriendlyByteBuf, MachinesPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MachinesPayload::session, MachineInfo.CODEC.apply(ByteBufCodecs.list()), MachinesPayload::machines, MachinesPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static final int NOTIFY_BATCH_DONE = 0;
	public static final int NOTIFY_NO_FUEL = 1;
	public static final int NOTIFY_OUTPUT_FULL = 2;
	public static final int NOTIFY_BREWING_DONE = 3;
	public static final int NOTIFY_STORAGE_FULL = 4;
	public static final int NOTIFY_DIDNT_FIT = 5;

	/** A pop-up. The client decides from its settings whether to show it. {@code count} isn't capped by the stack size. */
	public record NotifyPayload(int kind, Component where, ItemStack icon, int count) implements CustomPacketPayload {
		public static final Type<NotifyPayload> TYPE = payloadType("notify");
		public static final StreamCodec<RegistryFriendlyByteBuf, NotifyPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, NotifyPayload::kind,
			ComponentSerialization.STREAM_CODEC, NotifyPayload::where,
			ItemStack.OPTIONAL_STREAM_CODEC, NotifyPayload::icon,
			ByteBufCodecs.VAR_INT, NotifyPayload::count,
			NotifyPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	// Requests from the client. The server checks membership, range and rate on every one.

	public record OpenStoragePayload(int tab) implements CustomPacketPayload {
		public static final Type<OpenStoragePayload> TYPE = payloadType("open_storage");
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenStoragePayload> CODEC = ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast().map(OpenStoragePayload::new, OpenStoragePayload::tab);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record OpenCraftingPayload() implements CustomPacketPayload {
		public static final Type<OpenCraftingPayload> TYPE = payloadType("open_crafting");
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenCraftingPayload> CODEC = StreamCodec.unit(new OpenCraftingPayload());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record SetTabPayload(int tab) implements CustomPacketPayload {
		public static final Type<SetTabPayload> TYPE = payloadType("set_tab");
		public static final StreamCodec<RegistryFriendlyByteBuf, SetTabPayload> CODEC = ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast().map(SetTabPayload::new, SetTabPayload::tab);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static final int ITEM_TAKE_STACK = 0;
	public static final int ITEM_TAKE_HALF = 1;
	public static final int ITEM_TAKE_TO_INVENTORY = 2;
	public static final int ITEM_STORE_CARRIED = 3;
	public static final int ITEM_STORE_ONE = 4;

	/** A click on the item grid. {@code id} is -1 for an empty cell. */
	public record ItemActionPayload(int id, int action) implements CustomPacketPayload {
		public static final Type<ItemActionPayload> TYPE = payloadType("item_action");
		public static final StreamCodec<RegistryFriendlyByteBuf, ItemActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ItemActionPayload::id, ByteBufCodecs.VAR_INT, ItemActionPayload::action, ItemActionPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static final int GRID_STORE_ALL = 0;
	public static final int GRID_STORE_MATCHING = 1;
	public static final int GRID_CLEAR = 2;

	public record GridActionPayload(int action) implements CustomPacketPayload {
		public static final Type<GridActionPayload> TYPE = payloadType("grid_action");
		public static final StreamCodec<RegistryFriendlyByteBuf, GridActionPayload> CODEC = ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast().map(GridActionPayload::new, GridActionPayload::action);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static final int LINK_OPEN = 0;
	public static final int LINK_TARGET = 1;
	public static final int LINK_RENAME = 2;
	public static final int LINK_UNLINK = 3;

	public record LinkActionPayload(GlobalPos pos, int action, String text) implements CustomPacketPayload {
		public static final Type<LinkActionPayload> TYPE = payloadType("link_action");
		public static final StreamCodec<RegistryFriendlyByteBuf, LinkActionPayload> CODEC = StreamCodec.composite(
			POS, LinkActionPayload::pos, ByteBufCodecs.VAR_INT, LinkActionPayload::action, TEXT, LinkActionPayload::text, LinkActionPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static final int MACHINE_OPEN = 0;
	public static final int MACHINE_COLLECT = 1;
	public static final int MACHINE_COLLECT_TO_STORAGE = 2;

	public record MachineActionPayload(GlobalPos pos, int action) implements CustomPacketPayload {
		public static final Type<MachineActionPayload> TYPE = payloadType("machine_action");
		public static final StreamCodec<RegistryFriendlyByteBuf, MachineActionPayload> CODEC = StreamCodec.composite(
			POS, MachineActionPayload::pos, ByteBufCodecs.VAR_INT, MachineActionPayload::action, MachineActionPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static final int BASE_RENAME = 0;
	public static final int BASE_TRUST = 1;
	public static final int BASE_UNTRUST = 2;

	public record BaseActionPayload(UUID base, int action, String text) implements CustomPacketPayload {
		public static final Type<BaseActionPayload> TYPE = payloadType("base_action");
		public static final StreamCodec<RegistryFriendlyByteBuf, BaseActionPayload> CODEC = StreamCodec.composite(
			ID, BaseActionPayload::base, ByteBufCodecs.VAR_INT, BaseActionPayload::action, TEXT, BaseActionPayload::text, BaseActionPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
