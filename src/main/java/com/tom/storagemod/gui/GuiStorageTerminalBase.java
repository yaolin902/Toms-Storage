package com.tom.storagemod.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.tom.storagemod.StorageMod;
import com.tom.storagemod.StoredItemStack;
import com.tom.storagemod.TerminalItemStack;
import com.tom.storagemod.net.PacketTerminalAction;
import com.tom.storagemod.net.PacketTerminalAction.Action;
import com.tom.storagemod.sorting.SortingTypes;
import com.tom.storagemod.tile.TileEntityStorageTerminal;
import com.tom.storagemod.util.ExpiringCache;
import com.tom.storagemod.util.NumberUtil;
import com.tom.storagemod.util.TerminalSearch;

/**
 * Shared screen for both terminals: item grid, search, scroll bar and the settings buttons.
 *
 * Settings (stored on the server, layout as in 2.4.2, see TileEntityStorageTerminal):
 * sorting = type | reverse 0x100 | ghost items OFF 0x200, modes = control mode | tall 0x10 | pull from inventory 0x20,
 * searchType = bit 0 auto focus, bit 1 keep search.
 */
public abstract class GuiStorageTerminalBase extends GuiContainer {
	protected static final ResourceLocation TABS_TEXTURE = new ResourceLocation("textures/gui/container/creative_inventory/tabs.png");
	protected static final ResourceLocation ICONS = new ResourceLocation("toms_storage", "textures/gui/terminal_icons.png");
	private static final RenderItem itemRender = new RenderItem();

	// Icon sheet tiles (see terminal_icons.png): 8 icons per row, each 32 px wide = normal + hovered
	protected static final int ICON_SORT = 0; // + SortingTypes ordinal
	protected static final int ICON_DIR_DESC = 6, ICON_DIR_ASC = 7, ICON_SEARCH = 8;
	protected static final int ICON_CTRL = 9; // + control mode
	protected static final int ICON_GHOST_OFF = 12, ICON_GHOST_ON = 13, ICON_TALL_OFF = 14, ICON_TALL_ON = 15;
	protected static final int ICON_REFILL_OFF = 16, ICON_REFILL_ON = 17;

	/** Layout of the GUI textures: slots the texture shows, where the first one starts. */
	protected static final int TEXTURE_ROWS = 5, SLOT_START_X = 7, SLOT_START_Y = 17;
	protected static final int COLS = 9;
	protected static final int GRID_X = SLOT_START_X + 1, GRID_Y = SLOT_START_Y + 1;
	protected static final int SCROLL_X = 174, SCROLL_WIDTH = 14;
	protected static final int SEARCH_X = 82, SEARCH_Y = 6, SEARCH_WIDTH = 89;

	// Search data is slow to compute (tooltips especially) and rarely changes: cache it for a few seconds.
	private static final ExpiringCache<StoredItemStack, List<String>> TOOLTIPS = new ExpiringCache<StoredItemStack, List<String>>(5000, 4000);
	private static final ExpiringCache<StoredItemStack, List<String>> ORE_NAMES = new ExpiringCache<StoredItemStack, List<String>>(5000, 4000);
	private static final ExpiringCache<StoredItemStack, String> NBT_TEXT = new ExpiringCache<StoredItemStack, String>(5000, 4000);

	private final TerminalSearch.ItemInfo searchInfo = new TerminalSearch.ItemInfo() {
		@Override
		public String getName(TerminalItemStack s) {
			return s.getDisplayName();
		}

		@Override
		@SuppressWarnings("unchecked")
		public List<String> getTooltipLines(TerminalItemStack s) {
			long now = System.currentTimeMillis();
			List<String> lines = TOOLTIPS.get(s, now);
			if (lines == null) {
				lines = new ArrayList<String>();
				List<String> raw = s.getStack().getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
				for (String l : raw) {
					String plain = EnumChatFormatting.getTextWithoutFormattingCodes(l);
					if (plain != null) lines.add(plain.toLowerCase());
				}
				TOOLTIPS.put(s, lines, now);
			}
			return lines;
		}

		@Override
		public Collection<String> getOreNames(TerminalItemStack s) {
			long now = System.currentTimeMillis();
			List<String> names = ORE_NAMES.get(s, now);
			if (names == null) {
				names = new ArrayList<String>();
				for (int id : OreDictionary.getOreIDs(s.getStack())) names.add(OreDictionary.getOreName(id).toLowerCase());
				ORE_NAMES.put(s, names, now);
			}
			return names;
		}

		@Override
		public String getNbt(TerminalItemStack s) {
			if (s.getStack().stackTagCompound == null) return "";
			long now = System.currentTimeMillis();
			String text = NBT_TEXT.get(s, now);
			if (text == null) {
				text = s.getStack().stackTagCompound.toString();
				NBT_TEXT.put(s, text, now);
			}
			return text;
		}

		@Override
		public String getModId(TerminalItemStack s) {
			return s.getModId();
		}
	};

