package com.tom.storagemod.tile;

import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.tom.storagemod.InventoryAccess;
import com.tom.storagemod.InventoryNetwork;

/**
 * Terminal logic shared by both terminals. Keeps the player's sort/search
 * settings (they survive closing the GUI and restarting the world) and talks
 * to whatever inventory/connector the terminal is mounted on.
 * Does no ticking: the network is looked up on demand.
 */
public class TileEntityStorageTerminal extends TileEntity {
	/** Bits of {@link #getSorting()}. Low byte = sorting type (SortingTypes ordinal). */
	public static final int SORT_REVERSED = 0x100;
	/** Set when ghost items are switched OFF (so a fresh terminal has them on). */
	public static final int SORT_GHOST_OFF = 0x200;
	/** Bits of {@link #getModes()}. Low nibble = control mode. */
	public static final int MODE_TALL = 0x10;
	public static final int MODE_PULL_FROM_PLAYER = 0x20;

	private int sorting, modes, searchType;
	private String lastSearch = "";

	/** Network of a terminal that sits directly on a chest/furnace; rebuilt once a second. */
	private InventoryNetwork directNetwork;
	private long directNetworkTick = Long.MIN_VALUE;

	@Override
	public boolean canUpdate() {
		return false;
	}

	/**
	 * The inventories this terminal can see. Server side only. The object is long lived, so it keeps
	 * its slot snapshots and change counter between calls. Never null on the server (may be empty).
	 */
	public InventoryNetwork getNetwork() {
		if (worldObj == null || worldObj.isRemote) return null;
		TileEntityInventoryConnector con = InventoryNetwork.findConnector(worldObj, xCoord, yCoord, zCoord);
		if (con != null) return con.getNetwork();

		long now = worldObj.getTotalWorldTime();
		if (directNetwork == null || now - directNetworkTick >= 20 || now < directNetworkTick) {
			directNetworkTick = now;
			List<InventoryAccess> list = InventoryNetwork.collectDirect(worldObj, xCoord, yCoord, zCoord);
			if (directNetwork == null) directNetwork = new InventoryNetwork(list);
			else directNetwork.setInventories(list);
		}
		return directNetwork;
	}

	public long getCount(ItemStack key) {
		InventoryNetwork net = getNetwork();
		return net == null || key == null ? 0 : net.getCount(key);
	}

	/** Takes up to max items matching key out of storage. Returns null if none. */
	public ItemStack pullStack(ItemStack key, int max) {
		InventoryNetwork net = getNetwork();
		if (net == null || key == null || max < 1) return null;
		return net.extract(key, max);
	}

	/** Puts a stack into storage. Returns what did not fit, or null if everything went in. */
	public ItemStack pushStack(ItemStack stack) {
		if (stack == null || stack.stackSize < 1) return null;
		InventoryNetwork net = getNetwork();
		if (net == null) return stack;
		return net.insert(stack);
	}

	public void pushOrDrop(ItemStack stack) {
		ItemStack rest = pushStack(stack);
		if (rest != null) dropItem(worldObj, xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, rest);
	}

	public static void dropItem(World world, double x, double y, double z, ItemStack stack) {
		if (world == null || world.isRemote || stack == null || stack.stackSize < 1) return;
		world.spawnEntityInWorld(new EntityItem(world, x, y, z, stack.copy()));
	}

	/**
	 * Reach as in 2.4.2: 6 blocks to open the terminal, 25% more to keep it open
	 * (so standing at the edge doesn't make it flicker shut). Same dimension only.
	 */
	public boolean canInteractWith(EntityPlayer player, boolean menuCheck) {
		if (worldObj == null || worldObj.getTileEntity(xCoord, yCoord, zCoord) != this) return false;
		if (player.worldObj != worldObj) return false;
		double d = 6.0D;
		if (menuCheck) d *= 1.25D;
		return player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= d * d;
	}

	public int getSorting() {
		return sorting;
	}

	public int getModes() {
		return modes;
	}

	public int getSearchType() {
		return searchType;
	}

	public void setSorting(int v) {
		if (sorting != v) {
			sorting = v;
			markDirty();
		}
	}

	public void setModes(int v) {
		if (modes != v) {
			modes = v;
			markDirty();
		}
	}

	public void setSearchType(int v) {
		if (searchType != v) {
			searchType = v;
			markDirty();
		}
	}

	public String getLastSearch() {
		return lastSearch;
	}

	public void setLastSearch(String s) {
		lastSearch = s == null ? "" : s;
	}

	@Override
	public void writeToNBT(NBTTagCompound tag) {
		super.writeToNBT(tag);
		tag.setInteger("sorting", sorting);
		tag.setInteger("modes", modes);
		tag.setInteger("searchType", searchType);
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
		if (tag.hasKey("sort")) {
			// older save format: one int holding everything
			int s = tag.getInteger("sort");
			int control = s & 0b11;
			boolean reversed = (s & 0b100) != 0;
			int type = (s >> 3) & 0b11;
			searchType = (s >> 5) & 0b111;
			boolean pullFromInv = (s & (1 << 8)) != 0;
			modes = control | (pullFromInv ? MODE_PULL_FROM_PLAYER : 0);
			sorting = type | (reversed ? SORT_REVERSED : 0);
		} else {
			sorting = tag.getInteger("sorting");
			modes = tag.getInteger("modes");
			searchType = tag.getInteger("searchType");
		}
	}
}
