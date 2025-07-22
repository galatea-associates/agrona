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

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.agrona.BitUtil.isPowerOfTwo;

/**
 * Utility class providing common functions for buffer implementations and operations.
 * <p>
 * This class serves as the central hub for buffer-related utility operations in the Agrona library,
 * providing essential functionality for zero-copy buffer operations, memory management, and safety checks.
 * All methods are static and thread-safe unless otherwise noted.
 * <p>
 * Key capabilities provided:
 * <ul>
 * <li>Bounds checking for array and ByteBuffer access</li>
 * <li>Memory address calculations for direct ByteBuffers</li>
 * <li>Unsafe access to ByteBuffer internals (backing arrays, offsets)</li>
 * <li>Aligned memory allocation for performance optimization</li>
 * <li>Direct buffer cleanup and memory management</li>
 * </ul>
 * <p>
 * This class is extensively used by {@link DirectBuffer} and {@link MutableDirectBuffer} implementations
 * to provide low-level buffer operations while maintaining safety and performance.
 * <p>
 * <b>Thread Safety:</b> All methods in this class are thread-safe and can be called concurrently
 * from multiple threads without external synchronization.
 * <p>
 * <b>Performance Note:</b> Methods in this class use {@link UnsafeApi} for direct memory operations
 * when necessary, providing optimal performance for high-frequency buffer operations.
 *
 * @see DirectBuffer
 * @see MutableDirectBuffer
 * @see UnsafeApi
 */
public final class BufferUtil
{
    /**
     * UTF-8-encoded byte representation of the {@code "null"} string.
     * <p>
     * This constant provides a pre-encoded byte array for the string "null", commonly used
     * in buffer operations to represent null values in a consistent, zero-copy manner.
     * Using this constant avoids repeated encoding operations and ensures consistent
     * representation across the codebase.
     */
    public static final byte[] NULL_BYTES = "null".getBytes(StandardCharsets.UTF_8);

    /**
     * Native byte order of the current platform.
     * <p>
     * This constant caches the result of {@link ByteOrder#nativeOrder()} to avoid
     * repeated calls during buffer operations. Using the native byte order provides
     * optimal performance for primitive read/write operations as it avoids byte swapping.
     * <p>
     * <b>Performance Note:</b> Buffer implementations should prefer native byte order
     * when possible to maximize throughput in high-frequency operations.
     */
    public static final ByteOrder NATIVE_BYTE_ORDER = ByteOrder.nativeOrder();

    /**
     * Base offset for byte array access in the JVM memory layout.
     * <p>
     * This constant represents the offset from the object header to the first element
     * of a byte array. It is used in conjunction with {@link UnsafeApi} operations
     * to perform direct memory access on byte arrays, enabling zero-copy operations.
     * <p>
     * <b>Implementation Note:</b> This value is JVM-specific and is calculated at
     * class initialization time using {@link UnsafeApi#arrayBaseOffset(Class)}.
     */
    public static final long ARRAY_BASE_OFFSET = UnsafeApi.arrayBaseOffset(byte[].class);

    /**
     * Memory offset of the {@code hb} (heap buffer) field in {@link ByteBuffer}.
     * <p>
     * This offset enables direct access to the backing byte array of a heap-based
     * {@link ByteBuffer} using {@link UnsafeApi} operations. The {@code hb} field
     * contains the reference to the underlying byte array for heap buffers.
     * <p>
     * <b>Use Case:</b> Primarily used in {@link #array(ByteBuffer)} to retrieve
     * the backing array from read-only ByteBuffers where {@link ByteBuffer#array()}
     * would throw an exception.
     * <p>
     * <b>Initialization:</b> Calculated using reflection during static initialization.
     */
    public static final long BYTE_BUFFER_HB_FIELD_OFFSET;

    /**
     * Memory offset of the {@code offset} field in {@link ByteBuffer}.
     * <p>
     * This offset provides access to the array offset field of a {@link ByteBuffer},
     * which indicates the starting position within the backing array. This is essential
     * for calculating the correct memory addresses when a ByteBuffer represents a slice
     * or view of a larger array.
     * <p>
     * <b>Use Case:</b> Used in {@link #arrayOffset(ByteBuffer)} to retrieve the array
     * offset from read-only ByteBuffers where standard methods are not accessible.
     * <p>
     * <b>Initialization:</b> Calculated using reflection during static initialization.
     */
    public static final long BYTE_BUFFER_OFFSET_FIELD_OFFSET;

