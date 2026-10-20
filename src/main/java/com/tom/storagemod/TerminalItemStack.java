package com.tom.storagemod;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * One line of a terminal's item list: an item type, how many there are, in how many slots,
 * plus lazily cached strings that sorting and searching would otherwise recompute constantly.
 */
public class TerminalItemStack extends StoredItemStack {
	private int usedSlots = 1;
	/** Id the server assigned to this item for this terminal session (0 = none). */
	private int id;

	private String displayName, unlocalizedName, modId;
	private int tabOrder = Integer.MIN_VALUE, itemOrder = Integer.MIN_VALUE;

	public TerminalItemStack(ItemStack stack, long count) {
		super(stack, count);
	}

	/** Copies the stack; quantity from its stack size. */
	public TerminalItemStack(ItemStack stack) {
		super(stack);
	}

	public int getUsedSlotCount() {
		return usedSlots;
	}

	public void setUsedSlotCount(int n) {
		usedSlots = n;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public boolean equalDetails(TerminalItemStack o) {
		return o != null && o.count == count && o.usedSlots == usedSlots;
	}

	public String getDisplayName() {
		if (displayName == null) displayName = stack.getDisplayName();
		return displayName;
	}

	public String getUnlocalizedName() {
		if (unlocalizedName == null) unlocalizedName = stack.getUnlocalizedName();
		return unlocalizedName;
	}

	public String getModId() {
		if (modId == null) {
			GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(stack.getItem());
			modId = uid == null ? "minecraft" : uid.modId;
		}
		return modId;
	}

	/** Position of the item's creative tab (items without a tab go last). */
	public int getTabOrder() {
		if (tabOrder == Integer.MIN_VALUE) {
			Item item = stack.getItem();
			tabOrder = item.getCreativeTab() == null ? Integer.MAX_VALUE : item.getCreativeTab().getTabIndex();
		}
		return tabOrder;
	}

	/** Registration order of the item, which is the order creative tabs list them in. */
	public int getItemOrder() {
		if (itemOrder == Integer.MIN_VALUE) itemOrder = Item.getIdFromItem(stack.getItem());
		return itemOrder;
	}
}
