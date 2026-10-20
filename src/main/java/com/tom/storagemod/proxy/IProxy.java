package com.tom.storagemod.proxy;

import com.tom.storagemod.net.PacketTerminalSync;

public interface IProxy {
	void preInit();

	/** Called on the client when the server sends (part of) a terminal's item list. */
	void handleSync(PacketTerminalSync msg);
}
