package com.tom.storagemod.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tom.storagemod.StoredItemStack;
import com.tom.storagemod.TerminalItemStack;

/**
 * Client half of the terminal item sync: applies the server's changes to the list of item lines.
 * Lines are updated in place, so a line that vanishes keeps existing with a count of 0
 * (which is what makes "ghost items" possible while the order is frozen).
 */
public class TerminalSyncClient {
	private final Map<Integer, StoredItemStack> stacks = new HashMap<Integer, StoredItemStack>();
	private final Map<Integer, TerminalItemStack> lines = new HashMap<Integer, TerminalItemStack>();

	public void reset() {
		stacks.clear();
		lines.clear();
	}

	public void apply(List<SyncEntry> entries) {
		for (SyncEntry e : entries) {
			if (e.stack != null) stacks.put(e.id, new StoredItemStack(e.stack, 1));
			if (e.count <= 0) {
				TerminalItemStack old = lines.remove(e.id);
				if (old != null) {
					old.setCount(0);
					old.setUsedSlotCount(0);
				}
				continue;
			}
			TerminalItemStack line = lines.get(e.id);
			if (line == null) {
				StoredItemStack st = stacks.get(e.id);
				if (st == null) continue; // never told what this id is: ignore, a resync fixes it
				line = new TerminalItemStack(st.getStack(), e.count);
				line.setId(e.id);
				lines.put(e.id, line);
			}
			line.setCount(e.count);
			line.setUsedSlotCount(e.slots);
		}
	}

	/** A fresh list of the current lines (the line objects themselves are shared and keep updating). */
	public List<TerminalItemStack> snapshot() {
		return new ArrayList<TerminalItemStack>(lines.values());
	}

	/** Current amount of an id (0 if gone). */
	public long getAmount(int id) {
		TerminalItemStack l = lines.get(id);
		return l == null ? 0 : l.getQuantity();
	}
}