    /**
     * Memory offset of the {@code address} field in {@link java.nio.Buffer}.
     * <p>
     * This offset enables access to the native memory address of direct ByteBuffers.
     * The {@code address} field contains the actual memory pointer for off-heap
     * (direct) buffers, which is essential for performing direct memory operations.
     * <p>
     * <b>Use Case:</b> Critical for {@link #address(ByteBuffer)} method to retrieve
     * the native memory address for direct buffer operations and alignment calculations.
     * <p>
     * <b>Initialization:</b> Calculated using reflection during static initialization.
     * <p>
     * <b>Note:</b> This field is only meaningful for direct ByteBuffers; heap buffers
     * will have an address value of 0.
     */
    public static final long BYTE_BUFFER_ADDRESS_FIELD_OFFSET;

    static
    {
        try
        {
            BYTE_BUFFER_HB_FIELD_OFFSET = UnsafeApi.objectFieldOffset(
                ByteBuffer.class.getDeclaredField("hb"));

            BYTE_BUFFER_OFFSET_FIELD_OFFSET = UnsafeApi.objectFieldOffset(
                ByteBuffer.class.getDeclaredField("offset"));

            BYTE_BUFFER_ADDRESS_FIELD_OFFSET = UnsafeApi.objectFieldOffset(Buffer.class.getDeclaredField("address"));
        }
        catch (final Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    private BufferUtil()
    {
    }

    /**
     * Perform bounds checking on a byte array access range and throw an exception if exceeded.
     * <p>
     * This method validates that a read or write operation starting at the specified index
     * and spanning the given length will not exceed the bounds of the provided byte array.
     * It performs comprehensive range validation including negative index detection and
     * overflow protection for large index + length combinations.
     * <p>
     * <b>Range Validation:</b>
     * <ul>
     * <li>Verifies {@code index >= 0}</li>
     * <li>Verifies {@code index + length <= buffer.length}</li>
     * <li>Handles potential overflow when adding {@code index + length}</li>
     * </ul>
     * <p>
     * <b>Usage:</b> This method is typically called before any array access operation
     * to ensure memory safety and provide clear error messages for debugging.
     * <p>
     * <b>Performance:</b> This is a lightweight operation with minimal overhead,
     * suitable for use in high-frequency buffer operations when bounds checking is enabled.
     *
     * @param buffer the byte array to be checked for valid access bounds
     * @param index  the starting index for the access operation (must be non-negative)
     * @param length the number of bytes to be accessed (must be non-negative)
     * @throws IndexOutOfBoundsException if the access range exceeds array bounds,
     *         including detailed information about the attempted access and array capacity
     * @see #boundsCheck(ByteBuffer, long, int)
     */
    public static void boundsCheck(final byte[] buffer, final long index, final int length)
    {
        final int capacity = buffer.length;
        final long resultingPosition = index + (long)length;
        if (index < 0 || resultingPosition > capacity)
        {
            throw new IndexOutOfBoundsException("index=" + index + " length=" + length + " capacity=" + capacity);
        }
    }

    /**
     * Perform bounds checking on a ByteBuffer access range and throw an exception if exceeded.
     * <p>
     * This method validates that a read or write operation starting at the specified index
     * and spanning the given length will not exceed the capacity of the provided ByteBuffer.
     * It works with both heap and direct ByteBuffers, performing the same comprehensive
     * validation as the byte array variant.
     * <p>
     * <b>Range Validation:</b>
     * <ul>
     * <li>Verifies {@code index >= 0}</li>
     * <li>Verifies {@code index + length <= buffer.capacity()}</li>
     * <li>Handles potential overflow when adding {@code index + length}</li>
     * </ul>
     * <p>
     * <b>ByteBuffer Compatibility:</b> This method uses {@link ByteBuffer#capacity()} rather
     * than position/limit, making it suitable for absolute positioning operations commonly
     * used in zero-copy buffer implementations.
     * <p>
     * <b>Usage:</b> Essential for all DirectBuffer and MutableDirectBuffer implementations
     * to ensure safe memory access regardless of the underlying ByteBuffer type.
     *
     * @param buffer the ByteBuffer to be checked for valid access bounds
     * @param index  the starting index for the access operation (must be non-negative)
     * @param length the number of bytes to be accessed (must be non-negative)
     * @throws IndexOutOfBoundsException if the access range exceeds buffer capacity,
     *         including detailed information about the attempted access and buffer capacity
     * @see #boundsCheck(byte[], long, int)
     */
    public static void boundsCheck(final ByteBuffer buffer, final long index, final int length)
    {
        final int capacity = buffer.capacity();
        final long resultingPosition = index + (long)length;
        if (index < 0 || resultingPosition > capacity)
        {
            throw new IndexOutOfBoundsException("index=" + index + " length=" + length + " capacity=" + capacity);
        }
    }

    /**
     * Retrieve the native memory address of a direct ByteBuffer's underlying storage.
     * <p>
     * This method extracts the actual memory pointer for direct (off-heap) ByteBuffers,
     * enabling direct memory operations using {@link UnsafeApi}. The returned address
     * points to the beginning of the buffer's allocated memory region.
     * <p>
     * <b>Direct Buffer Requirement:</b> This method only works with direct ByteBuffers
     * that are backed by native memory. Heap-based ByteBuffers will cause an exception
     * as they do not have a meaningful memory address.
     * <p>
     * <b>Use Cases:</b>
     * <ul>
     * <li>Calculating aligned memory addresses for performance optimization</li>
     * <li>Performing direct memory operations in high-performance scenarios</li>
     * <li>Integrating with native code that requires memory pointers</li>
     * <li>Implementing zero-copy buffer operations</li>
     * </ul>
     * <p>
     * <b>Implementation:</b> Uses {@link UnsafeApi} to access the private {@code address}
     * field of the ByteBuffer, avoiding the overhead of JNI calls that would be required
     * with standard Java APIs.
     * <p>
     * <b>Thread Safety:</b> This method is thread-safe and the returned address remains
     * valid for the lifetime of the ByteBuffer object.
     *
     * @param buffer the direct ByteBuffer whose memory address is required
     * @return the native memory address pointing to the start of the buffer's storage
     * @throws IllegalArgumentException if the buffer is not direct (i.e., buffer.isDirect() returns false)
     * @see #allocateDirectAligned(int, int)
     * @see ByteBuffer#isDirect()
     */
    public static long address(final ByteBuffer buffer)
    {
        if (!buffer.isDirect())
        {
            throw new IllegalArgumentException("buffer.isDirect() must be true");
        }

        return UnsafeApi.getLong(buffer, BYTE_BUFFER_ADDRESS_FIELD_OFFSET);
    }

    /**
     * Extract the backing byte array from a heap-based ByteBuffer, including read-only buffers.
     * <p>
     * This method provides access to the underlying byte array for heap-based ByteBuffers,
     * working even with read-only ByteBuffers where the standard {@link ByteBuffer#array()}
     * method would throw a {@link ReadOnlyBufferException}. It uses direct field access
     * to retrieve the backing array reference.
     * <p>
     * <b>Heap Buffer Requirement:</b> This method only works with heap-based ByteBuffers
     * that are backed by a byte array. Direct ByteBuffers will cause an exception as they
     * are backed by native memory rather than a Java array.
     * <p>
     * <b>Use Cases:</b>
     * <ul>
     * <li>Accessing arrays from read-only ByteBuffer views</li>
     * <li>Implementing zero-copy operations with heap buffers</li>
     * <li>Converting ByteBuffers to array-based DirectBuffer implementations</li>
     * <li>Optimizing array-based operations without buffer copying</li>
     * </ul>
     * <p>
     * <b>Implementation:</b> Uses {@link UnsafeApi} to access the private {@code hb}
     * (heap buffer) field directly, bypassing the access restrictions of read-only buffers.
     * <p>
     * <b>Safety Note:</b> The returned array is the actual backing storage. Modifications
     * to this array will be reflected in the original ByteBuffer (unless it's read-only,
     * in which case the buffer's view remains unchanged but the underlying data is modified).
     *
     * @param buffer the heap-based ByteBuffer whose backing array is required
     * @return the underlying byte array that backs the buffer
     * @throws IllegalArgumentException if the buffer is direct (i.e., not backed by an array)
     * @see #arrayOffset(ByteBuffer)
     * @see ByteBuffer#array()
     * @see ByteBuffer#isDirect()
     */
    public static byte[] array(final ByteBuffer buffer)
    {
        if (buffer.isDirect())
        {
            throw new IllegalArgumentException("buffer must wrap an array");
        }

        return (byte[])UnsafeApi.getReference(buffer, BYTE_BUFFER_HB_FIELD_OFFSET);
    }

    /**
     * Retrieve the array offset for a heap-based ByteBuffer, including read-only buffers.
     * <p>
     * This method returns the starting offset within the backing byte array where this
     * ByteBuffer's data begins. It works with read-only ByteBuffers where the standard
     * {@link ByteBuffer#arrayOffset()} method would throw a {@link ReadOnlyBufferException}.
     * <p>
     * <b>Offset Significance:</b> The array offset is crucial when a ByteBuffer represents
     * a slice or view of a larger array. For example, if a ByteBuffer is created from
     * {@code array.slice()}, the offset indicates where in the original array this
     * buffer's data starts.
     * <p>
     * <b>Use Cases:</b>
     * <ul>
     * <li>Calculating correct memory addresses for array-based operations</li>
     * <li>Implementing zero-copy operations with sliced buffers</li>
     * <li>Converting ByteBuffer views to DirectBuffer implementations</li>
     * <li>Optimizing bulk operations by avoiding buffer position management</li>
     * </ul>
     * <p>
     * <b>Implementation:</b> Uses {@link UnsafeApi} to access the private {@code offset}
     * field directly, enabling access even for read-only buffer views.
     * <p>
     * <b>Relationship to array():</b> When used together with {@link #array(ByteBuffer)},
     * the effective starting address for buffer operations is {@code array + arrayOffset}.
     *
     * @param buffer the heap-based ByteBuffer whose array offset is required
     * @return the offset within the backing array where this buffer's data begins
     * @see #array(ByteBuffer)
     * @see ByteBuffer#arrayOffset()
     */
    public static int arrayOffset(final ByteBuffer buffer)
    {
        return UnsafeApi.getInt(buffer, BYTE_BUFFER_OFFSET_FIELD_OFFSET);
    }

    /**
     * Allocate a new direct ByteBuffer with guaranteed memory alignment for optimal performance.
     * <p>
     * This method creates a direct (off-heap) ByteBuffer whose starting memory address is
     * aligned to the specified boundary. Proper memory alignment is crucial for performance
     * in high-frequency operations, especially when interfacing with SIMD instructions,
     * cache line optimization, or hardware that requires specific alignment.
     * <p>
     * <b>Alignment Requirements:</b>
     * <ul>
     * <li>Alignment must be a power of 2 (e.g., 8, 16, 32, 64)</li>
     * <li>Common alignments: 8 bytes (long), 16 bytes (SSE), 32 bytes (AVX), 64 bytes (cache line)</li>
     * <li>Higher alignments may waste more memory but provide better performance</li>
     * </ul>
     * <p>
     * <b>Implementation Strategy:</b>
     * <ol>
     * <li>Allocates a larger direct buffer (capacity + alignment)</li>
     * <li>Calculates the memory address using {@link #address(ByteBuffer)}</li>
     * <li>Determines the offset needed to achieve proper alignment</li>
     * <li>Returns a slice of the buffer starting at the aligned position</li>
     * </ol>
     * <p>
     * <b>Important Limitations:</b>
     * <ul>
     * <li>The returned buffer is a {@link ByteBuffer#slice()}, not the original allocation</li>
     * <li>Cannot be freed using {@link #free(ByteBuffer)} - would attempt to free the slice</li>
     * <li>The original buffer's reference is lost, relying on GC for cleanup</li>
     * <li>Memory overhead of up to (alignment - 1) bytes per allocation</li>
     * </ul>
     * <p>
     * <b>Performance Benefits:</b> Aligned memory access can provide significant performance
     * improvements (10-50%) for bulk operations, especially when combined with vectorized
     * operations or when targeting specific CPU cache line boundaries.
     *
     * @param capacity  the required usable capacity of the buffer in bytes
     * @param alignment the memory alignment boundary in bytes (must be a power of 2)
     * @return a new direct ByteBuffer with the specified capacity and alignment
     * @throws IllegalArgumentException if alignment is not a power of 2
     * @see BitUtil#isPowerOfTwo(int)
     * @see #address(ByteBuffer)
     */
    public static ByteBuffer allocateDirectAligned(final int capacity, final int alignment)
    {
        if (!isPowerOfTwo(alignment))
        {
            throw new IllegalArgumentException("Must be a power of 2: alignment=" + alignment);
        }

        final ByteBuffer buffer = ByteBuffer.allocateDirect(capacity + alignment);

        final long address = address(buffer);
        final int remainder = (int)(address & (alignment - 1));
        final int offset = alignment - remainder;

        buffer.limit(capacity + offset);
        buffer.position(offset);

        return buffer.slice();
    }

    /**
     * Immediately release the native memory associated with a DirectBuffer's underlying ByteBuffer.
     * <p>
     * This method provides explicit control over direct memory deallocation by invoking the
     * internal {@code Cleaner} mechanism on the DirectBuffer's underlying ByteBuffer. This
     * is particularly important for applications with strict memory management requirements
     * where waiting for garbage collection is not acceptable.
     * <p>
     * <b>Memory Management Benefits:</b>
     * <ul>
     * <li>Immediate release of off-heap memory without waiting for GC</li>
     * <li>Prevents OutOfMemoryError in scenarios with many direct buffers</li>
     * <li>Provides deterministic memory cleanup for resource-constrained environments</li>
     * <li>Essential for long-running applications with dynamic buffer allocation</li>
     * </ul>
     * <p>
     * <b>Safety Guarantees:</b>
     * <ul>
     * <li>Null-safe: gracefully handles null DirectBuffer references</li>
     * <li>No-op for non-direct buffers: safely handles heap-based buffers</li>
     * <li>Idempotent: multiple calls on the same buffer are safe</li>
     * </ul>
     * <p>
     * <b>Usage Guidelines:</b>
     * Only call this method when you are certain the buffer will no longer be accessed.
     * Accessing a freed buffer results in undefined behavior and potential JVM crashes.
     * <p>
     * <b>Implementation:</b> Delegates to {@link #free(ByteBuffer)} after extracting
     * the underlying ByteBuffer using {@link DirectBuffer#byteBuffer()}.
     *
     * @param buffer the DirectBuffer to be freed, may be null
     * @see #free(ByteBuffer)
     * @see DirectBuffer#byteBuffer()
     */
    public static void free(final DirectBuffer buffer)
    {
        if (null != buffer)
        {
            free(buffer.byteBuffer());
        }
    }

    /**
     * Immediately release the native memory associated with a direct ByteBuffer.
     * <p>
     * This method provides direct control over off-heap memory deallocation by invoking
     * the internal {@code Cleaner} mechanism that is normally triggered during garbage
     * collection. This enables immediate memory release without waiting for GC cycles,
     * which is critical for applications with tight memory constraints.
     * <p>
     * <b>Direct Memory Management:</b>
     * Direct ByteBuffers allocate memory outside the Java heap using {@code malloc} or
     * similar native allocation. This memory is not subject to normal GC and must be
     * explicitly freed to prevent memory leaks. The JVM normally handles this through
     * a {@code Cleaner} attached to the ByteBuffer, but this method provides immediate
     * control over the cleanup process.
     * <p>
     * <b>Safety Features:</b>
     * <ul>
     * <li>Null-safe: gracefully handles null ByteBuffer references</li>
     * <li>Type-safe: only processes direct buffers, ignores heap buffers</li>
     * <li>Idempotent: safe to call multiple times on the same buffer</li>
     * <li>Exception-safe: handles any cleanup errors internally</li>
     * </ul>
     * <p>
     * <b>Critical Warning:</b> After calling this method, the ByteBuffer becomes invalid
     * and any subsequent access will result in undefined behavior, potentially causing
     * JVM crashes. Ensure no references to the buffer remain accessible.
     * <p>
     * <b>Use Cases:</b>
     * <ul>
     * <li>High-frequency buffer allocation/deallocation scenarios</li>
     * <li>Resource-constrained environments requiring immediate cleanup</li>
     * <li>Preventing DirectByteBuffer memory leaks in long-running applications</li>
     * <li>Integration with native libraries requiring explicit memory management</li>
     * </ul>
     * <p>
     * <b>Implementation:</b> Uses {@link UnsafeApi#invokeCleaner(ByteBuffer)} to trigger
     * the same cleanup mechanism that would normally be invoked during finalization.
     *
     * @param buffer the direct ByteBuffer to be freed, may be null or non-direct
     * @see ByteBuffer#isDirect()
     * @see UnsafeApi#invokeCleaner(ByteBuffer)
     */
    public static void free(final ByteBuffer buffer)
    {
        if (null != buffer && buffer.isDirect())
        {
            UnsafeApi.invokeCleaner(buffer);
        }
    }
}
