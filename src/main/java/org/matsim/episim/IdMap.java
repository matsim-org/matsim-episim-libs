package org.matsim.episim;

import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.matsim.api.core.v01.Id;

import java.util.function.Function;

/**
 * A specialized map with {@link Id} as keys. Its API is quite reduced, only use it when performance is necessary.
 */
final class IdMap<T, V> extends IntObjectHashMap<V> {

	public IdMap() {
	}

	public V put(Id<?> id, V value) {
		return this.put(id.index(), value);
	}

	public boolean containsKey(Id<T> key) {
		return super.containsKey(key.index());
	}

	public V get(Id<T> id) {
		return get(id.index());
	}

	public V computeIfAbsent(Id<T> key, Function<Id<T>, ? extends V> mappingFunction) {
		V v;
		if ((v = get(key)) == null) {
			V newValue;
			if ((newValue = mappingFunction.apply(key)) != null) {
				put(key, newValue);
				return newValue;
			}
		}

		return v;
	}
}
