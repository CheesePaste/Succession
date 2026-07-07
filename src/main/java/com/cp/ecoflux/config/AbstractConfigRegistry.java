package com.cp.ecoflux.config;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public abstract class AbstractConfigRegistry<K, T> {
    private volatile Map<K, T> store = Map.of();

    protected synchronized void replaceAll(Collection<T> entries, Function<T, K> keyExtractor) {
        Map<K, T> map = new ConcurrentHashMap<>();
        for (T entry : entries) {
            map.put(keyExtractor.apply(entry), entry);
        }
        this.store = Map.copyOf(map);
    }

    public Optional<T> get(K key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(store.get(key));
    }

    public Collection<T> getAll() {
        return Collections.unmodifiableCollection(store.values());
    }

    public int size() {
        return store.size();
    }
}
