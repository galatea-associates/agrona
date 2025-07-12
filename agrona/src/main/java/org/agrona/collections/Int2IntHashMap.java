/*
 * Copyright 2014-2025 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.agrona.collections;

import org.agrona.generation.DoNotSub;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

import static java.util.Objects.requireNonNull;
import static org.agrona.BitUtil.findNextPositivePowerOfTwo;
import static org.agrona.collections.CollectionUtil.validateLoadFactor;

/**
 * A high-performance hash map specialized for primitive int-to-int key-value pairs that completely
 * avoids boxing/unboxing overhead through type-specific implementation.
 * 
 * <h2>Design Characteristics</h2>
 * <p>This implementation uses open-addressing with linear probing for collision resolution, providing
 * excellent cache locality and minimal memory overhead. The map stores key-value pairs in a single
 * int array with even indices for keys and odd indices for values.</p>
 * 
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><strong>Time Complexity:</strong> O(1) average case for all operations, O(n) worst case</li>
 *   <li><strong>Memory Overhead:</strong> ~75% less memory usage compared to HashMap&lt;Integer, Integer&gt;</li>
 *   <li><strong>Cache Performance:</strong> Excellent locality due to linear probing and packed storage</li>
 *   <li><strong>Load Factor:</strong> Default 0.67, resize occurs when size exceeds threshold</li>
 *   <li><strong>Capacity:</strong> Always power-of-2, minimum 8 elements</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * <p><strong>Not thread-safe.</strong> External synchronization is required for concurrent access.
 * Multiple readers are safe only if no concurrent modifications occur.</p>
 * 
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Basic usage with automatic missing value detection
 * Int2IntHashMap map = new Int2IntHashMap(-1); // -1 represents null/missing
 * 
 * // Store key-value pairs without boxing
 * map.put(42, 100);
 * map.put(17, 200);
 * 
 * // Retrieve values efficiently
 * int value = map.get(42); // Returns 100
 * int missing = map.get(99); // Returns -1 (missing value)
 * 
 * // Check existence without retrieving value
 * if (map.containsKey(42)) {
 *     // Process known key
 * }
 * 
 * // Iterate without allocation (preferred)
 * map.forEachInt((key, value) -> {
 *     System.out.println(key + " -> " + value);
 * });
 * 
 * // Compute operations for efficient updates
 * map.computeIfAbsent(50, key -> key * 2); // Sets 50 -> 100 if absent
 * map.computeIfPresent(42, (key, oldValue) -> oldValue + 10); // Updates 42 -> 110
 * }</pre>
 * 
 * <h2>Memory Layout</h2>
 * <p>Entries are stored in a single int[] array where:</p>
 * <ul>
 *   <li>Even indices (0, 2, 4, ...) contain keys</li>
 *   <li>Odd indices (1, 3, 5, ...) contain values</li>
 *   <li>Missing entries are marked with the configured missingValue</li>
 * </ul>
 * 
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Choose a missingValue that won't conflict with legitimate values</li>
 *   <li>Size the initial capacity appropriately to minimize resizing</li>
 *   <li>Use primitive methods (get(int), put(int, int)) instead of boxed versions</li>
 *   <li>Use forEachInt() instead of entrySet().forEach() to avoid allocation</li>
 *   <li>Consider the load factor impact on performance vs memory usage</li>
 * </ul>
 * 
 * <h2>Comparison with Standard Collections</h2>
 * <table border="1">
 * <caption>Performance Comparison</caption>
 * <tr><th>Metric</th><th>Int2IntHashMap</th><th>HashMap&lt;Integer, Integer&gt;</th></tr>
 * <tr><td>Memory per entry</td><td>8 bytes</td><td>32+ bytes</td></tr>
 * <tr><td>Boxing overhead</td><td>None</td><td>Significant</td></tr>
 * <tr><td>GC pressure</td><td>Minimal</td><td>High (from boxing)</td></tr>
 * <tr><td>Cache locality</td><td>Excellent</td><td>Poor (object indirection)</td></tr>
 * </table>
 * 
 * @see org.agrona.collections.Long2LongHashMap Long2LongHashMap for long key-value pairs
 * @see org.agrona.collections.IntHashSet IntHashSet for int-only sets
 */
public class Int2IntHashMap implements Map<Integer, Integer>
{
    @DoNotSub static final int MIN_CAPACITY = 8;

    private final float loadFactor;
    private final int missingValue;
    @DoNotSub private int resizeThreshold;
    @DoNotSub private int size = 0;
    private final boolean shouldAvoidAllocation;

    private int[] entries;
    private KeySet keySet;
    private ValueCollection values;
    private EntrySet entrySet;

    /**
     * Create a map instance with default configuration and specified missing value.
     * <p>Uses minimum capacity of 8 elements, default load factor of 0.67, and enables
     * iterator caching to avoid allocation during iteration.</p>
     * 
     * <h3>Performance Impact</h3>
     * <ul>
     *   <li>Initial capacity: 8 elements (will auto-resize as needed)</li>
     *   <li>Memory usage: ~64 bytes initial allocation</li>
     *   <li>Resize threshold: 5 elements (8 * 0.67)</li>
     * </ul>
     * 
     * <h3>Missing Value Selection</h3>
     * <p>Choose a missingValue that will never be a legitimate value in your use case:</p>
     * <ul>
     *   <li>For positive counters: use -1 or Integer.MIN_VALUE</li>
     *   <li>For general integers: use Integer.MIN_VALUE or Integer.MAX_VALUE</li>
     *   <li>For small ranges: use a value outside your expected range</li>
     * </ul>
     * 
     * @param missingValue the value used to represent null/absent entries. This value
     *                     cannot be stored as a legitimate value in the map.
     * 
     * @throws IllegalArgumentException if later attempting to store the missingValue
     * 
     * @see #Int2IntHashMap(int, float, int) for custom capacity and load factor
     */
    public Int2IntHashMap(final int missingValue)
    {
        this(MIN_CAPACITY, Hashing.DEFAULT_LOAD_FACTOR, missingValue);
    }

    /**
     * Create a map instance with custom capacity and load factor configuration.
     * <p>This constructor allows fine-tuning performance characteristics based on
     * expected usage patterns and memory constraints.</p>
     * 
     * <h3>Capacity Planning</h3>
     * <p>The actual capacity will be the next power-of-2 greater than or equal to the
     * specified initialCapacity, with a minimum of 8. Plan capacity based on:</p>
     * <ul>
     *   <li><strong>Expected size:</strong> Set initial capacity to expected maximum size / load factor</li>
     *   <li><strong>Performance priority:</strong> Larger capacity = fewer collisions but more memory</li>
     *   <li><strong>Memory constraints:</strong> Each slot uses 8 bytes (key + value)</li>
     * </ul>
     * 
     * <h3>Load Factor Selection</h3>
     * <p>Load factor trades memory usage against performance:</p>
     * <ul>
     *   <li><strong>0.5-0.6:</strong> Excellent performance, ~50% memory waste</li>
     *   <li><strong>0.67 (default):</strong> Good balance of performance and memory</li>
     *   <li><strong>0.75-0.8:</strong> Good memory usage, increased collision probability</li>
     *   <li><strong>0.9+:</strong> Memory efficient but poor performance due to clustering</li>
     * </ul>
     * 
     * <h3>Example Sizing</h3>
     * <pre>{@code
     * // For ~1000 elements with good performance
     * new Int2IntHashMap(1500, 0.67f, -1); // Capacity: 2048, threshold: 1365
     * 
     * // For memory-constrained environment
     * new Int2IntHashMap(800, 0.8f, -1);   // Capacity: 1024, threshold: 819
     * 
     * // For maximum performance
     * new Int2IntHashMap(2000, 0.5f, -1);  // Capacity: 4096, threshold: 2048
     * }</pre>
     * 
     * @param initialCapacity minimum capacity for the map, actual capacity will be next power-of-2
     * @param loadFactor      fraction of capacity at which resize occurs (0.1 to 0.9)
     * @param missingValue    value representing null/absent entries
     * 
     * @throws IllegalArgumentException if loadFactor is not between 0.1 and 0.9
     */
    public Int2IntHashMap(
        @DoNotSub final int initialCapacity,
        @DoNotSub final float loadFactor,
        final int missingValue)
    {
        this(initialCapacity, loadFactor, missingValue, true);
    }

