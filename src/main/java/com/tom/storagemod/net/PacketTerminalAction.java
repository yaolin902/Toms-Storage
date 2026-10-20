package com.tom.storagemod.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

import com.tom.storagemod.gui.ContainerStorageTerminal;

/** Client -> server: the player clicked something in a terminal GUI. */
public class PacketTerminalAction implements IMessage {

	public enum Action {
		/** Left click: insert the held stack, or pick up a full stack. */
		PULL_OR_PUSH,
		/** Right click: insert half of the held stack, or pick up half a stack. */
		PULL_HALF,
		/** Ctrl + right click: like PULL_HALF, but a quarter. */
		PULL_QUARTER,
		/** Pick up one item (adds to a matching held stack). */
		PULL_ONE,
		/** Shift + left click: move a full stack straight into the player inventory. */
		SHIFT_PULL,
		/** Move one item straight into the player inventory. */
		TRANSFER_ONE,
		/** Space + click on empty area: dump the main player inventory into storage. */
		SPACE_CLICK,
		/** Sent when the GUI opens: the first list may have been sent before the client was ready. */
		REQUEST_SYNC,
		/** 'data' = sorting, 'data2' = modes, 'data3' = search type. */
		SET_SETTINGS,
		/** 'text' = search text to remember on the terminal. */
		SET_SEARCH,
		/** Crafting terminal: move the crafting grid contents into storage. */
		CLEAR_GRID
	}

	public Action action = Action.PULL_OR_PUSH;
	/** Which item line was clicked: the id the server gave it in the sync (0 = none). */
	public int itemId;
	public int data, data2, data3;
	public String text = "";

	public PacketTerminalAction() {
	}

	public PacketTerminalAction(Action action, int itemId) {
		this.action = action;
		this.itemId = itemId;
	}

	public static PacketTerminalAction settings(int sorting, int modes, int searchType) {
		PacketTerminalAction p = new PacketTerminalAction(Action.SET_SETTINGS, 0);
		p.data = sorting;
		p.data2 = modes;
		p.data3 = searchType;
		return p;
	}

	public static PacketTerminalAction search(String text) {
		PacketTerminalAction p = new PacketTerminalAction(Action.SET_SEARCH, 0);
		p.text = text;
		return p;
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeByte(action.ordinal());
		buf.writeInt(itemId);
		buf.writeInt(data);
		buf.writeInt(data2);
		buf.writeInt(data3);
		ByteBufUtils.writeUTF8String(buf, text);
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		int o = buf.readUnsignedByte();
		Action[] all = Action.values();
		action = o < all.length ? all[o] : Action.REQUEST_SYNC;
		itemId = buf.readInt();
		data = buf.readInt();
		data2 = buf.readInt();
		data3 = buf.readInt();
		text = ByteBufUtils.readUTF8String(buf);
		if (text.length() > 256) text = text.substring(0, 256);
	}

	public static class Handler implements IMessageHandler<PacketTerminalAction, IMessage> {
		@Override
		public IMessage onMessage(PacketTerminalAction msg, MessageContext ctx) {
			EntityPlayerMP player = ctx.getServerHandler().playerEntity;
			// Packets arrive on the network thread: just queue, the container runs it on the server tick.
			if (player.openContainer instanceof ContainerStorageTerminal) {
				((ContainerStorageTerminal) player.openContainer).queueAction(msg);
			}
			return null;
		}
	}
}
