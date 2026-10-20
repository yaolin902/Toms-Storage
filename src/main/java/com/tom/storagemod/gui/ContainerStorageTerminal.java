package com.tom.storagemod.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.tom.storagemod.InventoryNetwork;
import com.tom.storagemod.StorageMod;
import com.tom.storagemod.StoredItemStack;
import com.tom.storagemod.TerminalItemStack;
import com.tom.storagemod.net.PacketTerminalAction;
import com.tom.storagemod.net.PacketTerminalSync;
import com.tom.storagemod.sync.SyncEntry;
import com.tom.storagemod.sync.TerminalSyncClient;
import com.tom.storagemod.sync.TerminalSyncServer;
import com.tom.storagemod.tile.TileEntityStorageTerminal;

/**
 * Only the player inventory (and, for the crafting terminal, the grid) consists
 * of real slots. The big item grid is drawn and clicked by the GUI itself and
 * driven by packets, because a terminal can hold far more item types and larger
 * counts than slots can express.
 */
public class ContainerStorageTerminal extends Container {
	private static final int MAX_PACKET_BYTES = 24000;
	private static final int MAX_ENTRY_BYTES = 30000;
	/** Ticks between checks for changes. The check itself is cheap when nothing changed. */
	private static final int SYNC_INTERVAL = 2;
	/** Slot counts are capped like 2.4.2 does, so absurd networks don't overflow the display. */
	private static final int COUNT_CAP = Short.MAX_VALUE;

	protected final InventoryPlayer playerInv;
	protected final World world;
	protected final int posX, posY, posZ;
	/** Server side only. */
	protected final TileEntityStorageTerminal te;
	/** Index of the first main-inventory slot (27 main + 9 hotbar slots follow). */
	protected int playerSlotsStart;

	// ---- server side ----
	private final Queue<PacketTerminalAction> actionQueue = new ConcurrentLinkedQueue<PacketTerminalAction>();
	private final TerminalSyncServer syncServer = new TerminalSyncServer();
	private InventoryNetwork lastNet;
	private long lastChange = -1;
	private int lastSorting, lastModes, lastSearchType, lastSlotCount, lastFreeCount;
	private String lastSearchSent;
	private boolean needFull = true;
	private int tickCounter;
	private boolean forceSync = true;
	private boolean resyncSlots;

	// ---- client side ----
	private final TerminalSyncClient syncClient = new TerminalSyncClient();
	/** Latest complete list received from the server. Replaced as a whole; the lines in it keep updating in place. */
	public volatile List<TerminalItemStack> itemList = new ArrayList<TerminalItemStack>();
	/** Terminal settings saved on the server (layout: see TileEntityStorageTerminal). */
	public volatile int sorting, modes, searchType;
	/** Search text saved on the server. */
	public volatile String search;
	public volatile int slotCount, freeCount;
	/** True once the first list arrived. */
	public volatile boolean itemsLoaded;
	/** Incremented whenever a complete update arrived, so the GUI can react on the main thread. */
	public volatile int packetCounter;
	private int[] baseSlotY;

	public ContainerStorageTerminal(InventoryPlayer inv, World world, int x, int y, int z) {
		this(inv, world, x, y, z, true);
	}

	protected ContainerStorageTerminal(InventoryPlayer inv, World world, int x, int y, int z, boolean addPlayerSlots) {
		this.playerInv = inv;
		this.world = world;
		this.posX = x;
		this.posY = y;
		this.posZ = z;
		TileEntity tile = world.isRemote ? null : world.getTileEntity(x, y, z);
		this.te = tile instanceof TileEntityStorageTerminal ? (TileEntityStorageTerminal) tile : null;
		if (addPlayerSlots) addPlayerSlots(8, 120);
	}