	protected final ContainerStorageTerminal terminal;
	protected GuiTextField searchField;
	/** Height of the GUI texture (the non-tall size). */
	protected final int guiHeight;
	protected int rowCount = TEXTURE_ROWS;

	protected int controlMode, searchType;
	protected boolean reverseSort, ghostItems = true, tallMode;
	protected SortingTypes sortingType = SortingTypes.AMOUNT;
	protected final List<TerminalButton> buttons = new ArrayList<TerminalButton>();

	private String searchLast = "";
	private boolean refreshItemList;
	private boolean loadedSearch;
	private boolean syncRequested;
	private boolean relayout;
	/** Ghost items: while Shift is held the order is frozen, so repeated shift-clicks don't move things around. */
	private boolean frozen;
	private int lastPacket;
	private List<TerminalItemStack> sourceList;
	private final List<TerminalItemStack> sorted = new ArrayList<TerminalItemStack>();

	private float currentScroll;
	private boolean isScrolling;

	protected GuiStorageTerminalBase(ContainerStorageTerminal container, int guiHeight) {
		super(container);
		this.terminal = container;
		this.guiHeight = guiHeight;
		this.xSize = 194;
		this.ySize = guiHeight;
	}

	protected abstract ResourceLocation getGuiTexture();

	/** Lang key of the title drawn in the top left corner. */
	protected abstract String getTitleKey();

	// ------------------------------------------------------------------
	// Setup
	// ------------------------------------------------------------------

