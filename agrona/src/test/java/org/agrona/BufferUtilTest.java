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

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;

import java.nio.ByteBuffer;

import static org.agrona.BitUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.condition.JRE.JAVA_9;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test suite for {@link BufferUtil} that demonstrates comprehensive buffer allocation,
 * alignment, and cleanup patterns essential for zero-copy operations.
 * 
 * <p>This test class serves as a reference implementation for the buffer-management.md
 * documentation, providing concrete examples of:
 * <ul>
 * <li>Direct buffer allocation with various alignment requirements</li>
 * <li>Memory alignment validation for optimal cache performance</li>
 * <li>Proper resource cleanup and memory management</li>
 * <li>Error handling for invalid alignment specifications</li>
 * <li>Integration patterns with different buffer types</li>
 * </ul>
 * 
 * <p>These patterns are fundamental to achieving microsecond-latency performance
 * characteristics in high-frequency trading and real-time messaging systems.
 * 
 * @see BufferUtil
 * @see DirectBuffer
 * @see MutableDirectBuffer
 */

class BufferUtilTest
{
    /**
     * Demonstrates validation of alignment requirements for buffer allocation.
     * 
     * <p>BufferUtil enforces that alignment values must be powers of two to ensure
     * proper memory alignment for atomic operations and cache-line optimization.
     * Non-power-of-two alignments would compromise performance and correctness.
     * 
     * <p><strong>Documentation Example:</strong> Error handling for invalid alignment specifications.
     */
    @Test
    void shouldDetectNonPowerOfTwoAlignment()
    {
        assertThrows(IllegalArgumentException.class, () -> BufferUtil.allocateDirectAligned(1, 3));
    }

    /**
     * Demonstrates direct buffer allocation with word-boundary alignment.
     * 
     * <p>Word-aligned buffers (8-byte alignment) are essential for atomic long operations
     * and optimal memory access patterns. This alignment ensures that primitive operations
     * don't span cache lines, maintaining performance on modern CPU architectures.
     * 
     * <p><strong>Documentation Example:</strong> Standard word-aligned buffer allocation.
     * <pre>
     * // Allocate 128-byte buffer aligned to 8-byte boundary
     * ByteBuffer buffer = BufferUtil.allocateDirectAligned(128, SIZE_OF_LONG);
     * long address = BufferUtil.address(buffer);
     * // Address is guaranteed to be divisible by 8
     * </pre>
     */
    @Test
    void shouldAlignToWordBoundary()
    {
        final int capacity = 128;
        final ByteBuffer byteBuffer = BufferUtil.allocateDirectAligned(capacity, SIZE_OF_LONG);

        final long address = BufferUtil.address(byteBuffer);
        assertTrue(isAligned(address, SIZE_OF_LONG));
        assertThat(byteBuffer.capacity(), is(capacity));
    }

    /**
     * Demonstrates direct buffer allocation with cache-line alignment.
     * 
     * <p>Cache-line aligned buffers (64-byte alignment on most modern processors) prevent
     * false sharing between adjacent memory regions and optimize cache utilization.
     * This alignment is critical for high-performance concurrent data structures
     * and ring buffer implementations.
     * 
     * <p><strong>Documentation Example:</strong> Cache-optimized buffer allocation.
     * <pre>
     * // Allocate buffer aligned to CPU cache line boundary (typically 64 bytes)
     * ByteBuffer buffer = BufferUtil.allocateDirectAligned(128, CACHE_LINE_LENGTH);
     * long address = BufferUtil.address(buffer);
     * // Address is aligned to prevent false sharing in multi-threaded scenarios
     * </pre>
     */
    @Test
    void shouldAlignToCacheLineBoundary()
    {
        final int capacity = 128;
        final ByteBuffer byteBuffer = BufferUtil.allocateDirectAligned(capacity, CACHE_LINE_LENGTH);

        final long address = BufferUtil.address(byteBuffer);
        assertTrue(isAligned(address, CACHE_LINE_LENGTH));
        assertThat(byteBuffer.capacity(), is(capacity));
    }

    /**
     * Demonstrates safe cleanup pattern for null DirectBuffer references.
     * 
     * <p>BufferUtil.free() is designed to be null-safe, allowing for simplified
     * cleanup code without explicit null checks. This pattern enables
     * defensive programming practices in resource management.
     * 
     * <p><strong>Documentation Example:</strong> Null-safe buffer cleanup.
     * <pre>
     * DirectBuffer buffer = null;
     * // ... potentially assign buffer
     * BufferUtil.free(buffer); // Safe even if buffer is null
     * </pre>
     */
    @Test
    void freeIsANoOpIfDirectBufferIsNull()
    {
        BufferUtil.free((DirectBuffer)null);
    }

