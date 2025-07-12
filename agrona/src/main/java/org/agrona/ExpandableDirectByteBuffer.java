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
package org.agrona;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.agrona.BufferUtil.address;

/**
 * Expandable {@link MutableDirectBuffer} that is backed by a direct {@link ByteBuffer} with automatic capacity
 * expansion and native memory management. This implementation provides zero-copy buffer operations with dynamic
 * growth capabilities for high-performance applications requiring predictable memory access patterns.
 * <p>
 * <h2>Automatic Buffer Expansion</h2>
 * When values are put into the buffer beyond its current capacity, the buffer automatically expands to accommodate
 * the resulting position. The expansion uses a growth factor of 1.5x (current capacity + current capacity / 2) to
 * balance memory efficiency with expansion frequency. Expansion continues until the buffer reaches
 * {@link #MAX_BUFFER_LENGTH} (Integer.MAX_VALUE - 8) to prevent JVM array size limitations.
 * <p>
 * <h2>Native Memory Management</h2>
 * This buffer implementation leverages {@link ByteBuffer#allocateDirect(int)} for off-heap memory allocation,
 * providing direct access to native memory through the Unsafe API via {@link BufferUtil#address(ByteBuffer)}.
 * This approach delivers optimal performance characteristics:
 * <ul>
 * <li><b>Zero-copy operations:</b> Direct memory access without intermediate heap allocation</li>
 * <li><b>Reduced GC pressure:</b> Off-heap storage minimizes garbage collection impact</li>
 * <li><b>Cache-friendly access:</b> Contiguous memory layout optimizes CPU cache utilization</li>
 * <li><b>Memory ordering:</b> Supports precise memory ordering semantics for concurrent access</li>
 * </ul>
 * <p>
 * <h2>Performance Characteristics</h2>
 * <ul>
 * <li><b>Expansion cost:</b> O(n) copy operation when capacity is exceeded, amortized O(1) for typical usage</li>
 * <li><b>Access time:</b> O(1) direct memory access via Unsafe operations</li>
 * <li><b>Memory overhead:</b> Minimal - only direct buffer overhead plus expansion headroom</li>
 * <li><b>Thread safety:</b> Not thread-safe; external synchronization required for concurrent access</li>
 * </ul>
 * <p>
 * <h2>Usage Patterns</h2>
 * This buffer is particularly well-suited for:
 * <ul>
 * <li>High-frequency message processing where buffer sizes vary significantly</li>
 * <li>Streaming data scenarios with unknown payload sizes</li>
 * <li>Protocol encoding/decoding with dynamic message structures</li>
 * <li>Zero-copy I/O operations requiring automatic capacity management</li>
 * </ul>
 * <p>
 * <h2>Operational Semantics</h2>
 * Put operations will expand the capacity as necessary up to {@link #MAX_BUFFER_LENGTH}. Get operations will throw
 * a {@link IndexOutOfBoundsException} if accessing beyond current capacity - expansion only occurs on write operations.
 * <p>
 * {@link ByteOrder} of a wrapped buffer is not applied to the {@link ExpandableDirectByteBuffer};
 * To control {@link ByteOrder} use the appropriate method with the {@link ByteOrder} overload.
 * <p>
 * <b>Note:</b> This class has a natural ordering that is inconsistent with equals.
 * Types may be different but equal on buffer contents.
 * <p>
 * <b>Implementation note:</b> Wrap operations are not supported as this buffer manages its own capacity expansion.
 * Attempting to wrap external buffers will result in {@link UnsupportedOperationException}.
 *
 * @see DirectBuffer
 * @see MutableDirectBuffer
 * @see AbstractMutableDirectBuffer
 */
public class ExpandableDirectByteBuffer extends AbstractMutableDirectBuffer
{
    /**
     * Maximum length to which the underlying buffer can grow.
     * <p>
     * Set to {@code Integer.MAX_VALUE - 8} to avoid potential JVM-specific array size limitations.
     * This constraint prevents {@link OutOfMemoryError} conditions that can occur when requesting
     * arrays larger than the JVM's maximum array size. The buffer management system enforces this
     * limit during expansion operations to ensure reliable behavior across different JVM implementations.
     */
    public static final int MAX_BUFFER_LENGTH = Integer.MAX_VALUE - 8;

