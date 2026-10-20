package com.tom.storagemod.sorting;

import java.util.Collections;
import java.util.Comparator;

import com.tom.storagemod.TerminalItemStack;

/** Terminal sort orders. The ordinal is what gets saved in the terminal settings. */
public enum SortingTypes {
	AMOUNT {
		@Override
		public int compare(TerminalItemStack a, TerminalItemStack b) {
			int c = Long.compare(a.getQuantity(), b.getQuantity());
			return c != 0 ? -c : a.getDisplayName().compareTo(b.getDisplayName());
		}
	},
	NAME {
		@Override
		public int compare(TerminalItemStack a, TerminalItemStack b) {
			return a.getDisplayName().compareTo(b.getDisplayName());
		}
	},
	MOD {
		@Override
		public int compare(TerminalItemStack a, TerminalItemStack b) {
			int c = a.getModId().compareTo(b.getModId());
			return c != 0 ? c : a.getDisplayName().compareTo(b.getDisplayName());
		}
	},
	/** Items that take up the most slots first, so the worst space wasters are on top. */
	SPACE_EFFICIENCY {
		@Override
		public int compare(TerminalItemStack a, TerminalItemStack b) {
			int c = Integer.compare(a.getUsedSlotCount(), b.getUsedSlotCount());
			if (c != 0) return -c;
			c = Long.compare(a.getQuantity(), b.getQuantity());
			return c != 0 ? -c : a.getDisplayName().compareTo(b.getDisplayName());
		}
	},
	ID {
		@Override
		public int compare(TerminalItemStack a, TerminalItemStack b) {
			int c = a.getUnlocalizedName().compareTo(b.getUnlocalizedName());
			return c != 0 ? c : Integer.compare(a.getStack().getItemDamage(), b.getStack().getItemDamage());
		}
	},
	/** Like the creative inventory: by tab, then by item. */
	TYPE {
		@Override
		public int compare(TerminalItemStack a, TerminalItemStack b) {
			int c = Integer.compare(a.getTabOrder(), b.getTabOrder());
			if (c != 0) return c;
			c = Integer.compare(a.getItemOrder(), b.getItemOrder());
			if (c != 0) return c;
			c = Integer.compare(a.getStack().getItemDamage(), b.getStack().getItemDamage());
			return c != 0 ? c : a.getDisplayName().compareTo(b.getDisplayName());
		}
	};

	public static final SortingTypes[] VALUES = values();

	public abstract int compare(TerminalItemStack a, TerminalItemStack b);

	public Comparator<TerminalItemStack> create(boolean reversed) {
		final SortingTypes self = this;
		Comparator<TerminalItemStack> c = new Comparator<TerminalItemStack>() {
			@Override
			public int compare(TerminalItemStack a, TerminalItemStack b) {
				return self.compare(a, b);
			}
		};
		return reversed ? Collections.reverseOrder(c) : c;
	}

	/** Safe lookup for values read from disk/network. */
	public static SortingTypes byOrdinal(int i) {
		return i >= 0 && i < VALUES.length ? VALUES[i] : AMOUNT;
	}
}
