package com.tom.storagemod.util;

import net.minecraftforge.common.util.ForgeDirection;

/**
 * Terminal block metadata = direction from the terminal towards the block it is mounted on.
 * Remapped so that metadata 0 is NORTH: that is what the inventory/hand item render uses.
 *
 *   meta: 0 NORTH, 1 SOUTH, 2 WEST, 3 EAST, 4 DOWN, 5 UP
 */
public final class TerminalOrientation {
	private TerminalOrientation() {
	}

	public static ForgeDirection getMountDirection(int meta) {
		if (meta < 0 || meta > 5) return ForgeDirection.UNKNOWN;
		return ForgeDirection.getOrientation((meta + 2) % 6);
	}

	public static int toMeta(ForgeDirection dir) {
		return (dir.ordinal() + 4) % 6;
	}
}
