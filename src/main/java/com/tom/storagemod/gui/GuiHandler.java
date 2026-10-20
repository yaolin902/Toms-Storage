package com.tom.storagemod.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.IGuiHandler;

import com.tom.storagemod.block.CraftingTerminal;
import com.tom.storagemod.block.StorageTerminal;

public class GuiHandler implements IGuiHandler {

	private ContainerStorageTerminal container(int id, EntityPlayer player, World world, int x, int y, int z) {
		if (id == StorageTerminal.GUI_ID) return new ContainerStorageTerminal(player.inventory, world, x, y, z);
		if (id == CraftingTerminal.GUI_ID) return new ContainerCraftingTerminal(player.inventory, world, x, y, z);
		return null;
	}

	@Override
	public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
		return container(id, player, world, x, y, z);
	}

	@Override
	public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
		ContainerStorageTerminal c = container(id, player, world, x, y, z);
		if (c instanceof ContainerCraftingTerminal) return new GuiCraftingTerminal((ContainerCraftingTerminal) c);
		if (c != null) return new GuiStorageTerminal(c);
		return null;
	}
}
