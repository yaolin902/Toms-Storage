package com.tom.storagemod;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.ByteBufUtils;

/**
 * An item type (stack size always 1 internally) plus a long quantity, so a
 * terminal can show more than Integer.MAX_VALUE of something.
 * hashCode/equals ignore the quantity so this can be used as a map key.
 */
public class StoredItemStack {
	protected final ItemStack stack;
	protected long count;
	private int hash;
	private boolean hashed;

	public StoredItemStack(ItemStack stack, long count) {
		this.stack = stack;
		this.count = count;
	}

	/** Copies the stack; the quantity is taken from its stack size. */
	public StoredItemStack(ItemStack stack) {
		this.stack = stack.copy();
		this.stack.stackSize = 1;
		this.count = stack.stackSize;
	}

	public ItemStack getStack() {
		return stack;
	}

	public long getQuantity() {
		return count;
	}

	public void grow(long c) {
		count += c;
	}

	public void setCount(long c) {
		count = c;
	}

	public int getMaxStackSize() {
		return stack.getMaxStackSize();
	}

	public boolean equalItem(StoredItemStack other) {
		return other != null && matches(stack, other.stack);
	}

	public ItemStack getActualStack() {
		ItemStack s = stack.copy();
		s.stackSize = (int) Math.min(count, Integer.MAX_VALUE);
		return s;
	}

	public void write(ByteBuf buf) {
		ByteBufUtils.writeItemStack(buf, stack);
		buf.writeLong(count);
	}

	public static StoredItemStack read(ByteBuf buf) {
		ItemStack s = ByteBufUtils.readItemStack(buf);
		long c = buf.readLong();
		return s == null ? null : new StoredItemStack(s, c);
	}

	/** Same item, damage value and NBT. */
	public static boolean matches(ItemStack a, ItemStack b) {
		if (a == null || b == null) return false;
		return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage() && ItemStack.areItemStackTagsEqual(a, b);
	}

	/** Cached: hashing the NBT of a big stack is expensive and a key is hashed many times. */
	@Override
	public int hashCode() {
		if (!hashed) {
			int result = 1;
			result = 31 * result + stack.getItem().hashCode();
			result = 31 * result + stack.getItemDamage();
			result = 31 * result + (stack.stackTagCompound == null ? 0 : stack.stackTagCompound.hashCode());
			hash = result;
			hashed = true;
		}
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		// not getClass(): a TerminalItemStack must equal the StoredItemStack of the same item (same hashCode too)
		if (!(obj instanceof StoredItemStack)) return false;
		return matches(stack, ((StoredItemStack) obj).stack);
	}
}
