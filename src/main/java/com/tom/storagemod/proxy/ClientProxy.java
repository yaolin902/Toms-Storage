package com.tom.storagemod.proxy;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

import com.tom.storagemod.StorageMod;
import com.tom.storagemod.gui.ContainerStorageTerminal;
import com.tom.storagemod.net.PacketTerminalSync;

public class ClientProxy implements IProxy {
	@Override
	public void preInit() {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@Override
	public void handleSync(PacketTerminalSync msg) {
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		if (player != null && player.openContainer instanceof ContainerStorageTerminal) {
			((ContainerStorageTerminal) player.openContainer).receiveSync(msg);
		}
	}

	/** "Hold SHIFT for more info" block descriptions, like the 1.16 version. */
	@SubscribeEvent
	@SuppressWarnings("unchecked")
	public void onTooltip(ItemTooltipEvent event) {
		ItemStack stack = event.itemStack;
		if (stack == null) return;
		Block block = Block.getBlockFromItem(stack.getItem());
		String key;
		if (block == StorageMod.connector) key = "inventory_connector";
		else if (block == StorageMod.terminal) key = "storage_terminal";
		else if (block == StorageMod.craftingTerminal) key = "crafting_terminal";
		else return;

		if (GuiScreen.isShiftKeyDown()) {
			for (String line : I18n.format("tooltip.toms_storage." + key).split("\\|")) {
				event.toolTip.add(line);
			}
		} else {
			event.toolTip.add(EnumChatFormatting.ITALIC + "" + EnumChatFormatting.GRAY + I18n.format("tooltip.toms_storage.hold_shift_for_info"));
		}
	}
}
