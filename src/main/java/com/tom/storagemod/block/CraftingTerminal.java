package com.tom.storagemod.block;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.tom.storagemod.tile.TileEntityCraftingTerminal;

public class CraftingTerminal extends StorageTerminalBase {
	public static final int GUI_ID = 1;

	public CraftingTerminal() {
		super("ts.crafting_terminal");
	}

	@Override
	protected String getFrontTextureName() {
		return "crafting_terminal_front";
	}

	@Override
	public int getGuiId() {
		return GUI_ID;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityCraftingTerminal();
	}

	/** The crafting grid keeps its items between uses, so give them back when the block is broken. */
	@Override
	public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
		if (!world.isRemote) {
			TileEntity te = world.getTileEntity(x, y, z);
			if (te instanceof TileEntityCraftingTerminal) {
				((TileEntityCraftingTerminal) te).dropGridContents();
			}
		}
		super.breakBlock(world, x, y, z, block, meta);
	}
}
