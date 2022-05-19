package eelfloat.replcraft.util;

import java.util.HashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class ExpirableCacheMap<K, V> {
    /** What this expirable is guarding */
    public final HashMap<K, ExpirableEntry<V>> entries = new HashMap<>();
    /** How long entries are refreshed to live to*/
    private final long expireMs;
    private BiConsumer<K, V> onExpire;

    private class ExpirableEntry<V> {
        /** The value for this entry */
        V expiree;
        /** When this expirable expires */
        long expiration;

        public ExpirableEntry(V expiree) {
            this.expiree = expiree;
            this.resetExpiration();
        }

        public void resetExpiration() {
            expiration = System.currentTimeMillis() + expireMs;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiration;
        }
    }

    public ExpirableCacheMap(long expireMs) {
        this(expireMs, (a, b) -> {});
    }

    public ExpirableCacheMap(long expireMs, BiConsumer<K, V> onExpire) {
        this.expireMs = expireMs;
        this.onExpire = onExpire;
    }

    /**
     * Gets an entry, filling it with a default value if empty.
     * Also resets the key's expiration.
     * @param key the entry's key
     * @param defaultSupplier a default value supplier
     * @return the entry's value
     */
    public V get(K key, Supplier<V> defaultSupplier) {
        if (!this.entries.containsKey(key)) {
            this.entries.put(key, new ExpirableEntry<>(defaultSupplier.get()));
        }
        this.resetExpiration(key);
        return this.entries.get(key).expiree;
    }

    /**
     * Removes an entry without calling the expiration callback
     */
    public V remove(K key) {
        ExpirableEntry<V> removed = this.entries.remove(key);
        if (removed != null) return removed.expiree;
        return null;
    }

    public void set(K key, V value) {
        ExpirableEntry<V> removed = this.entries.put(key, new ExpirableEntry<>(value));
        if (removed != null) this.onExpire.accept(key, removed.expiree);
    }

    /**
     * Removes expired values from the map and calls the expiration callback with anything that was removed.
     */
    public void expire() {
        this.entries.entrySet().removeIf(next -> {
            if (next.getValue().isExpired()) {
                this.onExpire.accept(next.getKey(), next.getValue().expiree);
                return true;
            }
            return false;
        });
    }

    /**
     * Resets an entry's expiration date
     * @param key the entry's key
     * @return if the entry was present and reset
     */
    public boolean resetExpiration(K key) {
        ExpirableEntry<V> entry = this.entries.get(key);
        if (entry == null) return false;
        entry.resetExpiration();
        return true;
    }
}