	@Override
	public void initGui() {
		// tall mode: as many rows as fit on the screen
		int guiSize = guiHeight - TEXTURE_ROWS * 18;
		rowCount = tallMode ? Math.max(3, (height - 30 - guiSize) / 18) : TEXTURE_ROWS;
		ySize = guiSize + rowCount * 18;
		terminal.setSlotYOffset((rowCount - TEXTURE_ROWS) * 18);

		super.initGui();
		Keyboard.enableRepeatEvents(true);

		String old = searchField == null ? "" : searchField.getText();
		searchField = new GuiTextField(fontRendererObj, guiLeft + SEARCH_X, guiTop + SEARCH_Y, SEARCH_WIDTH, fontRendererObj.FONT_HEIGHT);
		searchField.setMaxStringLength(100);
		searchField.setEnableBackgroundDrawing(false);
		searchField.setVisible(true);
		searchField.setTextColor(0xFFFFFF);
		searchField.setText(old);

		buttons.clear();
		int bx = guiLeft - 18, by = guiTop + 5;
		buttons.add(new TerminalButton(bx, by) {
			@Override
			int icon() {
				return ICON_SORT + sortingType.ordinal();
			}

			@Override
			void onPress() {
				sortingType = cycle(SortingTypes.VALUES, sortingType.ordinal());
				sendSettings();
				refreshItemList = true;
			}

			@Override
			List<String> getTooltip() {
				return lines(I18n.format("tooltip.toms_storage.sorting_" + sortingType.name().toLowerCase()));
			}
		});
		buttons.add(new TerminalButton(bx, by + 18) {
			@Override
			int icon() {
				return reverseSort ? ICON_DIR_ASC : ICON_DIR_DESC;
			}

			@Override
			void onPress() {
				reverseSort = !reverseSort;
				sendSettings();
				refreshItemList = true;
			}

			@Override
			List<String> getTooltip() {
				return lines(I18n.format("tooltip.toms_storage.sort_dir_" + (reverseSort ? 1 : 0)));
			}
		});
		buttons.add(new TerminalButton(bx, by + 36) {
			@Override
			int icon() {
				return ICON_SEARCH;
			}

			@Override
			void draw(int mx, int my) {
				super.draw(mx, my);
				bindIcons();
				// overlays for the active search options: auto focus, keep search
				if ((searchType & 1) > 0) drawTexturedModalRect(x + 1, y + 1, 0, 48, 14, 14);
				if ((searchType & 2) > 0) drawTexturedModalRect(x + 1, y + 1, 16, 48, 14, 14);
				unbindIcons();
			}

			@Override
			void onPress() {
				searchType = cycleInt(4, searchType & 3);
				applySearchMode();
				sendSettings();
			}

			@Override
			List<String> getTooltip() {
				return lines(I18n.format("tooltip.toms_storage.search_" + (searchType & 3)));
			}
		});
		buttons.add(new TerminalButton(bx, by + 54) {
			@Override
			int icon() {
				return ICON_CTRL + controlMode;
			}

			@Override
			void onPress() {
				controlMode = cycleInt(ControllMode.VALUES.length, controlMode);
				sendSettings();
			}

			@Override
			List<String> getTooltip() {
				return lines(I18n.format("tooltip.toms_storage.ctrlMode_" + controlMode));
			}
		});
		buttons.add(new TerminalButton(bx, by + 72) {
			@Override
			int icon() {
				return ghostItems ? ICON_GHOST_ON : ICON_GHOST_OFF;
			}

			@Override
			void onPress() {
				ghostItems = !ghostItems;
				sendSettings();
			}

			@Override
			List<String> getTooltip() {
				return lines(I18n.format("tooltip.toms_storage.ghostMode_" + (ghostItems ? "on" : "off")));
			}
		});
		buttons.add(new TerminalButton(bx, by + 90) {
			@Override
			int icon() {
				return tallMode ? ICON_TALL_ON : ICON_TALL_OFF;
			}

			@Override
			void onPress() {
				tallMode = !tallMode;
				sendSettings();
				relayout = true;
			}

			@Override
			List<String> getTooltip() {
				return lines(I18n.format("tooltip.toms_storage.tallMode_" + (tallMode ? "on" : "off")));
			}
		});
		addExtraButtons();

		applySearchMode();
		sourceList = null; // force a rebuild
		if (!syncRequested) {
			// The server's first list can arrive before this GUI exists; ask again now that it does.
			syncRequested = true;
			send(Action.REQUEST_SYNC, 0);
		}
	}

	/** Subclass hook: more buttons. */
	protected void addExtraButtons() {
	}

	/** Subclass hook: mode bits above bit 4 that the subclass owns. */
	protected int extraModes() {
		return 0;
	}

	/** Subclass hook: settings arrived from the server. */
	protected void onSettingsApplied(int modes) {
	}

	@Override
	public void onGuiClosed() {
		super.onGuiClosed();
		Keyboard.enableRepeatEvents(false);
	}

	private void applySearchMode() {
		searchField.setCanLoseFocus((searchType & 1) == 0);
		if (!searchField.isFocused() && (searchType & 1) > 0) searchField.setFocused(true);
	}

	/** Cycles forward, or backward while Shift is held. */
	private static <T> T cycle(T[] values, int current) {
		return values[cycleInt(values.length, current)];
	}

	private static int cycleInt(int size, int current) {
		return (current + size + (GuiScreen.isShiftKeyDown() ? -1 : 1)) % size;
	}

	/** Settings from the server -> local state. Runs on the main thread. */
	private void applyTerminalData() {
		int s = terminal.sorting, m = terminal.modes;
		sortingType = SortingTypes.byOrdinal(s & 0xFF);
		reverseSort = (s & TileEntityStorageTerminal.SORT_REVERSED) != 0;
		ghostItems = (s & TileEntityStorageTerminal.SORT_GHOST_OFF) == 0;
		controlMode = (m & 0xF) % ControllMode.VALUES.length;
		boolean tall = (m & TileEntityStorageTerminal.MODE_TALL) != 0;
		if (tall != tallMode) {
			tallMode = tall;
			relayout = true;
		}
		searchType = terminal.searchType;
		applySearchMode();
		onSettingsApplied(m);

		if (!loadedSearch && terminal.search != null) {
			loadedSearch = true;
			if ((searchType & 2) > 0) searchField.setText(terminal.search);
		}
		refreshItemList = true;
	}

