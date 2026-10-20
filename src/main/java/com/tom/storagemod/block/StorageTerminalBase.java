package com.tom.storagemod.block;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import com.tom.storagemod.StorageMod;
import com.tom.storagemod.tile.TileEntityStorageTerminal;
import com.tom.storagemod.util.TerminalOrientation;

/**
 * A thin panel mounted on any side (including top and bottom) of an inventory or connector.
 * Metadata: see {@link TerminalOrientation}.
 */
public abstract class StorageTerminalBase extends BlockContainer {
	private static final float THICKNESS = 6.0F / 16.0F;

	@SideOnly(Side.CLIENT)
	private IIcon iconFront, iconSide;

	protected StorageTerminalBase(String name) {
		super(Material.wood);
		setBlockName(name);
		setHardness(3.0F);
		setResistance(5.0F);
		setStepSound(soundTypeWood);
		setHarvestLevel("axe", 0);
		setLightLevel(6.0F / 15.0F);
		setCreativeTab(StorageMod.tab);
	}

	/** Texture name (without mod id) of the screen face. */
	protected abstract String getFrontTextureName();

	/** IGuiHandler id of this terminal's GUI. */
	public abstract int getGuiId();

	// ---- placement ----

	@Override
	public int onBlockPlaced(World world, int x, int y, int z, int side, float hx, float hy, float hz, int meta) {
		// Clicked face 'side' => the block we are mounted on is in the opposite direction.
		return TerminalOrientation.toMeta(ForgeDirection.getOrientation(side).getOpposite());
	}

	// ---- interaction ----

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy, float hz) {
		if (!world.isRemote) {
			TileEntity te = world.getTileEntity(x, y, z);
			if (te instanceof TileEntityStorageTerminal) {
				if (((TileEntityStorageTerminal) te).canInteractWith(player, false)) {
					player.openGui(StorageMod.instance, getGuiId(), world, x, y, z);
				} else {
					player.addChatMessage(new ChatComponentTranslation("chat.toms_storage.terminal_out_of_range"));
				}
			}
		}
		return true;
	}

	// ---- shape ----

	@Override
	public boolean isOpaqueCube() {
		return false;
	}

	@Override
	public boolean renderAsNormalBlock() {
		return false;
	}

	private void applyBounds(int meta) {
		switch (TerminalOrientation.getMountDirection(meta)) {
		case SOUTH:
			setBlockBounds(0, 0, 1 - THICKNESS, 1, 1, 1);
			break;
		case WEST:
			setBlockBounds(0, 0, 0, THICKNESS, 1, 1);
			break;
		case EAST:
			setBlockBounds(1 - THICKNESS, 0, 0, 1, 1, 1);
			break;
		case DOWN:
			setBlockBounds(0, 0, 0, 1, THICKNESS, 1);
			break;
		case UP:
			setBlockBounds(0, 1 - THICKNESS, 0, 1, 1, 1);
			break;
		default: // NORTH and the item form
			setBlockBounds(0, 0, 0, 1, 1, THICKNESS);
			break;
		}
	}

	@Override
	public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
		applyBounds(world.getBlockMetadata(x, y, z));
	}

	@Override
	public void setBlockBoundsForItemRender() {
		applyBounds(0);
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
		setBlockBoundsBasedOnState(world, x, y, z);
		return super.getCollisionBoundingBoxFromPool(world, x, y, z);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
		setBlockBoundsBasedOnState(world, x, y, z);
		return super.getSelectedBoundingBoxFromPool(world, x, y, z);
	}

	// ---- textures ----

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister reg) {
		blockIcon = reg.registerIcon("toms_storage:inventory_block"); // the back, against the inventory
		iconFront = reg.registerIcon("toms_storage:" + getFrontTextureName());
		iconSide = reg.registerIcon("toms_storage:terminal_side");
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int meta) {
		ForgeDirection mount = TerminalOrientation.getMountDirection(meta);
		if (mount == ForgeDirection.UNKNOWN) mount = ForgeDirection.NORTH;
		if (side == mount.ordinal()) return blockIcon;
		if (side == mount.getOpposite().ordinal()) return iconFront;
		return iconSide;
	}
}
