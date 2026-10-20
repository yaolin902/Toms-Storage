package com.tom.storagemod.tile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;

import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.common.registry.GameRegistry;

import com.tom.storagemod.Config;
import com.tom.storagemod.InventoryAccess;
import com.tom.storagemod.InventoryNetwork;

/**
 * Every second, flood-fills outwards through touching inventories (within the
 * configured range) and remembers them as one InventoryNetwork.
 * Other connectors are not walked through, so two networks never double count.
 */
public class TileEntityInventoryConnector extends TileEntity {
	private final InventoryNetwork network = new InventoryNetwork(new ArrayList<InventoryAccess>());
	private boolean scanned;

	public InventoryNetwork getNetwork() {
		return network;
	}

	public int getFreeSlotCount() {
		refreshStats();
		return network.getFreeSlotCount();
	}

	public int getInvSize() {
		refreshStats();
		return network.getTotalSlots();
	}

	/** Number of inventories this connector sees. */
	public int getInventoryCount() {
		return network.getInventoryCount();
	}

	private void refreshStats() {
		if (worldObj != null && !worldObj.isRemote) network.refresh(worldObj);
	}

	@Override
	public void updateEntity() {
		if (worldObj == null || worldObj.isRemote) return;
		// every connector scans once a second, but on its own tick so they don't all land in the same one
		int offset = (((xCoord * 31 + yCoord) * 31 + zCoord) & 0x7FFFFFFF) % 20;
		if (!scanned || worldObj.getTotalWorldTime() % 20 == offset) {
			scan();
			scanned = true;
		}
	}

	private static boolean isAllowed(Block block) {
		if (Config.blockedBlocks.isEmpty() && Config.blockedMods.isEmpty()) return true;
		GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(block);
		return Config.canConnect(block, id == null ? null : id.modId);
	}

	private boolean inRange(int x, int y, int z) {
		int dx = x - xCoord, dy = y - yCoord, dz = z - zCoord;
		return dx * dx + dy * dy + dz * dz < Config.invRangeSq;
	}

	private void scan() {
		Deque<ChunkPosition> toCheck = new ArrayDeque<ChunkPosition>();
		Set<ChunkPosition> checked = new HashSet<ChunkPosition>();
		List<InventoryAccess> found = new ArrayList<InventoryAccess>();

		ChunkPosition start = new ChunkPosition(xCoord, yCoord, zCoord);
		toCheck.push(start);
		checked.add(start);

		while (!toCheck.isEmpty()) {
			ChunkPosition cp = toCheck.pop();
			for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
				int px = cp.chunkPosX + d.offsetX;
				int py = cp.chunkPosY + d.offsetY;
				int pz = cp.chunkPosZ + d.offsetZ;
				ChunkPosition p = new ChunkPosition(px, py, pz);
				if (checked.contains(p) || !inRange(px, py, pz)) continue;
				checked.add(p);
				// Never force-load chunks from here.
				if (py < 0 || py > 255 || !worldObj.blockExists(px, py, pz)) continue;

				TileEntity te = worldObj.getTileEntity(px, py, pz);
				if (te == null || te.isInvalid() || te instanceof TileEntityInventoryConnector) continue;

				Block block = worldObj.getBlock(px, py, pz);
				if (te instanceof IInventory && isAllowed(block)) {
					// 'side' is the face of that inventory which touches the block we came from.
					int side = d.getOpposite().ordinal();
					InventoryAccess acc = network.findExisting((IInventory) te, side);
					if (acc == null) acc = new InventoryAccess((IInventory) te, side);
					// nothing reachable from this side (or a 0-slot inventory): neither use nor walk through it
					if (acc.isUsable()) {
						found.add(acc);
						toCheck.push(p);
					}
				}
				if (Config.multiblockInvs.contains(block)) {
					skipBlocks(px, py, pz, checked, toCheck, block);
				}
			}
		}
		network.setInventories(found);
	}

	/** Marks a whole blob of the same (multiblock) block as one inventory so the walk can cross it. */
	private void skipBlocks(int sx, int sy, int sz, Set<ChunkPosition> checked, Deque<ChunkPosition> edges, Block block) {
		Deque<ChunkPosition> toCheck = new ArrayDeque<ChunkPosition>();
		ChunkPosition start = new ChunkPosition(sx, sy, sz);
		toCheck.push(start);
		edges.push(start);
		while (!toCheck.isEmpty()) {
			ChunkPosition cp = toCheck.pop();
			for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
				int px = cp.chunkPosX + d.offsetX;
				int py = cp.chunkPosY + d.offsetY;
				int pz = cp.chunkPosZ + d.offsetZ;
				ChunkPosition p = new ChunkPosition(px, py, pz);
				if (checked.contains(p) || !inRange(px, py, pz)) continue;
				if (py < 0 || py > 255 || !worldObj.blockExists(px, py, pz)) continue;
				if (worldObj.getBlock(px, py, pz) == block) {
					checked.add(p);
					edges.push(p);
					toCheck.push(p);
				}
			}
		}
	}
}
