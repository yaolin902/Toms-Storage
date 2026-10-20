package com.tom.storagemod.gui;

import net.minecraft.util.ResourceLocation;

public class GuiStorageTerminal extends GuiStorageTerminalBase {
	private static final ResourceLocation GUI = new ResourceLocation("toms_storage", "textures/gui/storage_terminal.png");

	public GuiStorageTerminal(ContainerStorageTerminal container) {
		super(container, 202);
	}

	@Override
	protected ResourceLocation getGuiTexture() {
		return GUI;
	}

	@Override
	protected String getTitleKey() {
		return "ts.storage_terminal";
	}
}
