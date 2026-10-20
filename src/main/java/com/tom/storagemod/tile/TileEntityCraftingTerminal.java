package com.tom.storagemod.tile;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import cpw.mods.fml.common.FMLCommonHandler;

import com.tom.storagemod.StoredItemStack;

/**
 * Storage terminal with a built-in 3x3 crafting grid. The grid contents are
 * saved with the block. Crafting refills used-up grid slots from storage, and
 * (optionally) from the player's inventory.
 */
public class TileEntityCraftingTerminal extends TileEntityStorageTerminal {

	/** InventoryCrafting needs a Container to notify; this one just recomputes the recipe. */
	private final Container notifier = new Container() {
		@Override
		public boolean canInteractWith(EntityPlayer player) {
			return false;
		}

		@Override
		public void onCraftMatrixChanged(IInventory inventory) {
			if (worldObj != null && !worldObj.isRemote && !suspendUpdates) {
				onCraftingMatrixChanged();
			}
		}
	};
	private final InventoryCrafting craftMatrix = new InventoryCrafting(notifier, 3, 3);
	private final InventoryCraftResult craftResult = new InventoryCraftResult();
	private boolean reading;
	private boolean suspendUpdates;
	/** The recipe that matched last time. Checked first, because searching all recipes is slow in big packs. */
	private IRecipe currentRecipe;
	private long craftedTick = Long.MIN_VALUE;
	private int craftedThisTick;

	public InventoryCrafting getCraftingInv() {
		return craftMatrix;
	}

	public IInventory getCraftResult() {
		return craftResult;
	}

	// ---- saving ----

