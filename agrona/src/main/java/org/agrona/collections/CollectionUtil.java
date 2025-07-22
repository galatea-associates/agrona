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

import org.agrona.BitUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Utility functions for collection objects providing performance-optimized operations
 * for high-throughput scenarios and validation methods for collection configuration.
 * <p>
 * This class contains static utility methods that complement the primitive collections
 * framework by providing common operations that avoid object allocation and optimize
 * cache locality for performance-critical applications.
 * <p>
 * <b>Performance Characteristics:</b>
 * <ul>
 * <li>All methods are designed to minimize object allocation</li>
 * <li>Validation methods provide fast parameter checking for collection configuration</li>
 * <li>Loop-based operations are optimized for {@link java.util.RandomAccess} implementations</li>
 * </ul>
 * <p>
 * <b>Thread Safety:</b> All methods in this class are stateless and thread-safe.
 * <p>
 * <b>Usage Example:</b>
 * <pre>{@code
 * // Validate collection configuration
 * CollectionUtil.validateLoadFactor(0.6f);
 * CollectionUtil.validatePositivePowerOfTwo(16);
 * 
 * // Efficient sum calculation without boxing
 * List<Order> orders = getOrders();
 * int totalQuantity = CollectionUtil.sum(orders, Order::getQuantity);
 * 
 * // Get or create pattern without intermediate allocation
 * Map<String, CounterSet> counters = new HashMap<>();
 * CounterSet counter = CollectionUtil.getOrDefault(counters, "key", k -> new CounterSet());
 * }</pre>
 * 
 * @see org.agrona.collections for primitive collection implementations
 * @since 1.0
 */
public final class CollectionUtil
{
    private CollectionUtil()
    {
    }

    /**
     * A getOrDefault operation that avoids object allocation when the supplier is non-capturing.
     * This method provides a compute-if-absent pattern that immediately stores the computed value
     * in the map, avoiding repeated computation for subsequent access.
     * <p>
     * <b>Performance Characteristics:</b>
     * <ul>
     * <li>Single map lookup operation</li>
     * <li>Zero allocation when supplier is non-capturing lambda or method reference</li>
     * <li>Immediate storage avoids recomputation on subsequent access</li>
     * </ul>
     * <p>
     * <b>Usage Examples:</b>
     * <pre>{@code
     * // Non-capturing lambda (zero allocation)
     * CounterMap counters = getOrDefault(cache, "metrics", k -> new CounterMap());
     * 
     * // Method reference (zero allocation)
     * List<String> items = getOrDefault(lists, userId, ArrayList::new);
     * 
     * // Capturing lambda (may allocate)
     * Config config = getOrDefault(configs, key, k -> new Config(defaultValue));
     * }</pre>
     * <p>
     * <b>Thread Safety:</b> This method is not atomic. External synchronization is required
     * for concurrent access to the same map.
     *
     * @param map      the map to perform the lookup on. Must not be {@code null}.
     * @param key      the key for the lookup. May be {@code null} if map supports null keys.
     * @param supplier function to create the default value if key is not found. Must not be {@code null}.
     * @param <K>      the type of the key
     * @param <V>      the type of the value
     * @return the existing value if found, or a new default value that has been added to the map.
     * @throws NullPointerException if map or supplier is {@code null}
     */
    public static <K, V> V getOrDefault(final Map<K, V> map, final K key, final Function<K, V> supplier)
    {
        V value = map.get(key);
        if (value == null)
        {
            value = supplier.apply(key);
            map.put(key, value);
        }

        return value;
    }