    /**
     * Initial capacity of the buffer from which it will expand.
     * <p>
     * Set to 128 bytes to provide a reasonable starting point that balances memory efficiency with
     * expansion frequency for typical use cases. This size accommodates small messages without
     * immediate expansion while remaining small enough to avoid significant memory waste for
     * applications that primarily handle small data structures.
     * <p>
     * The value is chosen to be cache-line friendly (multiple of 64 bytes) and power-of-two adjacent
     * for optimal memory allocator behavior on most platforms.
     */
    public static final int INITIAL_CAPACITY = 128;

    private ByteBuffer byteBuffer;

    /**
     * Create an {@link ExpandableDirectByteBuffer} with an initial length of {@link #INITIAL_CAPACITY}.
     * <p>
     * Allocates direct (off-heap) memory using {@link ByteBuffer#allocateDirect(int)} and establishes
     * the native memory address mapping through the Unsafe API for optimal performance.
     */
    public ExpandableDirectByteBuffer()
    {
        this(INITIAL_CAPACITY);
    }

    /**
     * Create an {@link ExpandableDirectByteBuffer} with a provided initial capacity.
     * <p>
     * Allocates direct (off-heap) memory of the specified size using {@link ByteBuffer#allocateDirect(int)}
     * and establishes the native memory address mapping through the Unsafe API. The buffer will automatically
     * expand beyond this initial capacity as needed, using a 1.5x growth factor to minimize allocation frequency.
     *
     * @param initialCapacity of the backing direct buffer in bytes.
     * @throws IllegalArgumentException if initialCapacity is negative.
     * @throws OutOfMemoryError if insufficient native memory is available for allocation.
     */
    public ExpandableDirectByteBuffer(final int initialCapacity)
    {
        byteBuffer = ByteBuffer.allocateDirect(initialCapacity);
        addressOffset = address(byteBuffer);
        capacity = initialCapacity;
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Not supported:</b> {@link ExpandableDirectByteBuffer} manages its own native memory allocation
     * and cannot wrap external heap-based arrays. This restriction ensures consistent expansion behavior
     * and maintains the direct memory management characteristics that are fundamental to this implementation.
     * 
     * @throws UnsupportedOperationException always, as wrapping operations are incompatible with
     *                                       automatic capacity expansion.
     */
    public void wrap(final byte[] buffer)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public void wrap(final byte[] buffer, final int offset, final int length)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public void wrap(final ByteBuffer buffer)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public void wrap(final ByteBuffer buffer, final int offset, final int length)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public void wrap(final DirectBuffer buffer)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public void wrap(final DirectBuffer buffer, final int offset, final int length)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public void wrap(final long address, final int length)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public byte[] byteArray()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the underlying direct {@link ByteBuffer} that provides access to the native memory region.
     * This buffer is allocated using {@link ByteBuffer#allocateDirect(int)} and provides zero-copy
     * access to off-heap memory. The returned buffer reflects the current capacity and may change
     * during expansion operations.
     * <p>
     * <b>Note:</b> Direct manipulation of the returned {@link ByteBuffer} may interfere with the
     * automatic expansion mechanism and should be used with caution.
     */
    public ByteBuffer byteBuffer()
    {
        return byteBuffer;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always returns {@code true} for {@link ExpandableDirectByteBuffer} as automatic capacity expansion
     * is a core feature of this implementation. This indicates to the buffer management system that
     * write operations beyond current capacity will trigger expansion rather than throwing exceptions.
     */
    public boolean isExpandable()
    {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public int wrapAdjustment()
    {
        return 0;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Ensures the buffer has sufficient capacity to accommodate operations up to the specified limit.
     * This method triggers automatic expansion if necessary, using the same growth algorithm as
     * write operations. Unlike bounds checking in fixed-size buffers, this method proactively
     * expands capacity to prevent subsequent operation failures.
     */
    public void checkLimit(final int limit)
    {
        ensureCapacity(limit, 0);
    }

    /**
     * {@inheritDoc}
     */
    public String toString()
    {
        return "ExpandableDirectByteBuffer{" +
            "address=" + addressOffset +
            ", capacity=" + capacity +
            ", byteBuffer=" + byteBuffer +
            '}';
    }

    /**
     * Ensures the buffer has sufficient capacity to accommodate a write operation at the specified index and length.
     * <p>
     * <h3>Automatic Expansion Algorithm</h3>
     * If the required capacity exceeds the current buffer size, this method performs the following operations:
     * <ol>
     * <li>Calculates the new capacity using a 1.5x growth factor via {@link #calculateExpansion(int, long)}</li>
     * <li>Allocates a new direct {@link ByteBuffer} with the expanded capacity</li>
     * <li>Copies existing data from the old buffer to the new buffer using zero-copy operations</li>
     * <li>Updates internal references to use the new native memory address</li>
     * <li>Releases the old buffer for garbage collection</li>
     * </ol>
     * <p>
     * <h3>Performance Considerations</h3>
     * The expansion operation has O(n) complexity due to the data copy requirement, but the 1.5x growth factor
     * ensures amortized O(1) performance for typical append operations. Native memory allocation may be slower
     * than heap allocation but provides superior cache locality and eliminates GC pressure.
     *
     * @param index  the starting index for the operation.
     * @param length the number of bytes required for the operation.
     * @throws IndexOutOfBoundsException if index or length is negative, or if the required capacity
     *                                   would exceed {@link #MAX_BUFFER_LENGTH}.
     * @throws OutOfMemoryError if insufficient native memory is available for buffer expansion.
     */
    protected final void ensureCapacity(final int index, final int length)
    {
        if (index < 0 || length < 0)
        {
            throw new IndexOutOfBoundsException("negative value: index=" + index + " length=" + length);
        }

        final long resultingPosition = index + (long)length;
        final int currentCapacity = capacity;
        if (resultingPosition > currentCapacity)
        {
            if (resultingPosition > MAX_BUFFER_LENGTH)
            {
                throw new IndexOutOfBoundsException(
                    "index=" + index + " length=" + length + " maxCapacity=" + MAX_BUFFER_LENGTH);
            }

            final int newCapacity = calculateExpansion(currentCapacity, resultingPosition);
            final ByteBuffer newBuffer = ByteBuffer.allocateDirect(newCapacity);
            final long newAddress = address(newBuffer);

            getBytes(0, newBuffer, 0, currentCapacity);

            byteBuffer = newBuffer;
            addressOffset = newAddress;
            capacity = newCapacity;
        }
    }

    /**
     * Calculates the new buffer capacity using a 1.5x geometric growth strategy to balance memory efficiency
     * with expansion frequency.
     * <p>
     * <h3>Growth Algorithm Details</h3>
     * The algorithm uses the formula: {@code newCapacity = currentCapacity + (currentCapacity >> 1)}, which
     * provides 50% growth per expansion. This strategy:
     * <ul>
     * <li>Minimizes the number of expensive allocation/copy cycles</li>
     * <li>Avoids excessive memory overhead compared to 2x doubling strategies</li>
     * <li>Provides predictable memory usage patterns for capacity planning</li>
     * <li>Ensures geometric growth to achieve amortized O(1) append performance</li>
     * </ul>
     * <p>
     * The algorithm continues iterating until the capacity meets or exceeds the required length, with an
     * upper bound constraint of {@link #MAX_BUFFER_LENGTH} to prevent integer overflow issues.
     *
     * @param currentLength  the current buffer capacity in bytes.
     * @param requiredLength the minimum required capacity in bytes.
     * @return the new buffer capacity that will satisfy the required length.
     */
    private static int calculateExpansion(final int currentLength, final long requiredLength)
    {
        long value = Math.max(currentLength, 2);

        while (value < requiredLength)
        {
            value = value + (value >> 1);

            if (value > MAX_BUFFER_LENGTH)
            {
                value = MAX_BUFFER_LENGTH;
            }
        }

        return (int)value;
    }
}