	@Override
	public void writeToNBT(NBTTagCompound tag) {
		super.writeToNBT(tag);
		NBTTagList list = new NBTTagList();
		for (int i = 0; i < craftMatrix.getSizeInventory(); i++) {
			ItemStack stack = craftMatrix.getStackInSlot(i);
			if (stack != null) {
				NBTTagCompound t = new NBTTagCompound();
				t.setByte("Slot", (byte) i);
				stack.writeToNBT(t);
				list.appendTag(t);
			}
		}
		tag.setTag("CraftingTable", list);
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
		reading = true;
		NBTTagList list = tag.getTagList("CraftingTable", 10);
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound t = list.getCompoundTagAt(i);
			int slot = t.getByte("Slot") & 255;
			if (slot < craftMatrix.getSizeInventory()) {
				craftMatrix.setInventorySlotContents(slot, ItemStack.loadItemStackFromNBT(t));
			}
		}
		reading = false;
	}

	// ---- recipe ----

	/** Re-evaluates the recipe for the current grid (also used after loading from disk). */
	public void onCraftingMatrixChanged() {
		ItemStack result = null;
		if (worldObj != null) result = findResult();
		craftResult.setInventorySlotContents(0, result);
		if (!reading) markDirty();
	}

	@SuppressWarnings("rawtypes")
	private ItemStack findResult() {
		int filled = 0;
		for (int i = 0; i < craftMatrix.getSizeInventory(); i++) {
			if (craftMatrix.getStackInSlot(i) != null) filled++;
		}
		if (filled == 0) {
			currentRecipe = null;
			return null;
		}
		// Two stacks can also be a tool repair, which vanilla checks before any recipe: leave that to vanilla.
		if (filled == 2) {
			currentRecipe = null;
			return CraftingManager.getInstance().findMatchingRecipe(craftMatrix, worldObj);
		}
		if (currentRecipe != null && currentRecipe.matches(craftMatrix, worldObj)) {
			return currentRecipe.getCraftingResult(craftMatrix);
		}
		// same search vanilla does, but remembering which recipe matched
		List recipes = CraftingManager.getInstance().getRecipeList();
		for (Object o : recipes) {
			IRecipe r = (IRecipe) o;
			if (r.matches(craftMatrix, worldObj)) {
				currentRecipe = r;
				return r.getCraftingResult(craftMatrix);
			}
		}
		currentRecipe = null;
		return null;
	}

	// ---- crafting ----

	/** False if crafting again this tick would exceed one stack (stops packet spam from crafting unlimited stacks per tick). */
	public boolean canCraft() {
		ItemStack r = craftResult.getStackInSlot(0);
		if (r == null || worldObj == null) return false;
		long now = worldObj.getTotalWorldTime();
		if (now != craftedTick) {
			craftedTick = now;
			craftedThisTick = 0;
		}
		return craftedThisTick + r.stackSize <= r.getMaxStackSize();
	}

	/** Shift-click on the result: craft repeatedly (up to one full stack) and hand the items out. */
	public void craftShift(EntityPlayer player) {
		if (!canCraft()) return;
		ItemStack first = craftResult.getStackInSlot(0);
		ItemStack crafted = first.copy();
		List<ItemStack> made = new ArrayList<ItemStack>();
		int amount = 0;
		do {
			craft(player);
			made.add(crafted.copy());
			amount += crafted.stackSize;
			ItemStack next = craftResult.getStackInSlot(0);
			if (next == null || !StoredItemStack.matches(crafted, next)) break;
		} while (amount + crafted.stackSize <= crafted.getMaxStackSize());
		craftedThisTick += amount;

		for (ItemStack item : made) {
			player.inventory.addItemStackToInventory(item); // leaves the remainder in 'item'
			if (item.stackSize > 0) {
				ItemStack rest = pushStack(item);
				if (rest != null) dropItem(player.worldObj, player.posX, player.posY, player.posZ, rest);
			}
		}
		player.inventory.markDirty();

		ItemStack total = crafted.copy();
		total.stackSize = amount;
		crafted.onCrafting(player.worldObj, player, amount);
		FMLCommonHandler.instance().firePlayerCraftingEvent(player, total, craftMatrix);
	}

	/**
	 * Consumes one set of ingredients. A slot that runs out is refilled from storage (or the player,
	 * if enabled) BEFORE its container item (bucket etc.) is dealt with, so recipes can keep going:
	 * the empty bucket goes to storage while the next full one takes its place.
	 */
	public void craft(EntityPlayer player) {
		suspendUpdates = true;
		try {
			boolean pullFromPlayer = (getModes() & MODE_PULL_FROM_PLAYER) != 0;
			for (int i = 0; i < craftMatrix.getSizeInventory(); i++) {
				ItemStack slot = craftMatrix.getStackInSlot(i);
				if (slot == null) continue;

				ItemStack old = slot.copy();
				ItemStack remainder = null;
				if (slot.getItem().hasContainerItem(slot)) {
					remainder = slot.getItem().getContainerItem(slot);
					if (remainder != null && remainder.isItemStackDamageable() && remainder.getItemDamage() > remainder.getMaxDamage()) {
						remainder = null; // the container item broke
					}
				}

				craftMatrix.decrStackSize(i, 1);
				slot = craftMatrix.getStackInSlot(i);
				if (slot == null) {
					ItemStack refill = pullStack(old, 1);
					if (refill == null && pullFromPlayer) refill = takeFromPlayer(player, old);
					if (refill != null) {
						craftMatrix.setInventorySlotContents(i, refill);
						slot = refill;
					}
				}

				if (remainder != null) {
					if (slot == null) craftMatrix.setInventorySlotContents(i, remainder);
					else storeOrGive(player, remainder);
				}
			}
		} finally {
			suspendUpdates = false;
		}
		player.inventory.markDirty();
		onCraftingMatrixChanged();
	}

	/** Storage first, then the player's inventory, then the floor. */
	private void storeOrGive(EntityPlayer player, ItemStack stack) {
		ItemStack rest = pushStack(stack);
		if (rest != null) {
			player.inventory.addItemStackToInventory(rest);
			if (rest.stackSize > 0) dropItem(player.worldObj, player.posX, player.posY, player.posZ, rest);
		}
	}

	private static ItemStack takeFromPlayer(EntityPlayer player, ItemStack like) {
		ItemStack[] main = player.inventory.mainInventory;
		for (int j = 0; j < main.length; j++) {
			if (main[j] != null && StoredItemStack.matches(main[j], like)) {
				return player.inventory.decrStackSize(j, 1);
			}
		}
		return null;
	}

	// ---- grid management ----

	/** Moves the grid contents into storage; what doesn't fit goes to the player, then the floor. */
	public void clear(EntityPlayer player) {
		suspendUpdates = true;
		try {
			for (int i = 0; i < craftMatrix.getSizeInventory(); i++) {
				ItemStack st = craftMatrix.getStackInSlotOnClosing(i);
				if (st != null) storeOrGive(player, st);
			}
		} finally {
			suspendUpdates = false;
		}
		onCraftingMatrixChanged();
	}

	/** Called when the block is broken. */
	public void dropGridContents() {
		for (int i = 0; i < craftMatrix.getSizeInventory(); i++) {
			ItemStack st = craftMatrix.getStackInSlotOnClosing(i);
			if (st != null) dropItem(worldObj, xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, st);
		}
	}
}
