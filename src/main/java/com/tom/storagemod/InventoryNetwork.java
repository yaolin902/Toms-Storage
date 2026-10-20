package com.tom.storagemod;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;

import net.minecraftforge.common.util.ForgeDirection;

import com.tom.storagemod.tile.TileEntityInventoryConnector;
import com.tom.storagemod.util.TerminalOrientation;

/**
 * A set of inventories that a terminal treats as one big pool of items.
 *
 * The network is long lived: it keeps a snapshot of every slot and a change counter, so any number of
 * terminals can ask "did anything change?" for the price of one cheap comparison pass per game tick,
 * and the aggregated item list is only rebuilt after a real change.
 */
public class InventoryNetwork {
	private List<InventoryAccess> inventories = new ArrayList<InventoryAccess>();
	private long changeCounter = 1;
	private long lastRefreshTick = Long.MIN_VALUE;
	private Map<StoredItemStack, TerminalItemStack> contents;
	private int totalSlots, freeSlots;

	public InventoryNetwork(List<InventoryAccess> list) {
		setInventories(list);
	}

	/**
	 * Replaces the member inventories. Members that are still present are kept (with their snapshots),
	 * duplicates and inventories with no reachable slot are dropped.
	 */
	public void setInventories(List<InventoryAccess> candidates) {
		List<InventoryAccess> result = new ArrayList<InventoryAccess>(candidates.size());
		IdentityHashMap<IInventory, Boolean> seen = new IdentityHashMap<IInventory, Boolean>();
		boolean differs = candidates.size() != inventories.size();
		for (InventoryAccess cand : candidates) {
			if (!cand.isUsable()) continue;
			// the same inventory object can be reachable through several tiles/faces: count it once
			if (seen.put(cand.getInventory(), Boolean.TRUE) != null) continue;
			InventoryAccess keep = cand;
			for (InventoryAccess old : inventories) {
				if (old.matches(cand.getInventory(), cand.sideOf())) {
					keep = old;
					break;
				}
			}
			result.add(keep);
		}
		if (result.size() != inventories.size()) differs = true;
		else {
			for (int i = 0; i < result.size() && !differs; i++) differs = result.get(i) != inventories.get(i);
		}
		inventories = result;
		if (differs) {
			changeCounter++;
			contents = null;
			lastRefreshTick = Long.MIN_VALUE;
		}
	}

	/** The member that already wraps this inventory/side, so scans can reuse it instead of allocating. */
	public InventoryAccess findExisting(IInventory inv, int side) {
		for (InventoryAccess a : inventories) {
			if (a.matches(inv, side)) return a;
		}
		return null;
	}

	/** Forces the next {@link #refresh} to re-read the inventories (after we changed them ourselves). */
	public void invalidate() {
		lastRefreshTick = Long.MIN_VALUE;
	}

	/**
	 * Compares every slot against the snapshot, at most once per game tick.
	 *
	 * @return true if something changed during this call
	 */
	public boolean refresh(World world) {
		long tick = world.getTotalWorldTime();
		if (tick == lastRefreshTick) return false;
		lastRefreshTick = tick;
		boolean changed = false;
		int total = 0, free = 0;
		for (InventoryAccess a : inventories) {
			changed |= a.refresh();
			total += a.getSlotCount();
			free += a.getFreeSlotCount();
		}
		totalSlots = total;
		freeSlots = free;
		if (changed) {
			changeCounter++;
			contents = null;
		}
		return changed;
	}

	/** Increases whenever the item list changed. */
	public long getChangeCounter() {
		return changeCounter;
	}

	/** Every item type with its total quantity and the number of slots it occupies, as of the last refresh. */
	public Map<StoredItemStack, TerminalItemStack> getContents() {
		if (contents == null) {
			Map<StoredItemStack, TerminalItemStack> map = new LinkedHashMap<StoredItemStack, TerminalItemStack>();
			for (InventoryAccess a : inventories) {
				for (int i = 0; i < a.getSlotCount(); i++) {
					ItemStack s = a.getSnapshot(i);
					if (s == null) continue;
					TerminalItemStack entry = new TerminalItemStack(s);
					TerminalItemStack existing = map.get(entry);
					if (existing == null) {
						map.put(entry, entry);
					} else {
						existing.grow(entry.getQuantity());
						existing.setUsedSlotCount(existing.getUsedSlotCount() + 1);
					}
				}
			}
			contents = map;
		}
		return contents;
	}

	public int getInventoryCount() {
		return inventories.size();
	}