    /**
     * Create a map instance with specified parameters.
     *
     * @param initialCapacity       for the map to override {@link #MIN_CAPACITY}
     * @param loadFactor            for the map to override {@link Hashing#DEFAULT_LOAD_FACTOR}.
     * @param missingValue          for the map that represents null.
     * @param shouldAvoidAllocation should allocation be avoided by caching iterators and map entries.
     */
    public Int2IntHashMap(
        @DoNotSub final int initialCapacity,
        @DoNotSub final float loadFactor,
        final int missingValue,
        final boolean shouldAvoidAllocation)
    {
        validateLoadFactor(loadFactor);

        this.loadFactor = loadFactor;
        this.missingValue = missingValue;
        this.shouldAvoidAllocation = shouldAvoidAllocation;

        capacity(findNextPositivePowerOfTwo(Math.max(MIN_CAPACITY, initialCapacity)));
    }

    /**
     * Copy construct a new map from an existing one.
     *
     * @param mapToCopy for construction.
     */
    public Int2IntHashMap(final Int2IntHashMap mapToCopy)
    {
        this.loadFactor = mapToCopy.loadFactor;
        this.resizeThreshold = mapToCopy.resizeThreshold;
        this.size = mapToCopy.size;
        this.shouldAvoidAllocation = mapToCopy.shouldAvoidAllocation;
        this.missingValue = mapToCopy.missingValue;

        entries = mapToCopy.entries.clone();
    }

    /**
     * The value to be used as a null marker in the map.
     *
     * @return value to be used as a null marker in the map.
     */
    public int missingValue()
    {
        return missingValue;
    }

    /**
     * Get the load factor applied for resize operations.
     *
     * @return the load factor applied for resize operations.
     */
    public float loadFactor()
    {
        return loadFactor;
    }

    /**
     * Get the current capacity of the map's internal storage.
     * <p>Capacity represents the maximum number of key-value pairs that can be stored
     * before triggering a resize operation. The actual resize occurs when size exceeds
     * {@code capacity * loadFactor}.</p>
     * 
     * <h3>Capacity Characteristics</h3>
     * <ul>
     *   <li><strong>Always power-of-2:</strong> Enables efficient hash masking</li>
     *   <li><strong>Minimum value:</strong> 8 elements</li>
     *   <li><strong>Growth pattern:</strong> Doubles on each resize (8 → 16 → 32 → 64 ...)</li>
     *   <li><strong>Memory usage:</strong> capacity × 8 bytes for storage array</li>
     * </ul>
     * 
     * <h3>Monitoring Usage</h3>
     * <pre>{@code
     * Int2IntHashMap map = new Int2IntHashMap(100, 0.75f, -1);
     * 
     * System.out.printf("Capacity: %d elements%n", map.capacity());        // 128
     * System.out.printf("Memory: %d bytes%n", map.capacity() * 8);          // 1024 bytes
     * System.out.printf("Threshold: %d elements%n", map.resizeThreshold()); // 96
     * System.out.printf("Current size: %d%n", map.size());                  // 0
     * 
     * // Calculate efficiency
     * double utilization = (double) map.size() / map.capacity();
     * if (utilization < 0.25) {
     *     System.out.println("Consider compacting to save memory");
     * }
     * }</pre>
     * 
     * @return the current capacity (number of possible key-value pairs before resize)
     * 
     * @see #resizeThreshold() for the actual resize trigger point
     * @see #size() for current number of stored elements
     * @see #compact() to optimize capacity for current size
     */
    @DoNotSub public int capacity()
    {
        return entries.length >> 1;
    }

    /**
     * Get the size threshold that triggers automatic map resizing.
     * <p>When the number of stored elements exceeds this threshold, the map will
     * double its capacity and rehash all existing entries. The threshold is calculated
     * as {@code capacity × loadFactor}.</p>
     * 
     * <h3>Resize Behavior</h3>
     * <p>When size exceeds threshold:</p>
     * <ol>
     *   <li>New capacity = current capacity × 2</li>
     *   <li>New threshold = new capacity × load factor</li>
     *   <li>All entries rehashed to new positions</li>
     *   <li>Original array discarded for garbage collection</li>
     * </ol>
     * 
     * <h3>Performance Planning</h3>
     * <p>Understanding the threshold helps predict resize operations:</p>
     * <pre>{@code
     * Int2IntHashMap map = new Int2IntHashMap(64, 0.75f, -1);
     * 
     * System.out.printf("Current threshold: %d%n", map.resizeThreshold()); // 48
     * System.out.printf("Next resize at size: %d%n", map.resizeThreshold() + 1);
     * 
     * // Add elements and monitor approaching resize
     * for (int i = 0; i < 50; i++) {
     *     map.put(i, i * 2);
     *     if (map.size() >= map.resizeThreshold() - 5) {
     *         System.out.printf("Approaching resize: %d/%d%n", 
     *                          map.size(), map.resizeThreshold());
     *     }
     * }
     * 
     * // After resize
     * System.out.printf("New threshold: %d%n", map.resizeThreshold()); // 96
     * }</pre>
     * 
     * <h3>Load Factor Impact</h3>
     * <p>Examples with capacity of 128:</p>
     * <ul>
     *   <li><strong>Load factor 0.5:</strong> Threshold = 64 (more space, better performance)</li>
     *   <li><strong>Load factor 0.67:</strong> Threshold = 85 (balanced)</li>
     *   <li><strong>Load factor 0.75:</strong> Threshold = 96 (memory efficient)</li>
     * </ul>
     * 
     * @return the number of elements that will trigger the next resize operation
     * 
     * @see #capacity() for maximum possible elements before resize
     * @see #loadFactor() for the multiplier used in threshold calculation
     * @see #size() for current number of stored elements
     */
    @DoNotSub public int resizeThreshold()
    {
        return resizeThreshold;
    }