    /**
     * Demonstrates safe cleanup pattern for null ByteBuffer references.
     * 
     * <p>Consistent null-safety across different buffer types ensures
     * uniform cleanup patterns regardless of the underlying buffer
     * implementation being used.
     * 
     * <p><strong>Documentation Example:</strong> Null-safe ByteBuffer cleanup.
     */
    @Test
    void freeIsANoOpIfByteBufferIsNull()
    {
        BufferUtil.free((ByteBuffer)null);
    }

    /**
     * Demonstrates selective cleanup behavior for heap-allocated ByteBuffers.
     * 
     * <p>BufferUtil.free() only releases resources for direct (off-heap) buffers.
     * Heap-allocated buffers are managed by the garbage collector and don't
     * require explicit cleanup. The buffer remains functional after the
     * free() call, demonstrating the selective nature of resource management.
     * 
     * <p><strong>Documentation Example:</strong> Heap vs direct buffer cleanup distinction.
     * <pre>
     * ByteBuffer heapBuffer = ByteBuffer.allocate(1024);
     * BufferUtil.free(heapBuffer); // No-op for heap buffers
     * // heapBuffer remains usable - GC handles cleanup
     * 
     * ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024);
     * BufferUtil.free(directBuffer); // Releases off-heap memory
     * </pre>
     */
    @Test
    void freeIsANoOpIfByteBufferIsNotDirect()
    {
        final ByteBuffer buffer = ByteBuffer.allocate(4);

        BufferUtil.free(buffer);

        buffer.put(2, (byte)101);
        assertEquals(101, buffer.get(2));
    }

    /**
     * Demonstrates cleanup behavior for DirectBuffer wrapping heap ByteBuffer.
     * 
     * <p>When a DirectBuffer wraps a heap-allocated ByteBuffer, the free()
     * operation becomes a no-op since the underlying memory is managed by
     * the garbage collector. This test shows the defensive programming
     * approach where cleanup attempts are safe regardless of buffer type.
     * 
     * <p><strong>Documentation Example:</strong> DirectBuffer wrapper cleanup safety.
     */
    @Test
    void freeIsANoOpIfDirectBufferContainsNonDirectByteBuffer()
    {
        final DirectBuffer buffer = mock(DirectBuffer.class);
        final ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        when(buffer.byteBuffer()).thenReturn(byteBuffer);

        BufferUtil.free(buffer);

        byteBuffer.put(1, (byte)5);
        assertEquals(5, byteBuffer.get(1));
    }

    /**
     * Demonstrates proper cleanup of direct ByteBuffer resources.
     * 
     * <p>Direct ByteBuffers allocate off-heap memory that must be explicitly
     * released to prevent memory leaks. BufferUtil.free() provides immediate
     * cleanup rather than waiting for garbage collection, which is critical
     * in long-running applications with high buffer allocation rates.
     * 
     * <p><strong>Documentation Example:</strong> Direct ByteBuffer lifecycle management.
     * <pre>
     * // Allocate off-heap buffer
     * ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
     * try {
     *     // Use buffer for zero-copy operations
     *     buffer.put(data);
     *     processBuffer(buffer);
     * } finally {
     *     // Explicit cleanup prevents memory leaks
     *     BufferUtil.free(buffer);
     * }
     * </pre>
     */
    @Test
    void freeShouldReleaseByteBufferResources()
    {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(4);
        buffer.put((byte)1);
        buffer.put((byte)2);
        buffer.put((byte)3);
        buffer.position(0);

        BufferUtil.free(buffer);
    }

    /**
     * Demonstrates proper cleanup of UnsafeBuffer wrapping direct memory.
     * 
     * <p>UnsafeBuffer instances that wrap direct ByteBuffers require cleanup
     * of the underlying off-heap memory. This pattern is common when using
     * Agrona's high-performance buffer implementations with direct memory
     * for zero-copy operations and atomic access patterns.
     * 
     * <p><strong>Documentation Example:</strong> UnsafeBuffer with direct memory cleanup.
     * <pre>
     * // Create UnsafeBuffer backed by direct memory
     * UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));
     * try {
     *     // High-performance operations with Unsafe API
     *     buffer.putLongOrdered(0, timestamp);
     *     buffer.putInt(8, messageType);
     * } finally {
     *     // Release underlying direct memory
     *     BufferUtil.free(buffer);
     * }
     * </pre>
     */
    @Test
    void freeShouldReleaseDirectBufferResources()
    {
        final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(4));
        buffer.setMemory(0, 4, (byte)111);