    /**
     * Computes the sum of integer values extracted from a list without object allocation.
     * This method uses indexed access to avoid iterator allocation, making it suitable for
     * performance-critical code paths where garbage collection pressure must be minimized.
     * <p>
     * <b>Performance Characteristics:</b>
     * <ul>
     * <li>O(n) time complexity where n is the list size</li>
     * <li>Zero allocation when used with {@link java.util.RandomAccess} implementations</li>
     * <li>Indexed access pattern optimizes for CPU cache locality</li>
     * <li>No intermediate boxing/unboxing operations</li>
     * </ul>
     * <p>
     * <b>Usage Examples:</b>
     * <pre>{@code
     * // Sum order quantities
     * List<Order> orders = getOrders();
     * int totalQuantity = sum(orders, Order::getQuantity);
     * 
     * // Sum string lengths
     * List<String> strings = Arrays.asList("hello", "world");
     * int totalLength = sum(strings, String::length);
     * 
     * // Sum with transformation
     * List<Product> products = getProducts();
     * int totalPrice = sum(products, p -> p.getPrice().intValue());
     * }</pre>
     * <p>
     * <b>Performance Note:</b> This method is optimized for {@link java.util.RandomAccess} 
     * implementations such as {@link java.util.ArrayList}. For linked structures like 
     * {@link java.util.LinkedList}, prefer using stream operations.
     * <p>
     * <b>Overflow Behavior:</b> This method does not check for integer overflow. Results
     * are undefined if the sum exceeds {@link Integer#MAX_VALUE}.
     *
     * @param values   the list of input values. Must not be {@code null}.
     * @param function function that maps each value to an int. Must not be {@code null}.
     * @param <V>      the type of values in the list
     * @return the sum of all int values returned by the function for each list element.
     *         Returns 0 for an empty list.
     * @throws NullPointerException if values or function is {@code null}
     */
    public static <V> int sum(final List<V> values, final ToIntFunction<V> function)
    {
        int total = 0;

        final int size = values.size();
        for (int i = 0; i < size; i++)
        {
            final V value = values.get(i);
            total += function.applyAsInt(value);
        }

        return total;
    }

    /**
     * Validates that a load factor is within the acceptable range of 0.1 to 0.9 for hash-based collections.
     * This validation ensures optimal performance characteristics for open-addressing hash tables used
     * throughout the primitive collections framework.
     * <p>
     * <b>Load Factor Guidelines:</b>
     * <ul>
     * <li><b>0.1 - 0.4:</b> Low density, faster lookups, higher memory usage</li>
     * <li><b>0.5 - 0.7:</b> Recommended range for linear probing (optimal balance)</li>
     * <li><b>0.7 - 0.9:</b> Higher density, potential performance degradation</li>
     * </ul>
     * <p>
     * <b>Performance Impact:</b>
     * Load factors outside the valid range can severely impact hash table performance:
     * <ul>
     * <li>Too low (&lt; 0.1): Wastes memory and reduces cache efficiency</li>
     * <li>Too high (&gt; 0.9): Increases collision probability and probe distances</li>
     * </ul>
     * <p>
     * <b>Usage Example:</b>
     * <pre>{@code
     * // Validate before creating primitive hash map
     * float loadFactor = 0.6f;
     * CollectionUtil.validateLoadFactor(loadFactor);
     * Int2IntHashMap map = new Int2IntHashMap(1024, loadFactor);
     * }</pre>
     * <p>
     * <b>Algorithm Context:</b> This validation is specifically designed for open-addressing
     * hash tables with linear probing, as used by Agrona's primitive collections.
     *
     * @param loadFactor the load factor to validate. Must be in range [0.1, 0.9].
     * @throws IllegalArgumentException if loadFactor is outside the range [0.1, 0.9]
     */
    public static void validateLoadFactor(final float loadFactor)
    {
        if (loadFactor < 0.1f || loadFactor > 0.9f)
        {
            throw new IllegalArgumentException("load factor must be in the range of 0.1 to 0.9: " + loadFactor);
        }
    }

