package com.tom.storagemod.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import com.tom.storagemod.TerminalItemStack;

/**
 * The terminal search language (same as 2.4.2):
 * <ul>
 * <li><code>iron pick</code> - every word must match (name or tooltip, regex allowed)</li>
 * <li><code>iron | gold</code> - either side may match</li>
 * <li><code>@mod</code> - mod id contains</li>
 * <li><code>#ore</code> - ore dictionary name contains</li>
 * <li><code>$regex</code> - item NBT matches</li>
 * </ul>
 */
public final class TerminalSearch {
	private TerminalSearch() {
	}

	/** Where the search gets its data from (the GUI caches these, they can be slow to compute). */
	public interface ItemInfo {
		String getName(TerminalItemStack s);

		/** Lowercase, formatting codes removed. */
		List<String> getTooltipLines(TerminalItemStack s);

		/** Ore dictionary names, lowercase. */
		Collection<String> getOreNames(TerminalItemStack s);

		/** The item's NBT as text; empty if it has none. */
		String getNbt(TerminalItemStack s);

		String getModId(TerminalItemStack s);
	}

	public interface Matcher {
		boolean test(TerminalItemStack s);
	}

	private static final Matcher ALWAYS = new Matcher() {
		@Override
		public boolean test(TerminalItemStack s) {
			return true;
		}
	};

	/** Text -> matcher. An empty/blank search matches everything. */
	public static Matcher compile(String query, final ItemInfo info) {
		final List<List<Matcher>> alternatives = new ArrayList<List<Matcher>>();
		for (String part : query.split("\\|")) {
			part = part.trim();
			if (part.isEmpty()) continue;
			List<Matcher> terms = new ArrayList<Matcher>();
			for (String word : part.split(" ")) {
				if (word.isEmpty()) continue;
				Matcher m = term(word.toLowerCase(), info);
				if (m != null) terms.add(m);
			}
			if (!terms.isEmpty()) alternatives.add(terms);
		}
		if (alternatives.isEmpty()) return ALWAYS;
		return new Matcher() {
			@Override
			public boolean test(TerminalItemStack s) {
				for (List<Matcher> all : alternatives) {
					boolean ok = true;
					for (Matcher m : all) {
						if (!m.test(s)) {
							ok = false;
							break;
						}
					}
					if (ok) return true;
				}
				return false;
			}
		};
	}

	private static Matcher term(String word, final ItemInfo info) {
		if (word.startsWith("@")) {
			final String f = word.substring(1);
			return new Matcher() {
				@Override
				public boolean test(TerminalItemStack s) {
					return info.getModId(s).toLowerCase().contains(f);
				}
			};
		}
		if (word.startsWith("#")) {
			final String f = word.substring(1);
			return new Matcher() {
				@Override
				public boolean test(TerminalItemStack s) {
					for (String ore : info.getOreNames(s)) {
						if (ore.contains(f)) return true;
					}
					return false;
				}
			};
		}
		if (word.startsWith("$")) {
			final Pattern p = pattern(word.substring(1));
			if (p == null) return null;
			return new Matcher() {
				@Override
				public boolean test(TerminalItemStack s) {
					String nbt = info.getNbt(s);
					return nbt != null && !nbt.isEmpty() && p.matcher(nbt).find();
				}
			};
		}
		final Pattern p = pattern(word);
		if (p == null) return null;
		return new Matcher() {
			@Override
			public boolean test(TerminalItemStack s) {
				if (p.matcher(info.getName(s).toLowerCase()).find()) return true;
				for (String line : info.getTooltipLines(s)) {
					if (p.matcher(line).find()) return true;
				}
				return false;
			}
		};
	}

	/** Regex if it is one, otherwise the literal text. */
	private static Pattern pattern(String s) {
		try {
			return Pattern.compile(s, Pattern.CASE_INSENSITIVE);
		} catch (Throwable t) {
			try {
				return Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE);
			} catch (Throwable t2) {
				return null;
			}
		}
	}
}