	/** 27 main slots followed by the hotbar. */
	protected final void addPlayerSlots(int x, int y) {
		playerSlotsStart = inventorySlots.size();
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 9; j++) {
				addSlotToContainer(new Slot(playerInv, j + i * 9 + 9, x + j * 18, y + i * 18));
			}
		}
		for (int i = 0; i < 9; i++) {
			addSlotToContainer(new Slot(playerInv, i, x + i * 18, y + 58));
		}
	}

	/** Client: moves every real slot down by dy pixels (tall mode makes the item grid taller). */
	public void setSlotYOffset(int dy) {
		if (baseSlotY == null) {
			baseSlotY = new int[inventorySlots.size()];
			for (int i = 0; i < baseSlotY.length; i++) baseSlotY[i] = ((Slot) inventorySlots.get(i)).yDisplayPosition;
		}
		for (int i = 0; i < baseSlotY.length; i++) ((Slot) inventorySlots.get(i)).yDisplayPosition = baseSlotY[i] + dy;
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		if (te != null) return te.canInteractWith(player, true);
		return true;
	}

	// ------------------------------------------------------------------
	// Client: receiving updates
	// ------------------------------------------------------------------

	public void receiveSync(PacketTerminalSync msg) {
		if (msg.reset) syncClient.reset();
		syncClient.apply(msg.entries);
		if (msg.last) {
			itemList = syncClient.snapshot();
			sorting = msg.sorting;
			modes = msg.modes;
			searchType = msg.searchType;
			search = msg.search;
			slotCount = msg.slotCount;
			freeCount = msg.freeCount;
			itemsLoaded = true;
			packetCounter++;
		}
	}

	// ------------------------------------------------------------------
	// Server: syncing and handling clicks
	// ------------------------------------------------------------------

	/**
	 * Vanilla's shift-click (and taking a crafting result) runs on the client too, and while the
	 * server handles that click it suppresses slot updates, trusting the client's own prediction.
	 * Our changes only happen on the server, so the client would keep showing stale slots
	 * ("ghost" items). Asking for a resync sends every slot directly, bypassing that suppression.
	 */
	public void requestSlotResync() {
		resyncSlots = true;
	}

	/** Sends one slot straight to the player (bypasses vanilla's slot-update filtering). */
	protected final void sendSlotToPlayer(int index) {
		if (!(playerInv.player instanceof EntityPlayerMP)) return;
		Slot slot = (Slot) inventorySlots.get(index);
		((EntityPlayerMP) playerInv.player).playerNetServerHandler.sendPacket(new S2FPacketSetSlot(windowId, index, slot.getStack()));
	}

	private void resendAllSlots() {
		if (!(playerInv.player instanceof EntityPlayerMP)) return;
		for (int i = 0; i < inventorySlots.size(); i++) sendSlotToPlayer(i);
		((EntityPlayerMP) playerInv.player).updateHeldItem();
	}

	public void queueAction(PacketTerminalAction action) {
		actionQueue.add(action);
	}

	@Override
	public void detectAndSendChanges() {
		if (!world.isRemote) {
			PacketTerminalAction a;
			while ((a = actionQueue.poll()) != null) {
				processAction(a);
			}
		}
		super.detectAndSendChanges();
		if (!world.isRemote && resyncSlots) {
			resyncSlots = false;
			resendAllSlots();
		}
		if (world.isRemote || te == null || !(playerInv.player instanceof EntityPlayerMP)) return;

		if (forceSync || ++tickCounter % SYNC_INTERVAL == 0) {
			forceSync = false;
			syncToClient((EntityPlayerMP) playerInv.player);
		}
	}

	/** Sends whatever changed since the last time: item lines, settings, search text, slot statistics. */
	private void syncToClient(EntityPlayerMP player) {
		InventoryNetwork net = te.getNetwork();
		if (net == null) return;
		net.refresh(world);

		boolean full = needFull;
		if (full) {
			syncServer.reset();
			lastChange = -1;
			needFull = false;
		}

		List<SyncEntry> entries = Collections.emptyList();
		if (full || net != lastNet || net.getChangeCounter() != lastChange) {
			entries = syncServer.diff(net.getContents());
			lastChange = net.getChangeCounter();
			lastNet = net;
		}

		int sorting = te.getSorting(), modes = te.getModes(), searchType = te.getSearchType();
		String searchText = te.getLastSearch();
		int slots = Math.min(net.getTotalSlots(), COUNT_CAP), free = Math.min(net.getFreeSlotCount(), COUNT_CAP);
		boolean settingsChanged = full || sorting != lastSorting || modes != lastModes || searchType != lastSearchType
				|| slots != lastSlotCount || free != lastFreeCount || !searchText.equals(lastSearchSent);

		if (entries.isEmpty() && !settingsChanged) return;
		lastSorting = sorting;
		lastModes = modes;
		lastSearchType = searchType;
		lastSlotCount = slots;
		lastFreeCount = free;
		lastSearchSent = searchText;
		send(player, entries, full, sorting, modes, searchType, searchText, slots, free);
	}

	private void send(EntityPlayerMP player, List<SyncEntry> entries, boolean reset, int sorting, int modes, int searchType,
			String searchText, int slots, int free) {
		PacketTerminalSync pkt = newPacket(reset, sorting, modes, searchType, searchText, slots, free);
		int bytes = 0;
		for (SyncEntry e : entries) {
			int size = measure(e);
			if (size > MAX_ENTRY_BYTES && e.stack != null) {
				e.stack = makeMiniStack(e.stack);
				size = measure(e);
			}
			if (size > MAX_ENTRY_BYTES) continue; // still hopeless: skip rather than kick the player
			if (!pkt.entries.isEmpty() && bytes + size > MAX_PACKET_BYTES) {
				StorageMod.network.sendTo(pkt, player);
				pkt = newPacket(false, sorting, modes, searchType, searchText, slots, free);
				bytes = 0;
			}
			pkt.entries.add(e);
			bytes += size;
		}
		pkt.last = true;
		StorageMod.network.sendTo(pkt, player);
	}

	private static PacketTerminalSync newPacket(boolean reset, int sorting, int modes, int searchType, String searchText, int slots, int free) {
		PacketTerminalSync p = new PacketTerminalSync();
		p.reset = reset;
		p.sorting = sorting;
		p.modes = modes;
		p.searchType = searchType;
		p.search = searchText;
		p.slotCount = slots;
		p.freeCount = free;
		return p;
	}

	private static int measure(SyncEntry e) {
		ByteBuf tmp = Unpooled.buffer();
		PacketTerminalSync.writeEntry(tmp, e);
		return tmp.readableBytes();
	}

	/** Stand-in for an item whose NBT is too big to send: same item, no tag, with a warning in its tooltip. */
	private static ItemStack makeMiniStack(ItemStack real) {
		ItemStack mini = new ItemStack(real.getItem(), 1, real.getItemDamage());
		NBTTagCompound tag = new NBTTagCompound();
		NBTTagCompound display = new NBTTagCompound();
		NBTTagList lore = new NBTTagList();
		lore.appendTag(new NBTTagString("\u00a7cThis item's tag is too big, it may not show correctly in the terminal"));
		display.setTag("Lore", lore);
		tag.setTag("display", display);
		mini.setTagCompound(tag);
		return mini;
	}

	/** The item line a click refers to (null if unknown or no item). */
	private ItemStack keyFor(int itemId) {
		if (itemId == 0) return null;
		StoredItemStack s = syncServer.lookup(itemId);
		return s == null ? null : s.getStack();
	}

	protected void processAction(PacketTerminalAction a) {
		switch (a.action) {
		case REQUEST_SYNC:
			needFull = true; // forget what the client knows; the whole list goes out again
			forceSync = true;
			return;
		case SET_SETTINGS:
			if (te != null) {
				te.setSorting(a.data);
				te.setModes(a.data2);
				te.setSearchType(a.data3);
			}
			return;
		case SET_SEARCH:
			if (te != null) te.setLastSearch(a.text);
			return;
		default:
			break;
		}
		if (te == null) return;

		EntityPlayer player = playerInv.player;
		ItemStack held = playerInv.getItemStack();
		ItemStack key = keyFor(a.itemId);

		switch (a.action) {
		case PULL_OR_PUSH:
			if (held != null) {
				playerInv.setItemStack(te.pushStack(held));
			} else if (key != null) {
				playerInv.setItemStack(te.pullStack(key, key.getMaxStackSize()));
			}
			break;

		case PULL_HALF:
			splitAction(held, key, 2);
			break;

		case PULL_QUARTER:
			splitAction(held, key, 4);
			break;

		case PULL_ONE:
			if (key == null) break;
			if (held == null) {
				playerInv.setItemStack(te.pullStack(key, 1));
			} else if (StoredItemStack.matches(held, key) && held.stackSize < held.getMaxStackSize()) {
				if (te.pullStack(key, 1) != null) held.stackSize++;
			}
			break;

		case SHIFT_PULL:
			if (key != null) giveToPlayer(key, key.getMaxStackSize());
			break;

		case TRANSFER_ONE:
			if (key != null) giveToPlayer(key, 1);
			break;

		case SPACE_CLICK:
			for (int i = playerSlotsStart; i < playerSlotsStart + 27; i++) transferStackInSlot(player, i);
			break;

		default:
			handleExtraAction(a);
			break;
		}

		if (player instanceof EntityPlayerMP) ((EntityPlayerMP) player).updateHeldItem();
		forceSync = true;
	}

	/** Hook for subclasses (crafting terminal). */
	protected void handleExtraAction(PacketTerminalAction a) {
	}

	/** Right click: insert 1/divisor of the held stack, or pick up 1/divisor of a stack from storage. */
	private void splitAction(ItemStack held, ItemStack key, int divisor) {
		if (held != null) {
			int n = Math.max(1, Math.min(held.stackSize, held.getMaxStackSize()) / divisor);
			ItemStack part = held.copy();
			part.stackSize = n;
			ItemStack rest = te.pushStack(part);
			held.stackSize -= n - (rest == null ? 0 : rest.stackSize);
			playerInv.setItemStack(held.stackSize > 0 ? held : null);
		} else if (key != null) {
			long total = te.getCount(key);
			int n = (int) Math.max(1, Math.min(total, key.getMaxStackSize()) / divisor);
			playerInv.setItemStack(te.pullStack(key, n));
		}
	}

	/** Pulls items out of storage and puts them into the player's inventory; leftovers go back or drop. */
	private void giveToPlayer(ItemStack key, int amount) {
		ItemStack out = te.pullStack(key, Math.min(amount, key.getMaxStackSize()));
		if (out == null) return;
		mergeItemStack(out, playerSlotsStart, playerSlotsStart + 36, true);
		if (out.stackSize > 0) te.pushOrDrop(out);
	}

	/** Shift-click: on the player's slots push into storage, anything else is up to the subclass. */
	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {
		if (world.isRemote || index < 0 || index >= inventorySlots.size()) return null;
		requestSlotResync();
		if (index >= playerSlotsStart) {
			if (te == null) return null;
			Slot slot = (Slot) inventorySlots.get(index);
			if (slot == null || !slot.getHasStack()) return null;
			slot.putStack(te.pushStack(slot.getStack()));
			forceSync = true;
			return null; // returning null stops the vanilla shift-click repeat loop
		}
		return shiftClickItems(player, index);
	}

	protected ItemStack shiftClickItems(EntityPlayer player, int index) {
		return null;
	}
}