    /**
     * Validates that a value is a positive power of two, which is required for optimal
     * performance in many collection and buffer operations throughout the Agrona library.
     * <p>
     * <b>Power of Two Benefits:</b>
     * <ul>
     * <li><b>Efficient Modulo:</b> (value % powerOfTwo) becomes (value &amp; (powerOfTwo - 1))</li>
     * <li><b>Cache Alignment:</b> Ensures proper alignment for CPU cache line optimization</li>
     * <li><b>Ring Buffer Performance:</b> Essential for lock-free circular buffer implementations</li>
     * <li><b>Hash Table Sizing:</b> Optimal for open-addressing hash table capacity</li>
     * </ul>
     * <p>
     * <b>Valid Power of Two Values:</b>
     * 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, ...
     * <p>
     * <b>Performance Context:</b>
     * Power-of-two sizing enables the JVM and processor to use efficient bitwise operations
     * instead of expensive division and modulo operations, critical for high-frequency scenarios.
     * <p>
     * <b>Usage Examples:</b>
     * <pre>{@code
     * // Validate ring buffer capacity
     * int capacity = 1024;
     * CollectionUtil.validatePositivePowerOfTwo(capacity);
     * OneToOneRingBuffer ringBuffer = new OneToOneRingBuffer(buffer, capacity);
     * 
     * // Validate hash map initial capacity
     * int initialCapacity = 256;
     * CollectionUtil.validatePositivePowerOfTwo(initialCapacity);
     * Int2IntHashMap map = new Int2IntHashMap(initialCapacity);
     * }</pre>
     * <p>
     * <b>Implementation Note:</b> This method delegates to {@link org.agrona.BitUtil#isPowerOfTwo(int)}
     * for the actual validation logic.
     *
     * @param value the value to validate. Must be a positive power of two (1, 2, 4, 8, 16, ...).
     * @throws IllegalArgumentException if value is not a positive power of two
     * @see org.agrona.BitUtil#isPowerOfTwo(int)
     */
    public static void validatePositivePowerOfTwo(final int value)
    {
        if (!BitUtil.isPowerOfTwo(value))
        {
            throw new IllegalArgumentException("value must be a positive power of two: " + value);
        }
    }

    /**
     * Removes elements from a list that match the specified predicate, using indexed access
     * to avoid iterator allocation. This method provides an efficient alternative to
     * {@link java.util.Collection#removeIf(java.util.function.Predicate)} for performance-critical code.
     * <p>
     * <b>Performance Characteristics:</b>
     * <ul>
     * <li>O(n) time complexity where n is the initial list size</li>
     * <li>Zero allocation when used with {@link java.util.RandomAccess} implementations</li>
     * <li>In-place removal maintains list ordering of remaining elements</li>
     * <li>Efficient for small to moderate removal counts</li>
     * </ul>
     * <p>
     * <b>Algorithm Details:</b>
     * The method iterates through the list using indexed access. When a matching element
     * is found, it is removed immediately and the loop continues with the same index
     * (since subsequent elements shift down). This approach maintains element ordering
     * and minimizes memory allocations.
     * <p>
     * <b>Usage Examples:</b>
     * <pre>{@code
     * // Remove expired items
     * List<CacheEntry> entries = getCacheEntries();
     * int removed = removeIf(entries, CacheEntry::isExpired);
     * 
     * // Remove items by value
     * List<Integer> numbers = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
     * int removed = removeIf(numbers, n -> n % 2 == 0); // Remove even numbers
     * 
     * // Remove items matching complex criteria  
     * List<Order> orders = getOrders();
     * int cancelled = removeIf(orders, o -> o.getStatus() == CANCELLED && o.isOld());
     * }</pre>
     * <p>
     * <b>Performance Note:</b> This method is optimized for {@link java.util.RandomAccess}
     * implementations like {@link java.util.ArrayList}. For large lists with many removals,
     * consider collecting non-matching elements into a new list instead.
     * <p>
     * <b>Concurrent Modification:</b> This method modifies the list during iteration.
     * External synchronization is required for concurrent access.
     *
     * @param values    the list to iterate over and modify. Must not be {@code null}.
     * @param predicate the predicate to test each element against. Must not be {@code null}.
     * @param <T>       the type of elements in the list
     * @return the number of elements removed from the list.
     * @throws NullPointerException if values or predicate is {@code null}
     * @throws UnsupportedOperationException if the list does not support removal
     */
    public static <T> int removeIf(final List<T> values, final Predicate<T> predicate)
    {
        int size = values.size();
        int total = 0;

        for (int i = 0; i < size; )
        {
            final T value = values.get(i);
            if (predicate.test(value))
            {
                values.remove(i);
                total++;
                size--;
            }
            else
            {
                i++;
            }
        }

        return total;
    }
}
