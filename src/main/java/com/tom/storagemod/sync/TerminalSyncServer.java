package com.tom.storagemod.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tom.storagemod.StoredItemStack;
import com.tom.storagemod.TerminalItemStack;

/**
 * Server half of the terminal item sync. Remembers what the client has been told and only produces
 * entries for what changed. Each item type gets a small id, and the item itself (with its NBT) is only
 * sent once; later updates and the client's clicks just use the id.
 */
public class TerminalSyncServer {
	private final Map<StoredItemStack, Integer> ids = new HashMap<StoredItemStack, Integer>();
	private final Map<Integer, StoredItemStack> byId = new HashMap<Integer, StoredItemStack>();
	/** Ids whose item the client already received. */
	private final Set<Integer> clientKnows = new HashSet<Integer>();
	/** What the client currently shows (contents maps are rebuilt on change, never mutated, so keeping a reference is safe). */
	private Map<StoredItemStack, TerminalItemStack> sent = new HashMap<StoredItemStack, TerminalItemStack>();
	private int nextId = 1;

	/** Forget what the client knows: the next diff re-sends everything (with the items). */
	public void reset() {
		sent = new HashMap<StoredItemStack, TerminalItemStack>();
		clientKnows.clear();
	}

	/** The item for an id the client sent us in a click, or null if unknown. */
	public StoredItemStack lookup(int id) {
		return byId.get(id);
	}

	/** Entries for everything that differs from what the client was last told. */
	public List<SyncEntry> diff(Map<StoredItemStack, TerminalItemStack> now) {
		List<SyncEntry> out = new ArrayList<SyncEntry>();
		for (Map.Entry<StoredItemStack, TerminalItemStack> e : now.entrySet()) {
			TerminalItemStack cur = e.getValue();
			TerminalItemStack prev = sent.get(e.getKey());
			if (prev == null || !prev.equalDetails(cur)) {
				out.add(entryFor(e.getKey(), cur.getQuantity(), cur.getUsedSlotCount()));
			}
		}
		for (StoredItemStack gone : sent.keySet()) {
			if (!now.containsKey(gone)) out.add(entryFor(gone, 0, 0));
		}
		sent = now;
		return out;
	}

	private SyncEntry entryFor(StoredItemStack key, long count, int slots) {
		Integer id = ids.get(key);
		if (id == null) {
			id = nextId++;
			ids.put(key, id);
			byId.put(id, key);
		}
		SyncEntry e = new SyncEntry(id, count, slots, null);
		if (count > 0 && clientKnows.add(id)) e.stack = key.getStack();
		return e;
	}
}