	private int buildSorting() {
		return sortingType.ordinal() | (reverseSort ? TileEntityStorageTerminal.SORT_REVERSED : 0)
				| (ghostItems ? 0 : TileEntityStorageTerminal.SORT_GHOST_OFF);
	}

	private int buildModes() {
		return (controlMode & 0xF) | (tallMode ? TileEntityStorageTerminal.MODE_TALL : 0) | extraModes();
	}

	protected void sendSettings() {
		StorageMod.network.sendToServer(PacketTerminalAction.settings(buildSorting(), buildModes(), searchType & 3));
	}

	protected void send(Action action, int itemId) {
		StorageMod.network.sendToServer(new PacketTerminalAction(action, itemId));
	}

	// ------------------------------------------------------------------
	// Search / sorting
	// ------------------------------------------------------------------

	@Override
	public void updateScreen() {
		super.updateScreen();
		searchField.updateCursorCounter();
		if (relayout) {
			relayout = false;
			initGui();
		}
		// Ghost items: hold Shift to freeze the order (lines that run out stay as a 0)
		boolean wantFrozen = ghostItems && terminal.itemsLoaded && GuiScreen.isShiftKeyDown();
		if (wantFrozen) {
			frozen = true;
		} else if (frozen) {
			frozen = false;
			refreshItemList = true;
		}
		if (lastPacket != terminal.packetCounter) {
			lastPacket = terminal.packetCounter;
			applyTerminalData();
		}
		if (sourceList != terminal.itemList) refreshItemList = true;
		updateSearch();
	}

	private void updateSearch() {
		String text = searchField.getText();
		boolean textChanged = !searchLast.equals(text);
		if (!refreshItemList && !textChanged) return;
		if (frozen && !textChanged) {
			refreshItemList = false; // keep the frozen order; the lines update their counts by themselves
			return;
		}
		refreshItemList = false;
		sourceList = terminal.itemList;

		TerminalSearch.Matcher matcher = TerminalSearch.compile(text, searchInfo);
		sorted.clear();
		for (TerminalItemStack is : sourceList) {
			if (is == null || is.getStack() == null) continue;
			try {
				if (matcher.test(is)) sorted.add(is);
			} catch (Throwable t) {
				// a broken tooltip from some other mod must not crash the screen
			}
		}
		Comparator<TerminalItemStack> comp = sortingType.create(reverseSort);
		Collections.sort(sorted, comp);

		if (textChanged) {
			currentScroll = 0;
			searchLast = text;
			if ((searchType & 2) > 0) {
				StorageMod.network.sendToServer(PacketTerminalAction.search(text));
			}
		}
		currentScroll = MathHelper.clamp_float(currentScroll, 0.0F, 1.0F);
	}

	// ------------------------------------------------------------------
	// Scrolling helpers
	// ------------------------------------------------------------------

	private int totalRows() {
		return (sorted.size() + COLS - 1) / COLS;
	}

	private boolean needsScrollBar() {
		return totalRows() > rowCount;
	}

	private int getRowOffset() {
		int extra = totalRows() - rowCount;
		if (extra <= 0) return 0;
		return MathHelper.clamp_int((int) (currentScroll * extra + 0.5F), 0, extra);
	}

