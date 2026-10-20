package com.tom.storagemod.net;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

import com.tom.storagemod.StorageMod;
import com.tom.storagemod.sync.SyncEntry;

/**
 * Server -> client: changed lines of the terminal's item list plus the terminal's saved settings,
 * last search text and slot statistics.
 * 1.7.10 custom payloads must stay under 32 KB, so big updates are split into several packets:
 * 'reset' (first packet only) clears the client's list first, 'last' marks the end of the update.
 */
public class PacketTerminalSync implements IMessage {
	public boolean reset, last;
	public int sorting, modes, searchType;
	public String search = "";
	public int slotCount, freeCount;
	public List<SyncEntry> entries = new ArrayList<SyncEntry>();

	public PacketTerminalSync() {
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeBoolean(reset);
		buf.writeBoolean(last);
		buf.writeInt(sorting);
		buf.writeInt(modes);
		buf.writeInt(searchType);
		ByteBufUtils.writeUTF8String(buf, search);
		buf.writeInt(slotCount);
		buf.writeInt(freeCount);
		buf.writeShort(entries.size());
		for (SyncEntry e : entries) writeEntry(buf, e);
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		reset = buf.readBoolean();
		last = buf.readBoolean();
		sorting = buf.readInt();
		modes = buf.readInt();
		searchType = buf.readInt();
		search = ByteBufUtils.readUTF8String(buf);
		slotCount = buf.readInt();
		freeCount = buf.readInt();
		int n = buf.readUnsignedShort();
		entries = new ArrayList<SyncEntry>(n);
		for (int i = 0; i < n; i++) entries.add(readEntry(buf));
	}

	public static void writeEntry(ByteBuf buf, SyncEntry e) {
		buf.writeInt(e.id);
		buf.writeLong(e.count);
		buf.writeInt(e.slots);
		buf.writeBoolean(e.stack != null);
		if (e.stack != null) ByteBufUtils.writeItemStack(buf, e.stack);
	}

	public static SyncEntry readEntry(ByteBuf buf) {
		SyncEntry e = new SyncEntry();
		e.id = buf.readInt();
		e.count = buf.readLong();
		e.slots = buf.readInt();
		if (buf.readBoolean()) {
			e.stack = ByteBufUtils.readItemStack(buf);
			// an item whose mod was removed reads as null: the entry is then ignored by the client
			if (e.stack == null) e.count = 0;
		}
		return e;
	}

	public static class Handler implements IMessageHandler<PacketTerminalSync, IMessage> {
		@Override
		public IMessage onMessage(PacketTerminalSync msg, MessageContext ctx) {
			StorageMod.proxy.handleSync(msg);
			return null;
		}
	}
}