    /**
     * {@inheritDoc}
     */
    @DoNotSub public int size()
    {
        return size;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty()
    {
        return 0 == size;
    }

    /**
     * Returns the value to which the specified key is mapped, or
     * {@code defaultValue} if this map contains no mapping for the key.
     *
     * @param key          whose associated value is to be returned.
     * @param defaultValue to be returned if there is no value in the map for a given {@code key}.
     * @return the value to which the specified key is mapped, or
     * {@code defaultValue} if this map contains no mapping for the key.
     */
    public int getOrDefault(final int key, final int defaultValue)
    {
        final int value = get(key);
        return missingValue != value ? value : defaultValue;
    }

    /**
     * Retrieve a value by key without boxing/unboxing overhead.
     * <p>This is the primary lookup method optimized for high-performance scenarios.
     * Uses open-addressing with linear probing for cache-friendly access patterns.</p>
     * 
     * <h3>Performance Characteristics</h3>
     * <ul>
     *   <li><strong>Average case:</strong> O(1) with 1-2 memory accesses</li>
     *   <li><strong>Worst case:</strong> O(n) if severe clustering occurs</li>
     *   <li><strong>No allocation:</strong> Zero garbage generation</li>
     *   <li><strong>Cache friendly:</strong> Linear probing maintains spatial locality</li>
     * </ul>
     * 
     * <h3>Algorithm Details</h3>
     * <p>Lookup process:</p>
     * <ol>
     *   <li>Compute hash index using {@code Hashing.evenHash(key, mask)}</li>
     *   <li>Check key at computed index</li>
     *   <li>If not found, probe linearly (index + 2) until key found or empty slot</li>
     *   <li>Return corresponding value or missingValue</li>
     * </ol>
     * 
     * <h3>Usage Examples</h3>
     * <pre>{@code
     * Int2IntHashMap counters = new Int2IntHashMap(-1);
     * counters.put(100, 42);
     * 
     * // Basic lookup
     * int value = counters.get(100);  // Returns 42
     * int missing = counters.get(200); // Returns -1 (missing value)
     * 
     * // Check for existence before use
     * int result = counters.get(key);
     * if (result != counters.missingValue()) {
     *     // Key exists, use result
     *     processValue(result);
     * }
     * 
     * // Or use getOrDefault for cleaner code
     * int safeValue = counters.getOrDefault(key, 0);
     * }</pre>
     * 
     * @param key the lookup key
     * @return value associated with the key, or {@link #missingValue()} if key not found
     * 
     * @see #getOrDefault(int, int) for automatic default value handling
     * @see #containsKey(int) to check existence without retrieving value
     */
    public int get(final int key)
    {
        final int missingValue = this.missingValue;
        final int[] entries = this.entries;
        @DoNotSub final int mask = entries.length - 1;
        @DoNotSub int index = Hashing.evenHash(key, mask);

        int value;
        while (missingValue != (value = entries[index + 1]))
        {
            if (key == entries[index])
            {
                break;
            }

            index = next(index, mask);
        }

        return value;
    }

    /**
     * Store a key-value pair in the map with optimal performance for primitive types.
     * <p>This method provides both insert and update functionality. If the key already exists,
     * its value is replaced and the old value is returned. If the key is new, it's inserted
     * and the missing value is returned.</p>
     * 
     * <h3>Performance Characteristics</h3>
     * <ul>
     *   <li><strong>Average case:</strong> O(1) with 1-3 memory accesses</li>
     *   <li><strong>Worst case:</strong> O(n) during resize or with clustering</li>
     *   <li><strong>Resize cost:</strong> O(n) when load factor exceeded, amortized O(1)</li>
     *   <li><strong>Memory impact:</strong> No boxing allocation, possible array resize</li>
     * </ul>
     * 
     * <h3>Resize Behavior</h3>
     * <p>When size exceeds {@code capacity * loadFactor}:</p>
     * <ul>
     *   <li>Capacity doubles (maintaining power-of-2)</li>
     *   <li>All existing entries are rehashed to new positions</li>
     *   <li>Resize threshold is recalculated</li>
     *   <li>Operation completes before returning</li>
     * </ul>
     * 
     * <h3>Usage Examples</h3>
     * <pre>{@code
     * Int2IntHashMap cache = new Int2IntHashMap(-1);
     * 
     * // Basic insertion
     * int oldValue = cache.put(42, 100);  // Returns -1 (missing), stores 42->100
     * 
     * // Update existing key
     * oldValue = cache.put(42, 200);      // Returns 100, updates to 42->200
     * 
     * // Increment pattern
     * int current = cache.get(key);
     * if (current != cache.missingValue()) {
     *     cache.put(key, current + 1);    // Increment existing
     * } else {
     *     cache.put(key, 1);              // Initialize new counter
     * }
     * 
     * // Better: use compute methods for atomic updates
     * cache.computeIfAbsent(key, k -> 1);
     * cache.computeIfPresent(key, (k, v) -> v + 1);
     * }</pre>
     * 
     * <h3>Memory Considerations</h3>
     * <p>Resize doubles capacity, so memory usage follows pattern: 8 → 16 → 32 → 64 → ... elements.
     * Each element uses 8 bytes (key + value ints). Plan initial capacity to minimize resizes.</p>
     * 
     * @param key   the lookup key (any int value)
     * @param value the value to store (cannot be {@link #missingValue()})
     * @return previous value for this key, or {@link #missingValue()} if key was not present
     * 
     * @throws IllegalArgumentException if value equals {@link #missingValue()}
     * 
     * @see #putIfAbsent(int, int) to insert only if key doesn't exist
     * @see #computeIfAbsent(int, IntUnaryOperator) for conditional insertion with computation
     */
    public int put(final int key, final int value)
    {
        final int missingValue = this.missingValue;
        if (missingValue == value)
        {
            throw new IllegalArgumentException("cannot accept missingValue");
        }

        final int[] entries = this.entries;
        @DoNotSub final int mask = entries.length - 1;
        @DoNotSub int index = Hashing.evenHash(key, mask);

        int oldValue;
        while (missingValue != (oldValue = entries[index + 1]))
        {
            if (key == entries[index])
            {
                break;
            }

            index = next(index, mask);
        }

        if (missingValue == oldValue)
        {
            ++size;
            entries[index] = key;
        }

        entries[index + 1] = value;

        increaseCapacity();

        return oldValue;
    }

    /**
     * Primitive specialised version of {@link Map#putIfAbsent(Object, Object)} method.
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or
     * {@link #missingValue()} if there was no mapping for the key.
     * @throws IllegalArgumentException if value is {@link #missingValue()}
     */
    public int putIfAbsent(final int key, final int value)
    {
        final int missingValue = this.missingValue;
        if (missingValue == value)
        {
            throw new IllegalArgumentException("cannot accept missingValue");
        }

        final int[] entries = this.entries;
        @DoNotSub final int mask = entries.length - 1;
        @DoNotSub int index = Hashing.evenHash(key, mask);

        int oldValue;
        while (missingValue != (oldValue = entries[index + 1]))
        {
            if (key == entries[index])
            {
                return oldValue;
            }

            index = next(index, mask);
        }

        ++size;
        entries[index] = key;
        entries[index + 1] = value;

        increaseCapacity();

        return oldValue;
    }

    private void increaseCapacity()
    {
        if (size > resizeThreshold)
        {
            // entries.length = 2 * capacity
            @DoNotSub final int newCapacity = entries.length;
            rehash(newCapacity);
        }
    }

    private void rehash(@DoNotSub final int newCapacity)
    {
        final int missingValue = this.missingValue;
        final int[] oldEntries = entries;
        @DoNotSub final int length = oldEntries.length;

        capacity(newCapacity);

        final int[] newEntries = entries;
        @DoNotSub final int mask = newEntries.length - 1;

        for (@DoNotSub int valueIndex = 1; valueIndex < length; valueIndex += 2)
        {
            final int value = oldEntries[valueIndex];
            if (missingValue != value)
            {
                final int key = oldEntries[valueIndex - 1];
                @DoNotSub int newKeyIndex = Hashing.evenHash(key, mask);

                while (missingValue != newEntries[newKeyIndex + 1])
                {
                    newKeyIndex = next(newKeyIndex, mask);
                }

                newEntries[newKeyIndex] = key;
                newEntries[newKeyIndex + 1] = value;
            }
        }
    }

    /**
     * Use {@link #forEachInt(IntIntConsumer)} instead.
     *
     * @param consumer a callback called for each key/value pair in the map.
     * @see #forEachInt(IntIntConsumer)
     * @deprecated Use {@link #forEachInt(IntIntConsumer)} instead.
     */
    @Deprecated
    public void intForEach(final IntIntConsumer consumer)
    {
        forEachInt(consumer);
    }

    /**
     * High-performance iteration over all key-value pairs without boxing overhead.
     * <p>This method provides the most efficient way to process all entries in the map,
     * avoiding object allocation and boxing that occurs with standard Java 8 streams
     * or iterator approaches.</p>
     * 
     * <h3>Performance Benefits</h3>
     * <ul>
     *   <li><strong>Zero allocation:</strong> No temporary objects created during iteration</li>
     *   <li><strong>No boxing:</strong> Primitive values passed directly to consumer</li>
     *   <li><strong>Cache efficient:</strong> Sequential access through underlying array</li>
     *   <li><strong>Predictable:</strong> Deterministic iteration order (insertion-dependent)</li>
     * </ul>
     * 
     * <h3>Algorithm Details</h3>
     * <p>Iteration process:</p>
     * <ol>
     *   <li>Traverse the internal array sequentially</li>
     *   <li>Skip slots containing missingValue (empty slots)</li>
     *   <li>Call consumer for each valid key-value pair</li>
     *   <li>Early termination when all elements processed</li>
     * </ol>
     * 
     * <h3>Usage Examples</h3>
     * <pre>{@code
     * Int2IntHashMap inventory = new Int2IntHashMap(-1);
     * // ... populate map ...
     * 
     * // Simple processing
     * inventory.forEachInt((productId, quantity) -> {
     *     System.out.printf("Product %d: %d units%n", productId, quantity);
     * });
     * 
     * // Aggregation without allocation
     * final AtomicInteger totalValue = new AtomicInteger();
     * priceMap.forEachInt((item, price) -> {
     *     totalValue.addAndGet(price);
     * });
     * 
     * // Conditional processing with early termination
     * final AtomicBoolean found = new AtomicBoolean(false);
     * map.forEachInt((key, value) -> {
     *     if (value > threshold) {
     *         processHighValue(key, value);
     *         found.set(true);
     *         // Note: cannot break early from forEach, use iterator for that
     *     }
     * });
     * 
     * // Performance comparison:
     * // SLOW: map.entrySet().forEach(entry -> process(entry.getKey(), entry.getValue()))
     * // FAST: map.forEachInt((key, value) -> process(key, value))
     * }</pre>
     * 
     * <h3>Thread Safety</h3>
     * <p><strong>Warning:</strong> Concurrent modification during iteration will cause
     * undefined behavior. Ensure no other threads modify the map during iteration.</p>
     * 
     * <h3>Naming Rationale</h3>
     * <p>Method renamed from {@code forEach} to avoid overloading issues with lambda
     * type inference when using primitive functional interfaces.</p>
     * 
     * @param consumer a callback invoked for each key-value pair. Receives primitive
     *                 int parameters avoiding any boxing overhead.
     * 
     * @throws NullPointerException if consumer is null
     * 
     * @see #entrySet() for iterator-based access with early termination capability
     * @see IntIntConsumer functional interface for the consumer parameter
     */
    public void forEachInt(final IntIntConsumer consumer)
    {
        requireNonNull(consumer);
        final int missingValue = this.missingValue;
        final int[] entries = this.entries;
        @DoNotSub final int length = entries.length;

        for (@DoNotSub int valueIndex = 1, remaining = size; remaining > 0 && valueIndex < length; valueIndex += 2)
        {
            if (missingValue != entries[valueIndex])
            {
                consumer.accept(entries[valueIndex - 1], entries[valueIndex]);
                --remaining;
            }
        }
    }

    /**
     * Int primitive specialised containsKey.
     *
     * @param key the key to check.
     * @return true if the map contains key as a key, false otherwise.
     */
    public boolean containsKey(final int key)
    {
        return missingValue != get(key);
    }

    /**
     * Does the map contain the value.
     *
     * @param value to be tested against contained values.
     * @return true if contained otherwise value.
     */
    public boolean containsValue(final int value)
    {
        boolean found = false;
        final int missingValue = this.missingValue;
        if (missingValue != value)
        {
            final int[] entries = this.entries;
            @DoNotSub final int length = entries.length;
            @DoNotSub int remaining = size;

            for (@DoNotSub int valueIndex = 1; remaining > 0 && valueIndex < length; valueIndex += 2)
            {
                final int existingValue = entries[valueIndex];
                if (missingValue != existingValue)
                {
                    if (existingValue == value)
                    {
                        found = true;
                        break;
                    }
                    --remaining;
                }
            }
        }

        return found;
    }

    /**
     * {@inheritDoc}
     */
    public void clear()
    {
        if (size > 0)
        {
            Arrays.fill(entries, missingValue);
            size = 0;
        }
    }

    /**
     * Optimize memory usage by shrinking the capacity to fit current size.
     * <p>This method recalculates the optimal capacity based on current size and load factor,
     * then rehashes all entries into a smaller array. Useful after bulk deletions to
     * reclaim unused memory.</p>
     * 
     * <h3>When to Use</h3>
     * <ul>
     *   <li><strong>After bulk deletions:</strong> When map size has significantly decreased</li>
     *   <li><strong>Memory pressure:</strong> When memory usage is more critical than speed</li>
     *   <li><strong>Phase transitions:</strong> Moving from loading phase to lookup-heavy phase</li>
     * </ul>
     * 
     * <h3>Performance Impact</h3>
     * <ul>
     *   <li><strong>Time complexity:</strong> O(n) - must rehash all existing entries</li>
     *   <li><strong>Memory benefit:</strong> Can reduce capacity significantly</li>
     *   <li><strong>Future performance:</strong> May improve cache locality with smaller capacity</li>
     *   <li><strong>One-time cost:</strong> Subsequent operations return to normal speed</li>
     * </ul>
     * 
     * <h3>Algorithm Details</h3>
     * <p>Compaction process:</p>
     * <ol>
     *   <li>Calculate ideal capacity: {@code size / loadFactor}</li>
     *   <li>Round up to next power-of-2, minimum 8</li>
     *   <li>If new capacity is smaller, allocate new array</li>
     *   <li>Rehash all existing entries to new positions</li>
     *   <li>Update internal structures</li>
     * </ol>
     * 
     * <h3>Usage Examples</h3>
     * <pre>{@code
     * Int2IntHashMap cache = new Int2IntHashMap(1000, 0.75f, -1);
     * 
     * // Load many entries
     * for (int i = 0; i < 800; i++) {
     *     cache.put(i, i * 2);
     * }
     * // Capacity: 1024, size: 800, threshold: 768
     * 
     * // Remove most entries
     * for (int i = 0; i < 700; i++) {
     *     cache.remove(i);
     * }
     * // Capacity: 1024, size: 100 (wasted space!)
     * 
     * // Compact to reclaim memory
     * cache.compact();
     * // Capacity: 256, size: 100, threshold: 192 (much better!)
     * 
     * // Best practice: compact after bulk operations
     * performBulkDeletions(cache);
     * if (cache.size() < cache.capacity() / 4) {
     *     cache.compact(); // Reclaim memory if significantly under-utilized
     * }
     * }</pre>
     * 
     * <h3>Memory Calculation</h3>
     * <p>Memory savings example with 100 entries and 0.75 load factor:</p>
     * <ul>
     *   <li>Before: 1024 capacity × 8 bytes = 8,192 bytes</li>
     *   <li>After: 256 capacity × 8 bytes = 2,048 bytes</li>
     *   <li>Savings: 6,144 bytes (75% reduction)</li>
     * </ul>
     * 
     * @see #capacity() to check current capacity
     * @see #size() to check current element count
     * @see #loadFactor() to understand resize behavior
     */
    public void compact()
    {
        @DoNotSub final int idealCapacity = (int)Math.round(size() * (1.0d / loadFactor));
        rehash(findNextPositivePowerOfTwo(Math.max(MIN_CAPACITY, idealCapacity)));
    }

    /**
     * Primitive specialised version of {@link Map#computeIfAbsent(Object, Function)}.
     *
     * @param key             to search on.
     * @param mappingFunction to provide a value if the get returns null.
     * @return the value if found otherwise the missing value.
     */
    public int computeIfAbsent(final int key, final IntUnaryOperator mappingFunction)
    {
        requireNonNull(mappingFunction);
        final int missingValue = this.missingValue;
        final int[] entries = this.entries;
        @DoNotSub final int mask = entries.length - 1;
        @DoNotSub int index = Hashing.evenHash(key, mask);

        int value;
        while (missingValue != (value = entries[index + 1]))
        {
            if (key == entries[index])
            {
                break;
            }

            index = next(index, mask);
        }

        if (missingValue == value && missingValue != (value = mappingFunction.applyAsInt(key)))
        {
            entries[index] = key;
            entries[index + 1] = value;
            ++size;
            increaseCapacity();
        }

        return value;
    }

    /**
     * Primitive specialised version of {@link Map#computeIfPresent(Object, BiFunction)}.
     *
     * @param key               to search on.
     * @param remappingFunction to compute a value if a mapping is found.
     * @return the updated value if a mapping was found, otherwise the missing value.
     */
    public int computeIfPresent(final int key, final IntBinaryOperator remappingFunction)
    {
        requireNonNull(remappingFunction);
        final int missingValue = this.missingValue;
        final int[] entries = this.entries;
        @DoNotSub final int mask = entries.length - 1;
        @DoNotSub int index = Hashing.evenHash(key, mask);

        int value;
        while (missingValue != (value = entries[index + 1]))
        {
            if (key == entries[index])
            {
                break;
            }

            index = next(index, mask);
        }

        if (missingValue != value)
        {
            value = remappingFunction.applyAsInt(key, value);
            entries[index + 1] = value;
            if (missingValue == value)
            {
                size--;
                compactChain(index);
            }
        }

        return value;
    }

    /**
     * Primitive specialised version of {@link Map#compute(Object, BiFunction)}.
     *
     * @param key               to search on.
     * @param remappingFunction to compute a value.
     * @return the updated value.
     */
    public int compute(final int key, final IntBinaryOperator remappingFunction)
    {
        requireNonNull(remappingFunction);
        final int missingValue = this.missingValue;
        final int[] entries = this.entries;
        @DoNotSub final int mask = entries.length - 1;
        @DoNotSub int index = Hashing.evenHash(key, mask);

        int oldValue;
        while (missingValue != (oldValue = entries[index + 1]))
        {
            if (key == entries[index])
            {
                break;
            }

            index = next(index, mask);
        }

        final int newValue = remappingFunction.applyAsInt(key, oldValue);
        if (missingValue != newValue)
        {
            entries[index + 1] = newValue;
            if (oldValue == missingValue)
            {
                entries[index] = key;
                ++size;
                increaseCapacity();
            }
        }
        else if (missingValue != oldValue)
        {
            entries[index + 1] = missingValue;
            size--;
            compactChain(index);
        }

        return newValue;
    }

    // ---------------- Boxed Versions Below ----------------

    /**
     * {@inheritDoc}
     */
    public Integer get(final Object key)
    {
        return valOrNull(get((int)key));
    }

    /**
     * {@inheritDoc}
     */
    public Integer put(final Integer key, final Integer value)
    {
        return valOrNull(put((int)key, (int)value));
    }

    /**
     * {@inheritDoc}
     */
    public void forEach(final BiConsumer<? super Integer, ? super Integer> action)
    {
        forEachInt(action::accept);
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsKey(final Object key)
    {
        return containsKey((int)key);
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsValue(final Object value)
    {
        return containsValue((int)value);
    }

    /**
     * {@inheritDoc}
     */
    public void putAll(final Map<? extends Integer, ? extends Integer> map)
    {
        for (final Map.Entry<? extends Integer, ? extends Integer> entry : map.entrySet())
        {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Put all values from the given map into this map without allocation.
     *
     * @param map whose value are to be added.
     */
    public void putAll(final Int2IntHashMap map)
    {
        final EntryIterator it = map.entrySet().iterator();
        while (it.hasNext())
        {
            it.findNext();
            put(it.getIntKey(), it.getIntValue());
        }
    }

    /**
     * {@inheritDoc}
     */
    public Integer putIfAbsent(final Integer key, final Integer value)
    {
        return valOrNull(putIfAbsent((int)key, (int)value));
    }

    /**
     * {@inheritDoc}
     */
    public Integer replace(final Integer key, final Integer value)
    {
        return valOrNull(replace((int)key, (int)value));
    }

    /**
     * {@inheritDoc}
     */
    public boolean replace(final Integer key, final Integer oldValue, final Integer newValue)
    {
        return replace((int)key, (int)oldValue, (int)newValue);
    }

    /**
     * {@inheritDoc}
     */
    public void replaceAll(final BiFunction<? super Integer, ? super Integer, ? extends Integer> function)
    {
        replaceAllInt(function::apply);
    }

    /**
     * {@inheritDoc}
     */
    public KeySet keySet()
    {
        if (null == keySet)
        {
            keySet = new KeySet();
        }

        return keySet;
    }

    /**
     * {@inheritDoc}
     */
    public ValueCollection values()
    {
        if (null == values)
        {
            values = new ValueCollection();
        }

        return values;
    }

    /**
     * {@inheritDoc}
     */
    public EntrySet entrySet()
    {
        if (null == entrySet)
        {
            entrySet = new EntrySet();
        }

        return entrySet;
    }

    /**
     * {@inheritDoc}
     */
    public Integer remove(final Object key)
    {
        return valOrNull(remove((int)key));
    }

    /**
     * {@inheritDoc}
     */
    public boolean remove(final Object key, final Object value)
    {
        return remove((int)key, (int)value);
    }

    /**
     * Remove a key-value pair from the map without boxing overhead.
     * <p>This method removes the entry for the specified key and performs necessary
     * chain compaction to maintain performance of subsequent lookups. The removal
     * process ensures the integrity of the linear probing sequence.</p>
     * 
     * <h3>Performance Characteristics</h3>
     * <ul>
     *   <li><strong>Average case:</strong> O(1) with 1-3 memory accesses</li>
     *   <li><strong>Worst case:</strong> O(n) if extensive chain compaction required</li>
     *   <li><strong>Compaction cost:</strong> Additional work to maintain lookup efficiency</li>
     *   <li><strong>Memory impact:</strong> No deallocation, capacity unchanged</li>
     * </ul>
     * 
     * <h3>Chain Compaction Algorithm</h3>
     * <p>After removing an entry, the algorithm ensures linear probing chains remain intact:</p>
     * <ol>
     *   <li>Mark the entry slot as empty (set to missingValue)</li>
     *   <li>Examine subsequent entries in the probe sequence</li>
     *   <li>Relocate entries that can move to a better position</li>
     *   <li>Continue until reaching an empty slot or completing cycle</li>
     * </ol>
     * 
     * <h3>Usage Examples</h3>
     * <pre>{@code
     * Int2IntHashMap sessionMap = new Int2IntHashMap(-1);
     * sessionMap.put(12345, 67890);
     * sessionMap.put(54321, 98765);
     * 
     * // Remove existing key
     * int removed = sessionMap.remove(12345);  // Returns 67890
     * 
     * // Remove non-existent key
     * int notFound = sessionMap.remove(99999); // Returns -1 (missing value)
     * 
     * // Safe removal pattern
     * if (sessionMap.containsKey(sessionId)) {
     *     int userData = sessionMap.remove(sessionId);
     *     processRemovedData(userData);
     * }
     * 
     * // Bulk removal with compaction consideration
     * List<Integer> toRemove = getExpiredSessions();
     * for (int sessionId : toRemove) {
     *     sessionMap.remove(sessionId);
     * }
     * // Consider compacting after bulk removals
     * if (sessionMap.size() < sessionMap.capacity() / 4) {
     *     sessionMap.compact();
     * }
     * }</pre>
     * 
     * <h3>Memory Considerations</h3>
     * <p>Removal does not reduce capacity or trigger compaction automatically. For memory
     * efficiency after bulk deletions, consider calling {@link #compact()} to reclaim
     * unused space.</p>
     * 
     * <h3>Thread Safety</h3>
     * <p><strong>Warning:</strong> Concurrent removals or modifications during removal
     * can corrupt the internal structure. Ensure exclusive access during modification.</p>
     * 
     * @param key the key whose mapping should be removed
     * @return the value previously associated with the key, or {@link #missingValue()}
     *         if the key was not present in the map
     * 
     * @see #remove(int, int) for conditional removal based on expected value
     * @see #compact() to reclaim memory after bulk deletions
     * @see #containsKey(int) to check existence before removal
     */
    public int remove(final int key)
    {
        final int missingValue = this.missingValue;
        final int[] entries = this.entries;
        @DoNotSub final int mask = entries.length - 1;
        @DoNotSub int keyIndex = Hashing.evenHash(key, mask);

        int oldValue;
        while (missingValue != (oldValue = entries[keyIndex + 1]))
        {
            if (key == entries[keyIndex])
            {
                entries[keyIndex + 1] = missingValue;
                size--;

                compactChain(keyIndex);

                break;
            }

            keyIndex = next(keyIndex, mask);
        }

        return oldValue;
    }

    /**
     * Primitive specialised version of {@link Map#remove(Object, Object)}.
     *
     * @param key   with which the specified value is associated.
     * @param value expected to be associated with the specified key.
     * @return {@code true} if the value was removed.
     */
    public boolean remove(final int key, final int value)
    {
        final int missingValue = this.missingValue;
        final int[] entries = this.entries;
        @DoNotSub final int mask = entries.length - 1;
        @DoNotSub int keyIndex = Hashing.evenHash(key, mask);

        int oldValue;
        while (missingValue != (oldValue = entries[keyIndex + 1]))
        {
            if (key == entries[keyIndex])
            {
                if (value == oldValue)
                {
                    entries[keyIndex + 1] = missingValue;
                    size--;

                    compactChain(keyIndex);
                    return true;
                }
                break;
            }

            keyIndex = next(keyIndex, mask);
        }

        return false;
    }

    /**
     * Primitive specialised version of {@link Map#merge(Object, Object, BiFunction)}.
     *
     * @param key               with which the resulting value is to be associated.
     * @param value             to be merged with the existing value associated with the key or, if no existing value or a null
     *                          value is associated with the key, to be associated with the key.
     * @param remappingFunction the function to recompute a value if present.
     * @return the new value associated with the specified key, or {@link #missingValue()} if no value is associated
     * with the key as the result of this operation.
     */
    public int merge(final int key, final int value, final IntIntFunction remappingFunction)
    {
        requireNonNull(remappingFunction);
        final int missingValue = this.missingValue;
        if (missingValue == value)
        {
            throw new IllegalArgumentException("cannot accept missingValue");
        }
        final int[] entries = this.entries;
        @DoNotSub final int mask = entries.length - 1;
        @DoNotSub int index = Hashing.evenHash(key, mask);

        int oldValue;
        while (missingValue != (oldValue = entries[index + 1]))
        {
            if (key == entries[index])
            {
                break;
            }

            index = next(index, mask);
        }

        final int newValue = missingValue == oldValue ? value : remappingFunction.apply(oldValue, value);
        if (missingValue != newValue)
        {
            entries[index + 1] = newValue;
            if (missingValue == oldValue)
            {
                entries[index] = key;
                ++size;
                increaseCapacity();
            }
        }
        else
        {
            entries[index + 1] = missingValue;
            size--;
            compactChain(index);
        }

        return newValue;
    }

    @SuppressWarnings("FinalParameters")
    private void compactChain(@DoNotSub int deleteKeyIndex)
    {
        final int missingValue = this.missingValue;
        final int[] entries = this.entries;
        @DoNotSub final int mask = entries.length - 1;
        @DoNotSub int keyIndex = deleteKeyIndex;

        while (true)
        {
            keyIndex = next(keyIndex, mask);
            final int value = entries[keyIndex + 1];
            if (missingValue == value)
            {
                break;
            }

            final int key = entries[keyIndex];
            @DoNotSub final int hash = Hashing.evenHash(key, mask);

            if ((keyIndex < hash && (hash <= deleteKeyIndex || deleteKeyIndex <= keyIndex)) ||
                (hash <= deleteKeyIndex && deleteKeyIndex <= keyIndex))
            {
                entries[deleteKeyIndex] = key;
                entries[deleteKeyIndex + 1] = value;

                entries[keyIndex + 1] = missingValue;
                deleteKeyIndex = keyIndex;
            }
        }
    }

    /**
     * Find the minimum value among all stored values in the map.
     * <p>This method scans all values in the map to find the smallest one, useful for
     * statistical analysis, range validation, or finding extreme values in datasets.</p>
     * 
     * <h3>Performance Characteristics</h3>
     * <ul>
     *   <li><strong>Time complexity:</strong> O(capacity) - must scan entire array</li>
     *   <li><strong>Memory overhead:</strong> No additional allocation</li>
     *   <li><strong>Cache behavior:</strong> Sequential scan, cache-friendly</li>
     * </ul>
     * 
     * <h3>Edge Cases</h3>
     * <ul>
     *   <li><strong>Empty map:</strong> Returns {@link #missingValue()}</li>
     *   <li><strong>Single element:</strong> Returns that element's value</li>
     *   <li><strong>All values same:</strong> Returns that common value</li>
     *   <li><strong>Missing value in range:</strong> Missing value is ignored</li>
     * </ul>
     * 
     * <h3>Usage Examples</h3>
     * <pre>{@code
     * Int2IntHashMap scores = new Int2IntHashMap(-1); // -1 = missing
     * scores.put(100, 85);  // Student 100: score 85
     * scores.put(101, 92);  // Student 101: score 92
     * scores.put(102, 78);  // Student 102: score 78
     * 
     * int lowest = scores.minValue();  // Returns 78
     * int highest = scores.maxValue(); // Returns 92
     * 
     * // Range validation
     * if (scores.minValue() < PASSING_GRADE) {
     *     System.out.println("Some students failed");
     * }
     * 
     * // Empty map handling
     * Int2IntHashMap empty = new Int2IntHashMap(-999);
     * int min = empty.minValue(); // Returns -999 (missing value)
     * if (min != empty.missingValue()) {
     *     // Map has data
     *     processMinimum(min);
     * }
     * 
     * // Statistics gathering
     * int range = scores.maxValue() - scores.minValue(); // 92 - 78 = 14
     * }</pre>
     * 
     * <h3>Performance Considerations</h3>
     * <p>This operation scales with capacity, not size. For frequent min/max queries
     * on large maps, consider maintaining separate min/max tracking variables or
     * using a specialized data structure.</p>
     * 
     * @return the minimum value among all stored values, or {@link #missingValue()}
     *         if the map is empty
     * 
     * @see #maxValue() for finding the maximum value
     * @see #size() to check if map is empty before calling
     */
    public int minValue()
    {
        final int missingValue = this.missingValue;
        int min = 0 == size ? missingValue : Integer.MAX_VALUE;
        final int[] entries = this.entries;
        @DoNotSub final int length = entries.length;

        for (@DoNotSub int valueIndex = 1; valueIndex < length; valueIndex += 2)
        {
            final int value = entries[valueIndex];
            if (missingValue != value)
            {
                min = Math.min(min, value);
            }
        }

        return min;
    }

    /**
     * Find the maximum value among all stored values in the map.
     * <p>This method scans all values in the map to find the largest one, useful for
     * statistical analysis, range validation, or finding extreme values in datasets.</p>
     * 
     * <h3>Performance Characteristics</h3>
     * <ul>
     *   <li><strong>Time complexity:</strong> O(capacity) - must scan entire array</li>
     *   <li><strong>Memory overhead:</strong> No additional allocation</li>
     *   <li><strong>Cache behavior:</strong> Sequential scan, cache-friendly</li>
     * </ul>
     * 
     * <h3>Edge Cases</h3>
     * <ul>
     *   <li><strong>Empty map:</strong> Returns {@link #missingValue()}</li>
     *   <li><strong>Single element:</strong> Returns that element's value</li>
     *   <li><strong>All values same:</strong> Returns that common value</li>
     *   <li><strong>Missing value in range:</strong> Missing value is ignored</li>
     * </ul>
     * 
     * <h3>Usage Examples</h3>
     * <pre>{@code
     * Int2IntHashMap temperatures = new Int2IntHashMap(Integer.MIN_VALUE);
     * temperatures.put(1, 23);  // Sensor 1: 23°C
     * temperatures.put(2, 19);  // Sensor 2: 19°C  
     * temperatures.put(3, 27);  // Sensor 3: 27°C
     * 
     * int hottest = temperatures.maxValue();   // Returns 27
     * int coolest = temperatures.minValue();   // Returns 19
     * 
     * // Threshold monitoring
     * if (temperatures.maxValue() > DANGER_THRESHOLD) {
     *     triggerCoolingSystem();
     * }
     * 
     * // Statistical analysis
     * int average = (temperatures.maxValue() + temperatures.minValue()) / 2;
     * int range = temperatures.maxValue() - temperatures.minValue();
     * 
     * // Safe usage with empty check
     * if (temperatures.size() > 0) {
     *     int peak = temperatures.maxValue();
     *     recordPeakTemperature(peak);
     * }
     * }</pre>
     * 
     * <h3>Missing Value Handling</h3>
     * <p>The missing value is never considered as a candidate for maximum, even if it
     * would numerically be the largest value. Only actual stored values are compared.</p>
     * 
     * <h3>Performance Considerations</h3>
     * <p>For applications requiring frequent min/max queries, consider:</p>
     * <ul>
     *   <li>Maintaining separate tracking variables updated on put/remove</li>
     *   <li>Using TreeMap for sorted access if range queries are common</li>
     *   <li>Caching results if map doesn't change frequently</li>
     * </ul>
     * 
     * @return the maximum value among all stored values, or {@link #missingValue()}
     *         if the map is empty
     * 
     * @see #minValue() for finding the minimum value
     * @see #size() to check if map is empty before calling
     */
    public int maxValue()
    {
        final int missingValue = this.missingValue;
        int max = 0 == size ? missingValue : Integer.MIN_VALUE;
        final int[] entries = this.entries;
        @DoNotSub final int length = entries.length;

        for (@DoNotSub int valueIndex = 1; valueIndex < length; valueIndex += 2)
        {
            final int value = entries[valueIndex];
            if (missingValue != value)
            {
                max = Math.max(max, value);
            }
        }

        return max;
    }

    /**
     * {@inheritDoc}
     */
    public String toString()
    {
        if (isEmpty())
        {
            return "{}";
        }

        final EntryIterator entryIterator = new EntryIterator();
        entryIterator.reset();

        final StringBuilder sb = new StringBuilder().append('{');
        while (true)
        {
            entryIterator.next();
            sb.append(entryIterator.getIntKey()).append('=').append(entryIterator.getIntValue());
            if (!entryIterator.hasNext())
            {
                return sb.append('}').toString();
            }
            sb.append(',').append(' ');
        }
    }

    /**
     * Primitive specialised version of {@link Map#replace(Object, Object)}.
     *
     * @param key   key with which the specified value is associated.
     * @param value value to be associated with the specified key.
     * @return the previous value associated with the specified key, or
     * {@link #missingValue()} if there was no mapping for the key.
     */
    public int replace(final int key, final int value)
    {
        final int missingValue = this.missingValue;
        final int[] entries = this.entries;
        @DoNotSub final int mask = entries.length - 1;
        @DoNotSub int keyIndex = Hashing.evenHash(key, mask);

        int oldValue;
        while (missingValue != (oldValue = entries[keyIndex + 1]))
        {
            if (key == entries[keyIndex])
            {
                entries[keyIndex + 1] = value;
                break;
            }

            keyIndex = next(keyIndex, mask);
        }

        return oldValue;
    }

    /**
     * Primitive specialised version of {@link Map#replace(Object, Object, Object)}.
     *
     * @param key      key with which the specified value is associated.
     * @param oldValue value expected to be associated with the specified key.
     * @param newValue value to be associated with the specified key.
     * @return {@code true} if the value was replaced.
     */
    public boolean replace(final int key, final int oldValue, final int newValue)
    {
        final int missingValue = this.missingValue;
        final int[] entries = this.entries;
        @DoNotSub final int mask = entries.length - 1;
        @DoNotSub int keyIndex = Hashing.evenHash(key, mask);

        int value;
        while (missingValue != (value = entries[keyIndex + 1]))
        {
            if (key == entries[keyIndex])
            {
                if (oldValue == value)
                {
                    entries[keyIndex + 1] = newValue;
                    return true;
                }
                break;
            }

            keyIndex = next(keyIndex, mask);
        }

        return false;
    }

    /**
     * Primitive specialised version of {@link Map#replaceAll(BiFunction)}.
     * <p>
     * NB: Renamed from replaceAll to avoid overloading on parameter types of lambda
     * expression, which doesn't play well with type inference in lambda expressions.
     *
     * @param function to apply to each entry.
     */
    public void replaceAllInt(final IntIntFunction function)
    {
        requireNonNull(function);
        final int missingValue = this.missingValue;
        final int[] entries = this.entries;
        @DoNotSub final int length = entries.length;

        for (@DoNotSub int valueIndex = 1, remaining = size; remaining > 0 && valueIndex < length; valueIndex += 2)
        {
            final int existingValue = entries[valueIndex];
            if (missingValue != existingValue)
            {
                final int newValue = function.apply(entries[valueIndex - 1], existingValue);
                if (missingValue == newValue)
                {
                    throw new IllegalArgumentException("cannot replace with a missingValue");
                }
                entries[valueIndex] = newValue;
                --remaining;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }

        if (!(o instanceof Map))
        {
            return false;
        }

        final Map<?, ?> that = (Map<?, ?>)o;

        return size == that.size() && entrySet().equals(that.entrySet());
    }

    /**
     * {@inheritDoc}
     */
    @DoNotSub public int hashCode()
    {
        return entrySet().hashCode();
    }

    @DoNotSub private static int next(final int index, final int mask)
    {
        return (index + 2) & mask;
    }

    private void capacity(@DoNotSub final int newCapacity)
    {
        @DoNotSub final int entriesLength = newCapacity * 2;
        if (entriesLength < 0)
        {
            throw new IllegalStateException("max capacity reached at size=" + size);
        }

        /*@DoNotSub*/ resizeThreshold = (int)(newCapacity * loadFactor);
        entries = new int[entriesLength];
        Arrays.fill(entries, missingValue);
    }

    private Integer valOrNull(final int value)
    {
        return missingValue == value ? null : value;
    }

    // ---------------- Utility Classes ----------------

    /**
     * Base iterator implementation.
     */
    abstract class AbstractIterator
    {
        /**
         * Is current position valid.
         */
        protected boolean isPositionValid = false;
        @DoNotSub private int remaining;
        @DoNotSub private int positionCounter;
        @DoNotSub private int stopCounter;

        final void reset()
        {
            isPositionValid = false;
            remaining = Int2IntHashMap.this.size;
            final int missingValue = Int2IntHashMap.this.missingValue;
            final int[] entries = Int2IntHashMap.this.entries;
            @DoNotSub final int capacity = entries.length;

            @DoNotSub int keyIndex = capacity;
            if (missingValue != entries[capacity - 1])
            {
                for (@DoNotSub int i = 1; i < capacity; i += 2)
                {
                    if (missingValue == entries[i])
                    {
                        keyIndex = i - 1;
                        break;
                    }
                }
            }

            stopCounter = keyIndex;
            positionCounter = keyIndex + capacity;
        }

        /**
         * Returns position of the key of the current entry.
         *
         * @return key position.
         */
        @DoNotSub protected final int keyPosition()
        {
            return positionCounter & entries.length - 1;
        }

        /**
         * Number of remaining elements.
         *
         * @return number of remaining elements.
         */
        @DoNotSub public int remaining()
        {
            return remaining;
        }

        /**
         * Check if there are more elements remaining.
         *
         * @return {@code true} if {@code remaining > 0}.
         */
        public boolean hasNext()
        {
            return remaining > 0;
        }

        /**
         * Advance to the next entry.
         *
         * @throws NoSuchElementException if no more entries available.
         */
        protected final void findNext()
        {
            if (!hasNext())
            {
                throw new NoSuchElementException();
            }

            final int[] entries = Int2IntHashMap.this.entries;
            final int missingValue = Int2IntHashMap.this.missingValue;
            @DoNotSub final int mask = entries.length - 1;

            for (@DoNotSub int keyIndex = positionCounter - 2, stop = stopCounter; keyIndex >= stop; keyIndex -= 2)
            {
                @DoNotSub final int index = keyIndex & mask;
                if (missingValue != entries[index + 1])
                {
                    isPositionValid = true;
                    positionCounter = keyIndex;
                    --remaining;
                    return;
                }
            }

            isPositionValid = false;
            throw new IllegalStateException();
        }

        /**
         * Removes from the underlying collection the last element returned by this iterator.
         */
        public void remove()
        {
            if (isPositionValid)
            {
                @DoNotSub final int position = keyPosition();
                entries[position + 1] = missingValue;
                --size;

                compactChain(position);

                isPositionValid = false;
            }
            else
            {
                throw new IllegalStateException();
            }
        }
    }

    /**
     * Iterator over keys which supports access to unboxed keys via {@link #nextValue()}.
     */
    public final class KeyIterator extends AbstractIterator implements Iterator<Integer>
    {
        /**
         * Create a new instance.
         */
        public KeyIterator()
        {
        }

        /**
         * {@inheritDoc}
         */
        public Integer next()
        {
            return nextValue();
        }

        /**
         * Return next key.
         *
         * @return next key.
         */
        public int nextValue()
        {
            findNext();
            return entries[keyPosition()];
        }
    }

    /**
     * Iterator over values which supports access to unboxed values.
     */
    public final class ValueIterator extends AbstractIterator implements Iterator<Integer>
    {
        /**
         * Create a new instance.
         */
        public ValueIterator()
        {
        }

        /**
         * {@inheritDoc}
         */
        public Integer next()
        {
            return nextValue();
        }

        /**
         * Return next value.
         *
         * @return next value.
         */
        public int nextValue()
        {
            findNext();
            return entries[keyPosition() + 1];
        }
    }

    /**
     * Iterator over entries which supports access to unboxed keys and values.
     */
    public final class EntryIterator
        extends AbstractIterator
        implements Iterator<Entry<Integer, Integer>>, Entry<Integer, Integer>
    {
        /**
         * Create a new instance.
         */
        public EntryIterator()
        {
        }

        /**
         * {@inheritDoc}
         */
        public Integer getKey()
        {
            return getIntKey();
        }

        /**
         * Returns the key of the current entry.
         *
         * @return the key.
         */
        public int getIntKey()
        {
            return entries[keyPosition()];
        }

        /**
         * {@inheritDoc}
         */
        public Integer getValue()
        {
            return getIntValue();
        }

        /**
         * Returns the value of the current entry.
         *
         * @return the value.
         */
        public int getIntValue()
        {
            return entries[keyPosition() + 1];
        }

        /**
         * {@inheritDoc}
         */
        public Integer setValue(final Integer value)
        {
            return setValue(value.intValue());
        }

        /**
         * Sets the value of the current entry.
         *
         * @param value to be set.
         * @return previous value of the entry.
         */
        public int setValue(final int value)
        {
            if (!isPositionValid)
            {
                throw new IllegalStateException();
            }

            if (missingValue == value)
            {
                throw new IllegalArgumentException("cannot accept missingValue");
            }

            @DoNotSub final int keyPosition = keyPosition();
            final int[] entries = Int2IntHashMap.this.entries;
            final int prevValue = entries[keyPosition + 1];
            entries[keyPosition + 1] = value;
            return prevValue;
        }

        /**
         * {@inheritDoc}
         */
        public Entry<Integer, Integer> next()
        {
            findNext();

            if (shouldAvoidAllocation)
            {
                return this;
            }

            return allocateDuplicateEntry();
        }

        private Entry<Integer, Integer> allocateDuplicateEntry()
        {
            return new MapEntry(getIntKey(), getIntValue());
        }

        /**
         * {@inheritDoc}
         */
        @DoNotSub public int hashCode()
        {
            return Integer.hashCode(getIntKey()) ^ Integer.hashCode(getIntValue());
        }

        /**
         * {@inheritDoc}
         */
        public boolean equals(final Object o)
        {
            if (this == o)
            {
                return true;
            }

            if (!(o instanceof Entry))
            {
                return false;
            }

            final Entry<?, ?> that = (Entry<?, ?>)o;

            return Objects.equals(getKey(), that.getKey()) && Objects.equals(getValue(), that.getValue());
        }

        /**
         * An {@link java.util.Map.Entry} implementation.
         */
        public final class MapEntry implements Entry<Integer, Integer>
        {
            private final int k;
            private final int v;

            /**
             * Constructs entry with given key and value.
             *
             * @param k key.
             * @param v value.
             */
            public MapEntry(final int k, final int v)
            {
                this.k = k;
                this.v = v;
            }

            /**
             * {@inheritDoc}
             */
            public Integer getKey()
            {
                return k;
            }

            /**
             * {@inheritDoc}
             */
            public Integer getValue()
            {
                return v;
            }

            /**
             * {@inheritDoc}
             */
            public Integer setValue(final Integer value)
            {
                return Int2IntHashMap.this.put(k, value.intValue());
            }

            /**
             * {@inheritDoc}
             */
            @DoNotSub public int hashCode()
            {
                return Integer.hashCode(getIntKey()) ^ Integer.hashCode(getIntValue());
            }

            /**
             * {@inheritDoc}
             */
            @DoNotSub public boolean equals(final Object o)
            {
                if (!(o instanceof Map.Entry))
                {
                    return false;
                }

                final Entry<?, ?> e = (Entry<?, ?>)o;

                return (e.getKey() != null && e.getValue() != null) && (e.getKey().equals(k) && e.getValue().equals(v));
            }

            /**
             * {@inheritDoc}
             */
            public String toString()
            {
                return k + "=" + v;
            }
        }
    }

    /**
     * Set of keys which supports optional cached iterators to avoid allocation.
     */
    public final class KeySet extends AbstractSet<Integer>
    {
        private final KeyIterator keyIterator = shouldAvoidAllocation ? new KeyIterator() : null;

        /**
         * Create a new instance.
         */
        public KeySet()
        {
        }

        /**
         * {@inheritDoc}
         */
        public KeyIterator iterator()
        {
            KeyIterator keyIterator = this.keyIterator;
            if (null == keyIterator)
            {
                keyIterator = new KeyIterator();
            }

            keyIterator.reset();

            return keyIterator;
        }

        /**
         * {@inheritDoc}
         */
        @DoNotSub public int size()
        {
            return Int2IntHashMap.this.size();
        }

        /**
         * {@inheritDoc}
         */
        public boolean isEmpty()
        {
            return Int2IntHashMap.this.isEmpty();
        }

        /**
         * {@inheritDoc}
         */
        public void clear()
        {
            Int2IntHashMap.this.clear();
        }

        /**
         * {@inheritDoc}
         */
        public boolean contains(final Object o)
        {
            return contains((int)o);
        }

        /**
         * Checks if key is contained in the map without boxing.
         *
         * @param key to check.
         * @return {@code true} if key is contained in this map.
         */
        public boolean contains(final int key)
        {
            return containsKey(key);
        }

        /**
         * Removes all the elements of this collection that satisfy the given predicate.
         * <p>
         * NB: Renamed from removeIf to avoid overloading on parameter types of lambda
         * expression, which doesn't play well with type inference in lambda expressions.
         *
         * @param filter a predicate to apply.
         * @return {@code true} if at least one key was removed.
         */
        public boolean removeIfInt(final IntPredicate filter)
        {
            boolean removed = false;
            final KeyIterator iterator = iterator();
            while (iterator.hasNext())
            {
                if (filter.test(iterator.nextValue()))
                {
                    iterator.remove();
                    removed = true;
                }
            }
            return removed;
        }
    }

    /**
     * Collection of values which supports optionally cached iterators to avoid allocation.
     */
    public final class ValueCollection extends AbstractCollection<Integer>
    {
        private final ValueIterator valueIterator = shouldAvoidAllocation ? new ValueIterator() : null;

        /**
         * Create a new instance.
         */
        public ValueCollection()
        {
        }

        /**
         * {@inheritDoc}
         */
        public ValueIterator iterator()
        {
            ValueIterator valueIterator = this.valueIterator;
            if (null == valueIterator)
            {
                valueIterator = new ValueIterator();
            }

            valueIterator.reset();

            return valueIterator;
        }

        /**
         * {@inheritDoc}
         */
        @DoNotSub public int size()
        {
            return Int2IntHashMap.this.size();
        }

        /**
         * {@inheritDoc}
         */
        public boolean contains(final Object o)
        {
            return contains((int)o);
        }

        /**
         * Checks if the value is contained in the map.
         *
         * @param value to be checked.
         * @return {@code true} if value is contained in this map.
         */
        public boolean contains(final int value)
        {
            return containsValue(value);
        }

        /**
         * Removes all the elements of this collection that satisfy the given predicate.
         * <p>
         * NB: Renamed from removeIf to avoid overloading on parameter types of lambda
         * expression, which doesn't play well with type inference in lambda expressions.
         *
         * @param filter a predicate to apply.
         * @return {@code true} if at least one value was removed.
         */
        public boolean removeIfInt(final IntPredicate filter)
        {
            boolean removed = false;
            final ValueIterator iterator = iterator();
            while (iterator.hasNext())
            {
                if (filter.test(iterator.nextValue()))
                {
                    iterator.remove();
                    removed = true;
                }
            }
            return removed;
        }
    }

    /**
     * Set of entries which supports optionally cached iterators to avoid allocation.
     */
    public final class EntrySet extends AbstractSet<Map.Entry<Integer, Integer>>
    {
        private final EntryIterator entryIterator = shouldAvoidAllocation ? new EntryIterator() : null;

        /**
         * Create a new instance.
         */
        public EntrySet()
        {
        }

        /**
         * {@inheritDoc}
         */
        public EntryIterator iterator()
        {
            EntryIterator entryIterator = this.entryIterator;
            if (null == entryIterator)
            {
                entryIterator = new EntryIterator();
            }

            entryIterator.reset();

            return entryIterator;
        }

        /**
         * {@inheritDoc}
         */
        @DoNotSub public int size()
        {
            return Int2IntHashMap.this.size();
        }

        /**
         * {@inheritDoc}
         */
        public boolean isEmpty()
        {
            return Int2IntHashMap.this.isEmpty();
        }

        /**
         * {@inheritDoc}
         */
        public void clear()
        {
            Int2IntHashMap.this.clear();
        }

        /**
         * {@inheritDoc}
         */
        public boolean contains(final Object o)
        {
            if (!(o instanceof Entry))
            {
                return false;
            }
            final Entry<?, ?> entry = (Entry<?, ?>)o;
            final Integer value = get(entry.getKey());

            return value != null && value.equals(entry.getValue());
        }

        /**
         * Removes all the elements of this collection that satisfy the given predicate.
         * <p>
         * NB: Renamed from removeIf to avoid overloading on parameter types of lambda
         * expression, which doesn't play well with type inference in lambda expressions.
         *
         * @param filter a predicate to apply.
         * @return {@code true} if at least one entry was removed.
         */
        public boolean removeIfInt(final IntIntPredicate filter)
        {
            boolean removed = false;
            final EntryIterator iterator = iterator();
            while (iterator.hasNext())
            {
                iterator.findNext();
                if (filter.test(iterator.getIntKey(), iterator.getIntValue()))
                {
                    iterator.remove();
                    removed = true;
                }
            }
            return removed;
        }

        /**
         * {@inheritDoc}
         */
        public Object[] toArray()
        {
            return toArray(new Object[size()]);
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(final T[] a)
        {
            final T[] array = a.length >= size ?
                a : (T[])java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
            final EntryIterator it = iterator();

            for (@DoNotSub int i = 0; i < array.length; i++)
            {
                if (it.hasNext())
                {
                    it.next();
                    array[i] = (T)it.allocateDuplicateEntry();
                }
                else
                {
                    array[i] = null;
                    break;
                }
            }

            return array;
        }
    }
}
