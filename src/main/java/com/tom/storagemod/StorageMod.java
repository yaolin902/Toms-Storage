package com.tom.storagemod;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import net.minecraftforge.oredict.ShapedOreRecipe;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import com.tom.storagemod.block.CraftingTerminal;
import com.tom.storagemod.block.InventoryConnector;
import com.tom.storagemod.block.StorageTerminal;
import com.tom.storagemod.gui.GuiHandler;
import com.tom.storagemod.net.PacketTerminalSync;
import com.tom.storagemod.net.PacketTerminalAction;
import com.tom.storagemod.proxy.IProxy;
import com.tom.storagemod.tile.TileEntityCraftingTerminal;
import com.tom.storagemod.tile.TileEntityInventoryConnector;
import com.tom.storagemod.tile.TileEntityStorageTerminal;

@Mod(modid = StorageMod.MODID, name = "Tom's Simple Storage Mod", version = Tags.VERSION, acceptedMinecraftVersions = "[1.7.10]")
public class StorageMod {
	public static final String MODID = "toms_storage";

	@Mod.Instance(MODID)
	public static StorageMod instance;

	@SidedProxy(clientSide = "com.tom.storagemod.proxy.ClientProxy", serverSide = "com.tom.storagemod.proxy.ServerProxy")
	public static IProxy proxy;

	public static SimpleNetworkWrapper network;

	public static final CreativeTabs tab = new CreativeTabs(MODID) {
		@Override
		@SideOnly(Side.CLIENT)
		public Item getTabIconItem() {
			return Item.getItemFromBlock(terminal);
		}
	};

	public static InventoryConnector connector;
	public static StorageTerminal terminal;
	public static CraftingTerminal craftingTerminal;

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		Config.load(event.getSuggestedConfigurationFile());

		connector = new InventoryConnector();
		terminal = new StorageTerminal();
		craftingTerminal = new CraftingTerminal();
		GameRegistry.registerBlock(connector, "ts.inventory_connector");
		GameRegistry.registerBlock(terminal, "ts.storage_terminal");
		GameRegistry.registerBlock(craftingTerminal, "ts.crafting_terminal");
		GameRegistry.registerTileEntity(TileEntityInventoryConnector.class, "ts.inventory_connector.tile");
		GameRegistry.registerTileEntity(TileEntityStorageTerminal.class, "ts.storage_terminal.tile");
		GameRegistry.registerTileEntity(TileEntityCraftingTerminal.class, "ts.crafting_terminal.tile");

		network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
		network.registerMessage(PacketTerminalSync.Handler.class, PacketTerminalSync.class, 0, Side.CLIENT);
		network.registerMessage(PacketTerminalAction.Handler.class, PacketTerminalAction.class, 1, Side.SERVER);

		NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());
		proxy.preInit();
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		// Same layouts as the 1.16 recipes (tags -> ore dictionary).
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(connector),
				"PCP",
				"cDc",
				"PEP",
				'P', "plankWood",
				'C', Items.comparator,
				'c', Blocks.chest,
				'D', "gemDiamond",
				'E', Items.ender_pearl));

		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(terminal),
				"PCP",
				"cGg",
				"PCP",
				'P', "plankWood",
				'C', Items.comparator,
				'c', Blocks.chest,
				'G', Blocks.glowstone,
				'g', "blockGlass"));

		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(craftingTerminal),
				"cdc",
				"dtd",
				"cdc",
				'c', Blocks.crafting_table,
				'd', "gemDiamond",
				't', terminal));
	}

	@Mod.EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		// other mods have registered their blocks by now
		Config.resolveBlocks();
	}
}
