package com.tom.storagemod.gui;

/** Click schemes for the terminal grid (see tooltip.toms_storage.ctrlMode_N). */
public enum ControllMode {
	/** Applied Energistics like. */
	AE,
	/** Refined Storage like (middle click). */
	RS,
	/** Vanilla like. */
	DEF;

	public static final ControllMode[] VALUES = values();
}