        BufferUtil.free(buffer);
    }

    /**
     * Demonstrates validation for sliced ByteBuffer cleanup attempts.
     * 
     * <p>Sliced buffers share the same underlying memory as their parent buffer
     * but have different position/limit settings. Attempting to free a slice
     * would compromise the parent buffer's memory, so BufferUtil prevents this
     * with explicit validation (Java 9+).
     * 
     * <p><strong>Documentation Example:</strong> Buffer slice cleanup restrictions.
     * <pre>
     * ByteBuffer original = ByteBuffer.allocateDirect(1024);
     * ByteBuffer slice = original.slice(); // Shares memory with original
     * 
     * BufferUtil.free(original); // Valid - releases memory
     * BufferUtil.free(slice);    // Invalid - throws IllegalArgumentException
     * </pre>
     */
    @Test
    @EnabledForJreRange(min = JAVA_9)
    void freeThrowsIllegalArgumentExceptionIfByteBufferIsASlice()
    {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(4).slice();

        assertThrows(IllegalArgumentException.class, () -> BufferUtil.free(buffer));
    }

    /**
     * Demonstrates validation for duplicated ByteBuffer cleanup attempts.
     * 
     * <p>Duplicated buffers share the same underlying memory as their parent
     * buffer with independent position/limit/mark. Like slices, freeing a
     * duplicate would affect the parent buffer's memory integrity, requiring
     * explicit validation to prevent unsafe operations.
     * 
     * <p><strong>Documentation Example:</strong> Buffer duplicate cleanup restrictions.
     * <pre>
     * ByteBuffer original = ByteBuffer.allocateDirect(1024);
     * ByteBuffer duplicate = original.duplicate(); // Independent view of same memory
     * 
     * BufferUtil.free(original);  // Valid - releases memory
     * BufferUtil.free(duplicate); // Invalid - throws IllegalArgumentException
     * </pre>
     */
    @Test
    @EnabledForJreRange(min = JAVA_9)
    void freeThrowsIllegalArgumentExceptionIfByteBufferIsADuplicate()
    {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(4).duplicate();

        assertThrows(IllegalArgumentException.class, () -> BufferUtil.free(buffer));
    }

    /**
     * Demonstrates various alignment requirements for different use cases.
     * 
     * <p>Different buffer operations require specific alignment for optimal performance:
     * <ul>
     * <li>4-byte alignment for int operations</li>
     * <li>8-byte alignment for long and double operations</li>
     * <li>16-byte alignment for SIMD operations on some architectures</li>
     * <li>64-byte alignment for cache-line optimization</li>
     * </ul>
     * 
     * <p><strong>Documentation Example:</strong> Multi-scenario alignment patterns.
     */
    @Test
    void shouldDemonstrateVariousAlignmentScenarios()
    {
        // Integer alignment for atomic int operations
        final ByteBuffer intBuffer = BufferUtil.allocateDirectAligned(64, SIZE_OF_INT);
        final long intAddress = BufferUtil.address(intBuffer);
        assertTrue(isAligned(intAddress, SIZE_OF_INT));

        // Double alignment for atomic double operations  
        final ByteBuffer doubleBuffer = BufferUtil.allocateDirectAligned(128, SIZE_OF_LONG);
        final long doubleAddress = BufferUtil.address(doubleBuffer);
        assertTrue(isAligned(doubleAddress, SIZE_OF_LONG));

        // SIMD alignment for vectorized operations (16-byte)
        final ByteBuffer simdBuffer = BufferUtil.allocateDirectAligned(256, 16);
        final long simdAddress = BufferUtil.address(simdBuffer);
        assertTrue(isAligned(simdAddress, 16));

        // Cache-line alignment for concurrent access patterns
        final ByteBuffer cacheBuffer = BufferUtil.allocateDirectAligned(512, CACHE_LINE_LENGTH);
        final long cacheAddress = BufferUtil.address(cacheBuffer);
        assertTrue(isAligned(cacheAddress, CACHE_LINE_LENGTH));

        // Cleanup all allocated buffers
        BufferUtil.free(intBuffer);
        BufferUtil.free(doubleBuffer);
        BufferUtil.free(simdBuffer);
        BufferUtil.free(cacheBuffer);
    }

    /**
     * Demonstrates buffer address calculation and alignment verification.
     * 
     * <p>Address calculation is essential for low-level operations and debugging
     * alignment issues. This pattern shows how to verify proper alignment
     * and calculate memory offsets for performance analysis.
     * 
     * <p><strong>Documentation Example:</strong> Address calculation and verification.
     * <pre>
     * ByteBuffer buffer = BufferUtil.allocateDirectAligned(1024, CACHE_LINE_LENGTH);
     * long address = BufferUtil.address(buffer);
     * 
     * // Verify alignment
     * boolean isCacheAligned = (address % CACHE_LINE_LENGTH) == 0;
     * 
     * // Calculate aligned offsets
     * long alignedOffset = align(offset, SIZE_OF_LONG);
     * </pre>
     */
    @Test
    void shouldDemonstrateAddressCalculationPatterns()
    {
        final int capacity = 1024;
        final ByteBuffer buffer = BufferUtil.allocateDirectAligned(capacity, CACHE_LINE_LENGTH);
        
        // Extract memory address for low-level operations
        final long address = BufferUtil.address(buffer);
        assertNotEquals(0L, address);
        
        // Verify cache-line alignment
        assertEquals(0L, address % CACHE_LINE_LENGTH, "Address should be cache-line aligned");
        
        // Demonstrate alignment verification utility
        assertTrue(isAligned(address, CACHE_LINE_LENGTH));
        assertTrue(isAligned(address, SIZE_OF_LONG));
        assertTrue(isAligned(address, SIZE_OF_INT));
        
        // Calculate aligned offsets within buffer
        final int unalignedOffset = 13;
        final int alignedOffset = align(unalignedOffset, SIZE_OF_LONG);
        assertEquals(16, alignedOffset); // Next 8-byte boundary
        
        BufferUtil.free(buffer);
    }

    /**
     * Demonstrates integration with UnsafeBuffer for high-performance operations.
     * 
     * <p>This pattern shows the complete lifecycle of creating aligned buffers
     * and wrapping them with UnsafeBuffer for high-performance operations.
     * This is the typical pattern used in Agrona's concurrent data structures.
     * 
     * <p><strong>Documentation Example:</strong> UnsafeBuffer integration pattern.
     * <pre>
     * // Create aligned direct buffer
     * ByteBuffer byteBuffer = BufferUtil.allocateDirectAligned(
     *     capacity, CACHE_LINE_LENGTH);
     * 
     * // Wrap with UnsafeBuffer for high-performance operations
     * UnsafeBuffer buffer = new UnsafeBuffer(byteBuffer);
     * 
     * // Perform atomic operations
     * buffer.putLongOrdered(0, System.nanoTime());
     * buffer.putIntOrdered(8, messageType);
     * 
     * // Cleanup
     * BufferUtil.free(buffer);
     * </pre>
     */
    @Test
    void shouldDemonstrateUnsafeBufferIntegrationPattern()
    {
        final int capacity = 256;
        
        // Allocate cache-line aligned direct buffer
        final ByteBuffer byteBuffer = BufferUtil.allocateDirectAligned(capacity, CACHE_LINE_LENGTH);
        
        // Wrap with UnsafeBuffer for high-performance operations
        final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(byteBuffer);
        
        // Verify buffer properties
        assertEquals(capacity, unsafeBuffer.capacity());
        assertEquals(byteBuffer, unsafeBuffer.byteBuffer());
        
        // Verify underlying buffer is aligned
        final long address = BufferUtil.address(byteBuffer);
        assertTrue(isAligned(address, CACHE_LINE_LENGTH));
        
        // Demonstrate high-performance operations
        final long timestamp = System.nanoTime();
        final int messageType = 42;
        
        unsafeBuffer.putLongOrdered(0, timestamp);
        unsafeBuffer.putIntOrdered(8, messageType);
        
        // Verify data integrity
        assertEquals(timestamp, unsafeBuffer.getLong(0));
        assertEquals(messageType, unsafeBuffer.getInt(8));
        
        // Proper cleanup of underlying direct memory
        BufferUtil.free(unsafeBuffer);
    }
}
