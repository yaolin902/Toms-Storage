package com.tom.storagemod.block;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.tom.storagemod.StorageMod;
import com.tom.storagemod.tile.TileEntityInventoryConnector;

public class InventoryConnector extends BlockContainer {

	public InventoryConnector() {
		super(Material.wood);
		setBlockName("ts.inventory_connector");
		setBlockTextureName("toms_storage:inventory_connector");
		setHardness(3.0F);
		setResistance(5.0F);
		setStepSound(soundTypeWood);
		setHarvestLevel("axe", 0);
		setCreativeTab(StorageMod.tab);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityInventoryConnector();
	}

	/** Right click: report how many slots are free in the connected network. */
	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy, float hz) {
		if (!world.isRemote) {
			TileEntity tile = world.getTileEntity(x, y, z);
			if (tile instanceof TileEntityInventoryConnector) {
				TileEntityInventoryConnector te = (TileEntityInventoryConnector) tile;
				player.addChatMessage(new ChatComponentTranslation("chat.toms_storage.inventory_connector.free_slots",
						te.getFreeSlotCount(), te.getInvSize(), te.getInventoryCount()));
			}
		}
		return true;
	}
}
