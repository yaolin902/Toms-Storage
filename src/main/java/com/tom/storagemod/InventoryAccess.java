package com.tom.storagemod;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

/**
 * One inventory as seen from one side (the 1.7.10 stand-in for an IItemHandler capability):
 * sided inventories only expose the slots a hopper could reach on that side.
 *
 * It also remembers a snapshot of its slots, so {@link #refresh()} can tell cheaply whether anything
 * changed since last time. Reading the snapshot never touches the real inventory.
 */
public class InventoryAccess {
	private final IInventory inv;
	private final ISidedInventory sided;
	/** Side of the inventory block that touches the connector/terminal. */
	private final int side;
	private int[] slots = new int[0];
	private int lastSize = -1;
	/** Copy of what each exposed slot held at the last refresh (null = empty), indexed like {@link #slots}. */
	private ItemStack[] snapshot = new ItemStack[0];
	private int freeSlots;

	public InventoryAccess(IInventory inv, int side) {
		this.inv = inv;
		this.side = side;
		this.sided = inv instanceof ISidedInventory ? (ISidedInventory) inv : null;
		updateSlots();
	}

	/** Same backing inventory and side: the connector reuses such objects between scans to keep their snapshots. */
	public boolean matches(IInventory other, int otherSide) {
		return inv == other && side == otherSide;
	}

	public IInventory getInventory() {
		return inv;
	}

	public int sideOf() {
		return side;
	}

	/** False once the backing tile has been removed from the world. */
	public boolean isValid() {
		return !(inv instanceof TileEntity) || !((TileEntity) inv).isInvalid();
	}

	/** An inventory with nothing reachable from this side (or a 0-slot one) is not worth tracking. */
	public boolean isUsable() {
		return slots.length > 0;
	}

	private void updateSlots() {
		if (sided != null) {
			int[] s = sided.getAccessibleSlotsFromSide(side);
			slots = s == null ? new int[0] : s;
		} else {
			int size = inv.getSizeInventory();
			if (size != lastSize) {
				lastSize = size;
				slots = new int[Math.max(size, 0)];
				for (int i = 0; i < slots.length; i++) slots[i] = i;
			}
		}
	}

	public int getSlotCount() {
		return slots.length;
	}

	public int getFreeSlotCount() {
		return freeSlots;
	}

	/** Slot contents as of the last {@link #refresh()}; null for empty. */
	public ItemStack getSnapshot(int index) {
		return snapshot[index];
	}

	/** Re-reads every exposed slot, updating the snapshot. Returns true if anything differs from before. */
	public boolean refresh() {
		boolean changed = false;
		if (!isValid()) {
			changed = snapshot.length > 0;
			snapshot = new ItemStack[0];
			slots = new int[0];
			lastSize = -1;
			freeSlots = 0;
			return changed;
		}
		updateSlots();
		if (snapshot.length != slots.length) {
			snapshot = new ItemStack[slots.length];
			changed = true;
		}
		int free = 0;
		for (int i = 0; i < slots.length; i++) {
			ItemStack cur = inv.getStackInSlot(slots[i]);
			if (cur == null || cur.stackSize < 1) {
				if (snapshot[i] != null) {
					snapshot[i] = null;
					changed = true;
				}
				free++;
			} else {
				ItemStack old = snapshot[i];
				if (old == null || old.getItem() != cur.getItem() || old.getItemDamage() != cur.getItemDamage()
						|| old.stackSize != cur.stackSize || !ItemStack.areItemStackTagsEqual(old, cur)) {
					snapshot[i] = cur.copy();
					changed = true;
				}
			}
		}
		freeSlots = free;
		return changed;
	}

	// ------------------------------------------------------------------
	// Moving items
	// ------------------------------------------------------------------

	private boolean canExtract(int slot, ItemStack stack) {
		return sided == null || sided.canExtractItem(slot, stack, side);
	}

	private boolean canInsert(int slot, ItemStack stack) {
		return inv.isItemValidForSlot(slot, stack) && (sided == null || sided.canInsertItem(slot, stack, side));
	}

	/** Number of matching items currently in the real inventory. */
	public long count(ItemStack key) {
		if (!isValid()) return 0;
		long total = 0;
		for (int i = 0; i < slots.length; i++) {
			ItemStack s = inv.getStackInSlot(slots[i]);
			if (s != null && StoredItemStack.matches(s, key)) total += s.stackSize;
		}
		return total;
	}

	/** Removes up to max items matching key. Returns null if nothing was taken. */
	public ItemStack extract(ItemStack key, int max) {
		if (!isValid() || max < 1) return null;
		ItemStack result = null;
		int remaining = max;
		for (int i = 0; i < slots.length && remaining > 0; i++) {
			int slot = slots[i];
			ItemStack s = inv.getStackInSlot(slot);
			if (s == null || s.stackSize < 1 || !StoredItemStack.matches(s, key)) continue;
			if (!canExtract(slot, s)) continue;
			ItemStack taken = inv.decrStackSize(slot, Math.min(remaining, s.stackSize));
			if (taken == null || taken.stackSize < 1) continue;
			inv.markDirty();
			remaining -= taken.stackSize;
			if (result == null) result = taken;
			else result.stackSize += taken.stackSize;
		}
		return result;
	}

	/**
	 * Inserts as much of stack as fits. The passed stack is consumed/modified.
	 *
	 * @param allowEmpty false = only top up existing stacks, true = also use empty slots
	 * @return what did not fit, or null if everything was inserted
	 */
	public ItemStack insert(ItemStack stack, boolean allowEmpty) {
		if (stack == null || !isValid()) return stack;
		for (int i = 0; i < slots.length; i++) {
			int slot = slots[i];
			if (!canInsert(slot, stack)) continue;
			int limit = Math.min(inv.getInventoryStackLimit(), stack.getMaxStackSize());
			ItemStack in = inv.getStackInSlot(slot);
			if (in == null) {
				if (!allowEmpty) continue;
				int n = Math.min(limit, stack.stackSize);
				ItemStack put = stack.copy();
				put.stackSize = n;
				inv.setInventorySlotContents(slot, put);
				inv.markDirty();
				stack.stackSize -= n;
			} else if (in.stackSize < limit && StoredItemStack.matches(in, stack)) {
				int n = Math.min(limit - in.stackSize, stack.stackSize);
				ItemStack merged = in.copy();
				merged.stackSize += n;
				inv.setInventorySlotContents(slot, merged);
				inv.markDirty();
				stack.stackSize -= n;
			}
			if (stack.stackSize < 1) return null;
		}
		return stack;
	}
}
