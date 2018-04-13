package eu.europeana.metis.transformation.service;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An LRU cache, based on <code>LinkedHashMap</code>.
 *
 * <p>
 * This cache has a fixed maximum number of elements (<code>cacheSize</code>).
 * If the cache is full and another entry is added, the LRU (least recently
 * used) entry is dropped.
 *
 *
 * <p>
 * Author: Christian d'Heureuse, Inventec Informatik AG, Zurich, Switzerland<br>
 * Multi-licensed: EPL / LGPL / GPL / AL / BSD.
 * @param <K> The key type of the map in the LRUCache
 * @param <V> The value type of the map in the LRUCache
 */
public class LRUCache<K, V> implements Serializable {

  private static final long serialVersionUID = 1L;

  private static final float HASH_TABLE_LOAD_FACTOR = 0.75F;

  private int hits;
  private int miss;
  private final LinkedHashMap<K, V> map;
  private final int cacheSize;

  /**
   * Creates a new LRU cache.
   *
   * @param cacheSize the maximum number of entries that will be kept in this cache.
   */
  public LRUCache(int cacheSize) {
    this.cacheSize = cacheSize;
    int hashTableCapacity = (int) Math.ceil((double)cacheSize / (double)HASH_TABLE_LOAD_FACTOR) + 1;
    map = new LinkedHashMap<K, V>(hashTableCapacity, HASH_TABLE_LOAD_FACTOR, true) {
      // (an anonymous inner class)
      private static final long serialVersionUID = 1;

      @Override
      protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > LRUCache.this.cacheSize;
      }
    };
  }

  /**
   * Retrieves an entry from the cache.<br>
   * The retrieved entry becomes the MRU (most recently used) entry.
   *
   * @param key the key whose associated value is to be returned.
   * @return the value associated to this key, or null if no value with this
   * key exists in the cache.
   */
  public V get(K key) {
    return map.get(key);
  }

  /**
   * Adds an entry to this cache. The new entry becomes the MRU (most recently
   * used) entry. If an entry with the specified key already exists in the
   * cache, it is replaced by the new entry. If the cache is full, the LRU
   * (least recently used) entry is removed from the cache.
   *
   * @param key the key with which the specified value is to be associated.
   * @param value a value to be associated with the specified key.
   */
  public void put(K key, V value) {
    map.put(key, value);
  }

  /**
   * Clears the cache.
   */
  public void clear() {
    map.clear();
  }

  /**
   * Returns the number of used entries in the cache.
   *
   * @return the number of entries currently in the cache.
   */
  public int usedEntries() {
    return map.size();
  }


  /**
   * Check if the specified key is part of the internal map
   *
   * @param k the key to check
   * @return true if the key is part of the map, otherwise false
   */
  public boolean containsKey(K k) {
    if (map.containsKey(k)) {
      hits++;
      return true;
    } else {
      miss++;
      return false;
    }
  }

  /**
   * Calculates current hit rate which is the number of hits divided by the total number of requests(hits + miss).
   *
   * @return the hit rate
   */
  public double hitRate() {
    return hits / (double) (hits + miss);
  }
}
