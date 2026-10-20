package com.tom.storagemod.gui;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import com.tom.storagemod.net.PacketTerminalAction.Action;
import com.tom.storagemod.tile.TileEntityStorageTerminal;

public class GuiCraftingTerminal extends GuiStorageTerminalBase {
	private static final ResourceLocation GUI = new ResourceLocation("toms_storage", "textures/gui/crafting_terminal.png");

	private boolean pullFromInv;

	public GuiCraftingTerminal(ContainerCraftingTerminal container) {
		super(container, 256);
	}

	@Override
	protected ResourceLocation getGuiTexture() {
		return GUI;
	}

	@Override
	protected String getTitleKey() {
		return "ts.crafting_terminal";
	}

	@Override
	protected void addExtraButtons() {
		// the small "X" between the storage and the crafting grid: move the grid contents back into storage
		buttons.add(new TerminalButton(guiLeft + 80, guiTop + 20 + rowCount * 18) {
			{
				w = 11;
				h = 11;
			}

			@Override
			int icon() {
				return 0;
			}

			@Override
			void onPress() {
				send(Action.CLEAR_GRID, 0);
			}

			@Override
			void draw(int mx, int my) {
				bindIcons();
				// icon sheet row 4: normal, hovered, disabled
				drawTexturedModalRect(x, y, isHovered(mx, my) ? 16 : 0, 64, w, h);
				unbindIcons();
			}
		});

		buttons.add(new TerminalButton(guiLeft - 18, guiTop + 5 + 18 * 6) {
			@Override
			int icon() {
				return pullFromInv ? ICON_REFILL_ON : ICON_REFILL_OFF;
			}

			@Override
			void onPress() {
				pullFromInv = !pullFromInv;
				sendSettings();
			}

			@Override
			List<String> getTooltip() {
				return Collections.singletonList(I18n.format("tooltip.toms_storage.pull_" + (pullFromInv ? 1 : 0)));
			}
		});
	}

	@Override
	protected int extraModes() {
		return pullFromInv ? TileEntityStorageTerminal.MODE_PULL_FROM_PLAYER : 0;
	}

	@Override
	protected void onSettingsApplied(int modes) {
		pullFromInv = (modes & TileEntityStorageTerminal.MODE_PULL_FROM_PLAYER) != 0;
	}
}
