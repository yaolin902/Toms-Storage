package com.tom.storagemod.sync;

import net.minecraft.item.ItemStack;

/** One changed line of a terminal's item list. */
public class SyncEntry {
	/** Session id of the item type (stable while the terminal is open). */
	public int id;
	/** New total quantity; 0 means the item type is gone. */
	public long count;
	/** Number of slots the item type occupies. */
	public int slots;
	/** The item (stack size 1), only present the first time the client hears about this id. */
	public ItemStack stack;

	public SyncEntry() {
	}

	public SyncEntry(int id, long count, int slots, ItemStack stack) {
		this.id = id;
		this.count = count;
		this.slots = slots;
		this.stack = stack;
	}
}
