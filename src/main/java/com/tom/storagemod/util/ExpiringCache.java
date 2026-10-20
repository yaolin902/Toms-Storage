package com.tom.storagemod.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Tiny map whose entries stop being returned after a while (tooltips and tags rarely change, but can). */
public class ExpiringCache<K, V> {
	private static class Entry<V> {
		final V value;
		final long expires;

		Entry(V value, long expires) {
			this.value = value;
			this.expires = expires;
		}
	}

	private final Map<K, Entry<V>> map = new HashMap<K, Entry<V>>();
	private final long ttlMillis;
	private final int purgeAbove;

	public ExpiringCache(long ttlMillis, int purgeAbove) {
		this.ttlMillis = ttlMillis;
		this.purgeAbove = purgeAbove;
	}

	public V get(K key, long now) {
		Entry<V> e = map.get(key);
		if (e == null) return null;
		if (e.expires < now) {
			map.remove(key);
			return null;
		}
		return e.value;
	}

	public void put(K key, V value, long now) {
		if (map.size() > purgeAbove) purge(now);
		map.put(key, new Entry<V>(value, now + ttlMillis));
	}

	private void purge(long now) {
		for (Iterator<Entry<V>> it = map.values().iterator(); it.hasNext();) {
			if (it.next().expires < now) it.remove();
		}
		if (map.size() > purgeAbove) map.clear(); // still too many live entries: start over
	}

	public int size() {
		return map.size();
	}

	public void clear() {
		map.clear();
	}
}
