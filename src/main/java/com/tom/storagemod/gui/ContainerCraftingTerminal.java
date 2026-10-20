package com.tom.storagemod.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.tom.storagemod.net.PacketTerminalAction;
import com.tom.storagemod.tile.TileEntityCraftingTerminal;

/**
 * Slot layout: 0 = result, 1-9 = crafting grid, then the player's inventory.
 * On the client the grid is a plain dummy inventory that the server's slot
 * updates fill in; on the server it is the tile entity's grid.
 */
public class ContainerCraftingTerminal extends ContainerStorageTerminal {

	/** Taking the result runs the terminal's crafting (storage refill etc.), not vanilla's. */
	public class SlotTerminalCrafting extends SlotCrafting {
		public SlotTerminalCrafting(EntityPlayer player, IInventory matrix, IInventory result, int index, int x, int y) {
			super(player, matrix, result, index, x, y);
		}

		@Override
		public boolean canTakeStack(EntityPlayer player) {
			return !resultBlocked;
		}

		@Override
		public void onPickupFromSlot(EntityPlayer player, ItemStack stack) {
			if (player.worldObj.isRemote || craftingTe == null) return;
			craftingTe.craft(player);
			requestSlotResync(); // the grid was refilled on the server only
			stack.onCrafting(player.worldObj, player, stack.stackSize);
			cpw.mods.fml.common.FMLCommonHandler.instance().firePlayerCraftingEvent(player, stack, craftingTe.getCraftingInv());
		}
	}

	private final TileEntityCraftingTerminal craftingTe;
	/** True while a double-click "pick up all" runs, so it can't pull items out of the crafting result. */
	private boolean resultBlocked;

	public ContainerCraftingTerminal(InventoryPlayer inv, World world, int x, int y, int z) {
		super(inv, world, x, y, z, false);
		craftingTe = te instanceof TileEntityCraftingTerminal ? (TileEntityCraftingTerminal) te : null;

		IInventory matrix;
		IInventory result;
		if (craftingTe != null) {
			matrix = craftingTe.getCraftingInv();
			result = craftingTe.getCraftResult();
			craftingTe.onCraftingMatrixChanged(); // make sure the result matches the saved grid
		} else {
			// client: slots only mirror what the server sends
			matrix = new InventoryCrafting(new Container0(), 3, 3);
			result = new InventoryCraftResult();
		}

		int gx = -4, gy = 94;
		addSlotToContainer(new SlotTerminalCrafting(inv.player, matrix, result, 0, gx + 124, gy + 35));
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				addSlotToContainer(new Slot(matrix, j + i * 3, gx + 30 + j * 18, gy + 17 + i * 18));
			}
		}
		addPlayerSlots(8, 174);
	}

	/** Do-nothing container so a client-side InventoryCrafting has something to notify. */
	private static class Container0 extends net.minecraft.inventory.Container {
		@Override
		public boolean canInteractWith(EntityPlayer player) {
			return false;
		}
	}

	/** Vanilla's crafting table excludes the result slot from double-click gathering; so do we. */
	@Override
	public ItemStack slotClick(int slotId, int clickedButton, int mode, EntityPlayer player) {
		if (mode == 6) {
			resultBlocked = true;
			try {
				return super.slotClick(slotId, clickedButton, mode, player);
			} finally {
				resultBlocked = false;
			}
		}
		return super.slotClick(slotId, clickedButton, mode, player);
	}

	private ItemStack lastResult;
	private boolean resultSent;

	/**
	 * Vanilla never sends crafting-result slots to the client (it expects the client to work the
	 * recipe out itself, like the vanilla crafting table). Our client grid is only a mirror, so the
	 * server has to send the result whenever it changes.
	 */
	@Override
	public void detectAndSendChanges() {
		super.detectAndSendChanges();
		if (world.isRemote || craftingTe == null) return;
		ItemStack current = ((Slot) inventorySlots.get(0)).getStack();
		if (!resultSent || !ItemStack.areItemStacksEqual(current, lastResult)) {
			resultSent = true;
			lastResult = current == null ? null : current.copy();
			sendSlotToPlayer(0);
		}
	}

	@Override
	protected ItemStack shiftClickItems(EntityPlayer player, int index) {
		if (craftingTe == null) return null;
		if (index == 0) {
			craftingTe.craftShift(player);
		} else if (index > 0 && index < 10) {
			Slot slot = (Slot) inventorySlots.get(index);
			if (slot != null && slot.getHasStack()) {
				slot.putStack(craftingTe.pushStack(slot.getStack()));
			}
		}
		return null;
	}

	@Override
	protected void handleExtraAction(PacketTerminalAction a) {
		if (a.action == PacketTerminalAction.Action.CLEAR_GRID && craftingTe != null) {
			craftingTe.clear(playerInv.player);
		}
	}
}