	@Override
	public void handleMouseInput() {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0 && needsScrollBar()) {
			int extra = totalRows() - rowCount;
			currentScroll = MathHelper.clamp_float(currentScroll - Math.signum(wheel) / extra, 0.0F, 1.0F);
		}
	}

	// ------------------------------------------------------------------
	// Grid geometry
	// ------------------------------------------------------------------

	/** Index into the visible grid (0 .. rowCount*COLS-1) under the mouse, or -1. */
	protected int getGridSlotAt(int mouseX, int mouseY) {
		int rx = mouseX - guiLeft - GRID_X;
		int ry = mouseY - guiTop - GRID_Y;
		if (rx < 0 || ry < 0 || rx >= COLS * 18 || ry >= rowCount * 18) return -1;
		return (ry / 18) * COLS + (rx / 18);
	}

	private TerminalItemStack getStackAtGridSlot(int gridSlot) {
		int index = getRowOffset() * COLS + gridSlot;
		return index >= 0 && index < sorted.size() ? sorted.get(index) : null;
	}

	// ------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------

	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		mc.getTextureManager().bindTexture(getGuiTexture());
		if (!tallMode) {
			drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
			return;
		}
		// Tall mode: the texture's header and footer, with its slot rows repeated in between.
		int guiStart = TEXTURE_ROWS * 18 + SLOT_START_Y;
		int footerStart = rowCount * 18 + SLOT_START_Y;
		int footerSize = guiHeight - TEXTURE_ROWS * 18 - SLOT_START_Y;
		int rowWidth = SLOT_START_X + COLS * 18 + 25;
		drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, SLOT_START_Y);
		drawTexturedModalRect(guiLeft, guiTop + footerStart, 0, guiStart, xSize, footerSize);
		drawTexturedModalRect(guiLeft, guiTop + SLOT_START_Y, 0, SLOT_START_Y, rowWidth, 18);
		for (int i = 1; i < rowCount - 1; i++) {
			drawTexturedModalRect(guiLeft, guiTop + SLOT_START_Y + i * 18, 0, SLOT_START_Y + 18, rowWidth, 18);
		}
		drawTexturedModalRect(guiLeft, guiTop + SLOT_START_Y + (rowCount - 1) * 18, 0, SLOT_START_Y + (TEXTURE_ROWS - 1) * 18, rowWidth, 18);
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		fontRendererObj.drawString(I18n.format(getTitleKey()), 8, 6, 0x404040);
		fontRendererObj.drawString(I18n.format("container.inventory"), 8, ySize - 92, 0x404040);
		fontRendererObj.drawString("i", 180, 6, 0x404040); // hover for storage statistics

		// scroll bar handle
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		mc.getTextureManager().bindTexture(TABS_TEXTURE);
		int travel = rowCount * 18 - 17;
		drawTexturedModalRect(SCROLL_X, GRID_Y + (int) (travel * currentScroll), 232 + (needsScrollBar() ? 0 : 12), 0, 12, 15);

		int hovered = getGridSlotAt(mouseX, mouseY);
		int rowOffset = getRowOffset();

		RenderHelper.enableGUIStandardItemLighting();
		GL11.glEnable(GL12.GL_RESCALE_NORMAL);
		for (int r = 0; r < rowCount; r++) {
			for (int c = 0; c < COLS; c++) {
				int index = (r + rowOffset) * COLS + c;
				if (index >= sorted.size()) break;
				TerminalItemStack s = sorted.get(index);
				int x = GRID_X + c * 18;
				int y = GRID_Y + r * 18;

				if (hovered == r * COLS + c) {
					GL11.glDisable(GL11.GL_LIGHTING);
					GL11.glDisable(GL11.GL_DEPTH_TEST);
					drawGradientRect(x, y, x + 16, y + 16, 0x80FFFFFF, 0x80FFFFFF);
					GL11.glEnable(GL11.GL_LIGHTING);
					GL11.glEnable(GL11.GL_DEPTH_TEST);
				}

				GL11.glEnable(GL11.GL_DEPTH_TEST);
				itemRender.zLevel = 100.0F;
				itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), s.getStack(), x, y);
				itemRender.renderItemOverlayIntoGUI(fontRendererObj, mc.getTextureManager(), s.getStack(), x, y, "");
				itemRender.zLevel = 0.0F;

				drawStackSize(fontRendererObj, s.getQuantity(), x, y);
			}
		}
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}

	/** Small quantity label in the bottom-right corner of a grid cell (yellow 0 = ghost item). */
	private void drawStackSize(FontRenderer fr, long size, int x, int y) {
		float scale = 0.6F;
		String text = NumberUtil.formatNumber(size);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glPushMatrix();
		GL11.glScalef(scale, scale, scale);
		int tx = (int) ((x + 16.0F - fr.getStringWidth(text) * scale) / scale);
		int ty = (int) ((y + 16.0F - 7.0F * scale) / scale);
		fr.drawStringWithShadow(text, tx, ty, size == 0 ? 0xFFFF00 : 0xFFFFFF);
		GL11.glPopMatrix();
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		// drag the scroll handle
		if (isScrolling && !Mouse.isButtonDown(0)) isScrolling = false;
		if (isScrolling) {
			int top = guiTop + GRID_Y;
			int bottom = top + rowCount * 18;
			currentScroll = MathHelper.clamp_float((mouseY - top - 7.5F) / (bottom - top - 15.0F), 0.0F, 1.0F);
		}

		super.drawScreen(mouseX, mouseY, partialTicks);

		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		searchField.drawTextBox();
		for (TerminalButton b : buttons) b.draw(mouseX, mouseY);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_DEPTH_TEST);

		for (TerminalButton b : buttons) {
			if (b.isHovered(mouseX, mouseY)) {
				List<String> tip = b.getTooltip();
				if (tip != null) drawHoveringText(tip, mouseX, mouseY, fontRendererObj);
			}
		}

		if (mouseX >= guiLeft + 176 && mouseX < guiLeft + 192 && mouseY >= guiTop + 4 && mouseY < guiTop + 14) {
			drawHoveringText(statisticsTooltip(), mouseX, mouseY, fontRendererObj);
		}

		int hovered = getGridSlotAt(mouseX, mouseY);
		if (hovered >= 0) {
			TerminalItemStack s = getStackAtGridSlot(hovered);
			if (s != null) drawStoredTooltip(s, mouseX, mouseY);
		}
	}

	private List<String> statisticsTooltip() {
		boolean aLot = terminal.slotCount >= Short.MAX_VALUE;
		boolean freeALot = terminal.freeCount >= Short.MAX_VALUE;
		String all = aLot ? I18n.format("tooltip.toms_storage.terminal_stat.alot") : String.valueOf(terminal.slotCount);
		String free = freeALot ? I18n.format("tooltip.toms_storage.terminal_stat.alot") : String.valueOf(terminal.freeCount);
		String percent = aLot || freeALot || terminal.slotCount <= 0 ? "?" : String.valueOf(Math.round(terminal.freeCount / (float) terminal.slotCount * 100));
		List<String> l = new ArrayList<String>();
		l.add(I18n.format("tooltip.toms_storage.terminal_stat.slots", all));
		l.add(I18n.format("tooltip.toms_storage.terminal_stat.free", free, percent));
		l.add(I18n.format("tooltip.toms_storage.terminal_stat.unique", terminal.itemList.size()));
		return l;
	}

	@SuppressWarnings("unchecked")
	private void drawStoredTooltip(TerminalItemStack s, int mouseX, int mouseY) {
		ItemStack stack = s.getStack();
		List<String> list = stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
		for (int i = 0; i < list.size(); i++) {
			if (i == 0) list.set(i, stack.getRarity().rarityColor + list.get(i));
			else list.set(i, EnumChatFormatting.GRAY + list.get(i));
		}
		if (s.getQuantity() >= 1000 || sortingType == SortingTypes.SPACE_EFFICIENCY) {
			list.add(I18n.format("tooltip.toms_storage.amount", s.getQuantity()));
			list.add(I18n.format("tooltip.toms_storage.slots_used", s.getUsedSlotCount()));
		}
		FontRenderer fr = stack.getItem().getFontRenderer(stack);
		drawHoveringText(list, mouseX, mouseY, fr == null ? fontRendererObj : fr);
	}

	// ------------------------------------------------------------------
	// Input
	// ------------------------------------------------------------------

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		if (button == 0) {
			for (TerminalButton b : buttons) {
				if (b.isHovered(mouseX, mouseY)) {
					b.onPress();
					return;
				}
			}
		}
		int gridSlot = getGridSlotAt(mouseX, mouseY);
		if (gridSlot >= 0) {
			handleGridClick(getStackAtGridSlot(gridSlot), button);
			return;
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_SPACE)) {
			send(Action.SPACE_CLICK, 0);
			return;
		}
		if (button == 0 && mouseX >= guiLeft + SCROLL_X && mouseX < guiLeft + SCROLL_X + SCROLL_WIDTH
				&& mouseY >= guiTop + GRID_Y && mouseY < guiTop + GRID_Y + rowCount * 18) {
			isScrolling = needsScrollBar();
			return;
		}
		boolean inSearch = mouseX >= guiLeft + SEARCH_X && mouseX < guiLeft + SEARCH_X + SEARCH_WIDTH
				&& mouseY >= guiTop + SEARCH_Y - 2 && mouseY < guiTop + SEARCH_Y + fontRendererObj.FONT_HEIGHT + 2;
		if (button == 1 && inSearch) {
			searchField.setText("");
			return;
		}
		searchField.mouseClicked(mouseX, mouseY, button);
		super.mouseClicked(mouseX, mouseY, button);
	}

	private void handleGridClick(TerminalItemStack s, int button) {
		ItemStack held = mc.thePlayer.inventory.getItemStack();
		boolean hasItem = s != null && s.getQuantity() > 0; // ghost lines (0) can't be taken
		int id = s == null ? 0 : s.getId();

		if (isPullOne(button)) {
			if (hasItem) send(isTransferOne(button) ? Action.TRANSFER_ONE : Action.PULL_ONE, id);
		} else if (pullHalf(button)) {
			Action act = GuiScreen.isCtrlKeyDown() ? Action.PULL_QUARTER : Action.PULL_HALF;
			if (held != null) send(act, 0);
			else if (hasItem) send(act, id);
		} else if (pullNormal(button)) {
			if (held != null) send(Action.PULL_OR_PUSH, 0);
			else if (hasItem) send(GuiScreen.isShiftKeyDown() ? Action.SHIFT_PULL : Action.PULL_OR_PUSH, id);
		}
	}

	private boolean heldIsEmpty() {
		return mc.thePlayer.inventory.getItemStack() == null;
	}

	private boolean isPullOne(int button) {
		switch (ControllMode.VALUES[controlMode]) {
		case AE:
			return button == 1 && GuiScreen.isShiftKeyDown();
		case RS:
			return button == 2;
		case DEF:
			return button == 1 && !heldIsEmpty();
		default:
			return false;
		}
	}

	private boolean isTransferOne(int button) {
		switch (ControllMode.VALUES[controlMode]) {
		case AE:
			return GuiScreen.isShiftKeyDown() && GuiScreen.isCtrlKeyDown();
		case RS:
			return GuiScreen.isShiftKeyDown() && button == 2;
		case DEF:
			return button == 1 && GuiScreen.isShiftKeyDown();
		default:
			return false;
		}
	}

	private boolean pullHalf(int button) {
		switch (ControllMode.VALUES[controlMode]) {
		case AE:
		case RS:
			return button == 1;
		case DEF:
			return button == 1 && heldIsEmpty();
		default:
			return false;
		}
	}

	private boolean pullNormal(int button) {
		return button == 0;
	}

	@Override
	protected void keyTyped(char typed, int keyCode) {
		// While typing into the search box, letters (incl. the inventory key) must not close the GUI.
		if (searchField.textboxKeyTyped(typed, keyCode)) return;
		super.keyTyped(typed, keyCode);
	}

	// ------------------------------------------------------------------
	// Buttons
	// ------------------------------------------------------------------

	static List<String> lines(String text) {
		List<String> l = new ArrayList<String>();
		for (String s : text.split("\\|")) l.add(s);
		return l;
	}

	protected void bindIcons() {
		mc.getTextureManager().bindTexture(ICONS);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
	}

	protected void unbindIcons() {
		GL11.glDisable(GL11.GL_BLEND);
	}

	/** A 16x16 button whose picture comes from the icon sheet (tile {@link #icon()}, hovered variant next to it). */
	protected abstract class TerminalButton {
		public int x, y;
		public int w = 16, h = 16;

		protected TerminalButton(int x, int y) {
			this.x = x;
			this.y = y;
		}

		abstract int icon();

		abstract void onPress();

		boolean isHovered(int mx, int my) {
			return mx >= x && my >= y && mx < x + w && my < y + h;
		}

		void draw(int mx, int my) {
			int i = icon();
			bindIcons();
			drawTexturedModalRect(x, y, (i % 8) * 32 + (isHovered(mx, my) ? 16 : 0), (i / 8) * 16, w, h);
			unbindIcons();
		}

		/** Hover text (null for none). */
		List<String> getTooltip() {
			return null;
		}
	}
}
