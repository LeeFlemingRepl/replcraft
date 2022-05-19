package eelfloat.replcraft.util;

import java.util.HashMap;
import java.util.function.Supplier;

public class ExpirableCacheMap<K, V> {
    /** What this expirable is guarding */
    public final HashMap<K, ExpirableEntry<V>> entries = new HashMap<>();
    /** How long entries are refreshed to live to*/
    private final long expireMs;

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
        this.expireMs = expireMs;
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