	/** Number of slots reachable through this network (as of the last refresh). */
	public int getTotalSlots() {
		return totalSlots;
	}

	public int getFreeSlotCount() {
		return freeSlots;
	}

	/** Live count of matching items in the real inventories. */
	public long getCount(ItemStack key) {
		long total = 0;
		for (InventoryAccess a : inventories) total += a.count(key);
		return total;
	}

	/** Takes up to max items matching key (max should not exceed key's max stack size). */
	public ItemStack extract(ItemStack key, int max) {
		ItemStack result = null;
		int remaining = max;
		for (InventoryAccess a : inventories) {
			if (remaining < 1) break;
			ItemStack got = a.extract(key, remaining);
			if (got == null) continue;
			remaining -= got.stackSize;
			if (result == null) result = got;
			else result.stackSize += got.stackSize;
		}
		if (result != null) invalidate();
		return result;
	}

	/** Inserts a copy of stack. Returns the part that did not fit, or null. */
	public ItemStack insert(ItemStack stack) {
		if (stack == null) return null;
		ItemStack rem = stack.copy();
		int before = rem.stackSize;
		// First top up existing stacks everywhere, then fall back to empty slots.
		for (InventoryAccess a : inventories) {
			rem = a.insert(rem, false);
			if (rem == null) break;
		}
		if (rem != null) {
			for (InventoryAccess a : inventories) {
				rem = a.insert(rem, true);
				if (rem == null) break;
			}
		}
		if (rem == null || rem.stackSize != before) invalidate();
		return rem;
	}

	// ---------------------------------------------------------------------
	// Finding what a terminal is attached to
	// ---------------------------------------------------------------------

	/** The connector the terminal at (x,y,z) is mounted on, or null. */
	public static TileEntityInventoryConnector findConnector(World world, int x, int y, int z) {
		ForgeDirection facing = TerminalOrientation.getMountDirection(world.getBlockMetadata(x, y, z));
		if (facing == ForgeDirection.UNKNOWN) return null;
		int ix = x + facing.offsetX, iy = y + facing.offsetY, iz = z + facing.offsetZ;
		if (!world.blockExists(ix, iy, iz)) return null;
		TileEntity te = world.getTileEntity(ix, iy, iz);
		return te instanceof TileEntityInventoryConnector ? (TileEntityInventoryConnector) te : null;
	}

	/** The inventories a terminal that is mounted directly on a chest/furnace/etc. can reach. Empty if none. */
	public static List<InventoryAccess> collectDirect(World world, int x, int y, int z) {
		List<InventoryAccess> list = new ArrayList<InventoryAccess>();
		ForgeDirection facing = TerminalOrientation.getMountDirection(world.getBlockMetadata(x, y, z));
		if (facing == ForgeDirection.UNKNOWN) return list;
		int ix = x + facing.offsetX, iy = y + facing.offsetY, iz = z + facing.offsetZ;
		if (!world.blockExists(ix, iy, iz)) return list;
		TileEntity te = world.getTileEntity(ix, iy, iz);
		if (!(te instanceof IInventory) || te instanceof TileEntityInventoryConnector || te.isInvalid()) return list;

		int side = facing.getOpposite().ordinal();
		list.add(new InventoryAccess((IInventory) te, side));
		if (te instanceof TileEntityChest) {
			// 1.7.10 has no "double chest" object: two same-type chests that touch
			// horizontally are a double chest, so add the neighbour as well.
			Block block = world.getBlock(ix, iy, iz);
			for (int i = 2; i < 6; i++) {
				ForgeDirection d = ForgeDirection.getOrientation(i);
				int nx = ix + d.offsetX, nz = iz + d.offsetZ;
				if (world.blockExists(nx, iy, nz) && world.getBlock(nx, iy, nz) == block) {
					TileEntity other = world.getTileEntity(nx, iy, nz);
					if (other instanceof TileEntityChest && !other.isInvalid()) {
						list.add(new InventoryAccess((IInventory) other, side));
					}
				}
			}
		}
		return list;
	}

	/** One-shot network for a terminal (used where no persistent one is kept, e.g. tests). */
	public static InventoryNetwork forTerminal(World world, int x, int y, int z) {
		TileEntityInventoryConnector con = findConnector(world, x, y, z);
		if (con != null) return con.getNetwork();
		List<InventoryAccess> direct = collectDirect(world, x, y, z);
		return direct.isEmpty() ? null : new InventoryNetwork(direct);
	}
}
