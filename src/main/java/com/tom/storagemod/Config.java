package com.tom.storagemod;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.block.Block;

import net.minecraftforge.common.config.Configuration;

/** Server-side options (config/toms_storage.cfg). */
public class Config {
	/** Inventory Connector range, stored squared for cheap distance checks. */
	public static int invRangeSq = 16 * 16;
	/** Blocks whose touching copies are treated as one multiblock inventory. */
	public static Set<Block> multiblockInvs = new HashSet<Block>();

	/** Blocks (and whole mods) the connector must ignore, even if they have an inventory. */
	public static Set<Block> blockedBlocks = new HashSet<Block>();
	public static Set<String> blockedMods = new HashSet<String>();

	private static String[] multiblockNames = new String[0];
	private static String[] blockedBlockNames = new String[0];

	public static void load(File file) {
		Configuration cfg = new Configuration(file);
		int range = cfg.getInt("inventoryConnectorRange", Configuration.CATEGORY_GENERAL, 16, 4, 256, "Inventory Connector Range");
		multiblockNames = cfg.getStringList("multiblockInv", Configuration.CATEGORY_GENERAL, new String[0],
				"Multiblock inventories: block ids (e.g. modid:blockname). Touching blocks of these types are scanned as one inventory.");
		blockedBlockNames = cfg.getStringList("blockedBlocks", Configuration.CATEGORY_GENERAL, new String[0],
				"List of block ids ignored by the inventory connector (e.g. modid:blockname)");
		String[] mods = cfg.getStringList("blockedMods", Configuration.CATEGORY_GENERAL, new String[0],
				"List of mod ids whose blocks are ignored by the inventory connector");
		Set<String> modSet = new HashSet<String>();
		for (String m : mods) {
			if (m != null && !m.trim().isEmpty()) modSet.add(m.trim().toLowerCase());
		}
		blockedMods = modSet;
		if (cfg.hasChanged()) cfg.save();
		invRangeSq = range * range;
	}

	/** Must run after all mods registered their blocks (postInit). */
	public static void resolveBlocks() {
		Set<Block> set = new HashSet<Block>();
		for (String name : multiblockNames) {
			if (name == null) continue;
			Block b = Block.getBlockFromName(name.trim());
			if (b != null && b != net.minecraft.init.Blocks.air) set.add(b);
		}
		multiblockInvs = set;

		Set<Block> blocked = new HashSet<Block>();
		for (String name : blockedBlockNames) {
			if (name == null) continue;
			Block b = Block.getBlockFromName(name.trim());
			if (b != null && b != net.minecraft.init.Blocks.air) blocked.add(b);
		}
		blockedBlocks = blocked;
	}

	/** False for blocks the player blacklisted in the config. */
	public static boolean canConnect(Block block, String modId) {
		if (blockedBlocks.contains(block)) return false;
		return blockedMods.isEmpty() || modId == null || !blockedMods.contains(modId.toLowerCase());
	}
}
