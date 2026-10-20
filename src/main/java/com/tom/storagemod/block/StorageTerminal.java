package com.tom.storagemod.block;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.tom.storagemod.tile.TileEntityStorageTerminal;

public class StorageTerminal extends StorageTerminalBase {
	public static final int GUI_ID = 0;

	public StorageTerminal() {
		super("ts.storage_terminal");
	}

	@Override
	protected String getFrontTextureName() {
		return "terminal_front";
	}

	@Override
	public int getGuiId() {
		return GUI_ID;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityStorageTerminal();
	}
}
