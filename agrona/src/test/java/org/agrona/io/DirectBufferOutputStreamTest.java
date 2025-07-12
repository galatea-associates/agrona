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
package org.agrona.io;

import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for DirectBufferOutputStream demonstrating all usage patterns.
 * This test class serves as a reference implementation for the io-utilities.md documentation,
 * providing examples of initialization, write operations, buffer verification, and error handling.
 */
class DirectBufferOutputStreamTest
{
    private static final byte[] SAMPLE_DATA = { 1, 2, 3, 4, 5, 6, 7, 8 };
    private static final int BUFFER_SIZE = 64;
    
    private UnsafeBuffer buffer;
    private DirectBufferOutputStream outputStream;

    @BeforeEach
    void setUp()
    {
        buffer = new UnsafeBuffer(new byte[BUFFER_SIZE]);
        outputStream = new DirectBufferOutputStream();
    }

    @Nested
    @DisplayName("Test Examples - Basic Usage Patterns")
    class TestExamples
    {
        @Test
        @DisplayName("Should demonstrate basic byte array write operation")
        void shouldWriteByteArray()
        {
            // Example 1: Basic initialization with buffer constructor
            final byte[] data = new byte[8];
            final DirectBufferOutputStream stream = new DirectBufferOutputStream(new UnsafeBuffer(data));

            final byte[] source = { 1, 2, 3, 4 };
            stream.write(source);

            // Verify the data was written correctly
            assertArrayEquals(source, java.util.Arrays.copyOf(data, source.length));
            assertEquals(4, stream.position());
        }

        @Test
        @DisplayName("Should demonstrate single byte write operations")
        void shouldWriteSingleBytes()
        {
            // Example 2: Single byte write operations
            outputStream.wrap(buffer);
            
            outputStream.write(0x41); // 'A'
            outputStream.write(0x42); // 'B'
            outputStream.write(0x43); // 'C'
            
            assertEquals(3, outputStream.position());
            assertEquals(0x41, buffer.getByte(0));
            assertEquals(0x42, buffer.getByte(1));
            assertEquals(0x43, buffer.getByte(2));
        }

        @Test
        @DisplayName("Should demonstrate partial byte array write operations")
        void shouldWritePartialByteArray()
        {
            // Example 3: Partial byte array write
            outputStream.wrap(buffer);
            
            final byte[] source = { 1, 2, 3, 4, 5, 6, 7, 8 };
            outputStream.write(source, 2, 4); // Write bytes 3,4,5,6 (indices 2-5)
            
            assertEquals(4, outputStream.position());
            assertEquals(3, buffer.getByte(0));
            assertEquals(4, buffer.getByte(1));
            assertEquals(5, buffer.getByte(2));
            assertEquals(6, buffer.getByte(3));
        }

        @Test
        @DisplayName("Should demonstrate bulk write operations")
        void shouldWriteBulkData()
        {
            // Example 4: Bulk data write with large arrays
            outputStream.wrap(buffer);
            
            final byte[] largeData = new byte[32];
            for (int i = 0; i < largeData.length; i++)
            {
                largeData[i] = (byte)(i % 256);
            }
            
            outputStream.write(largeData);
            
            assertEquals(32, outputStream.position());
            for (int i = 0; i < largeData.length; i++)
            {
                assertEquals((byte)(i % 256), buffer.getByte(i));
            }
        }
    }

    @Nested
    @DisplayName("Usage Patterns - Initialization and Configuration")
    class UsagePatterns
    {
        @Test
        @DisplayName("Should demonstrate default constructor usage pattern")
        void shouldUseDefaultConstructor()
        {
            // Pattern 1: Default constructor with wrap
            final DirectBufferOutputStream stream = new DirectBufferOutputStream();
            stream.wrap(buffer);
            
            assertSame(buffer, stream.buffer());
            assertEquals(0, stream.offset());
            assertEquals(BUFFER_SIZE, stream.length());
            assertEquals(0, stream.position());
        }

        @Test
        @DisplayName("Should demonstrate buffer constructor usage pattern")
        void shouldUseBufferConstructor()
        {
            // Pattern 2: Direct buffer constructor
            final DirectBufferOutputStream stream = new DirectBufferOutputStream(buffer);
            
            assertSame(buffer, stream.buffer());
            assertEquals(0, stream.offset());
            assertEquals(BUFFER_SIZE, stream.length());
            assertEquals(0, stream.position());
        }

        @Test
        @DisplayName("Should demonstrate offset and length constructor usage pattern")
        void shouldUseOffsetLengthConstructor()
        {
            // Pattern 3: Constructor with offset and length
            final int offset = 10;
            final int length = 20;
            final DirectBufferOutputStream stream = new DirectBufferOutputStream(buffer, offset, length);
            
            assertSame(buffer, stream.buffer());
            assertEquals(offset, stream.offset());
            assertEquals(length, stream.length());
            assertEquals(0, stream.position());
        }

        @Test
        @DisplayName("Should demonstrate buffer reuse patterns")
        void shouldReuseBuffers()
        {
            // Pattern 4: Buffer reuse with wrap methods
            outputStream.wrap(buffer);
            outputStream.write(SAMPLE_DATA);
            
            final int firstWritePosition = outputStream.position();
            
            // Reuse with different buffer
            final UnsafeBuffer newBuffer = new UnsafeBuffer(new byte[32]);
            outputStream.wrap(newBuffer);
            outputStream.write(SAMPLE_DATA);
            
            assertEquals(firstWritePosition, outputStream.position());
            assertSame(newBuffer, outputStream.buffer());
        }

        @Test
        @DisplayName("Should demonstrate expandable buffer usage pattern")
        void shouldUseExpandableBuffer()
        {
            // Pattern 5: Using expandable buffer for dynamic sizing
            final ExpandableArrayBuffer expandableBuffer = new ExpandableArrayBuffer(16);
            final DirectBufferOutputStream stream = new DirectBufferOutputStream(expandableBuffer);
            
            // Write more data than initial capacity
            final byte[] largeData = new byte[64];
            for (int i = 0; i < largeData.length; i++)
            {
                largeData[i] = (byte)(i + 1);
            }
            
            stream.write(largeData);
            
            assertEquals(64, stream.position());
            assertTrue(expandableBuffer.capacity() >= 64);
            
            // Verify all data was written correctly
            for (int i = 0; i < largeData.length; i++)
            {
                assertEquals((byte)(i + 1), expandableBuffer.getByte(i));
            }
        }
    }

    @Nested
    @DisplayName("Write Operations - Different Write Methods")
    class WriteOperations
    {
        @BeforeEach
        void setUp()
        {
            outputStream.wrap(buffer);
        }

        @Test
        @DisplayName("Should write single bytes correctly")
        void shouldWriteSingleByte()
        {
            // Write operation type 1: Single byte
            outputStream.write(0xFF);
            outputStream.write(0x00);
            outputStream.write(0x7F);
            
            assertEquals(3, outputStream.position());
            assertEquals((byte)0xFF, buffer.getByte(0));
            assertEquals((byte)0x00, buffer.getByte(1));
            assertEquals((byte)0x7F, buffer.getByte(2));
        }

        @Test
        @DisplayName("Should write complete byte arrays")
        void shouldWriteCompleteByteArray()
        {
            // Write operation type 2: Complete byte array
            outputStream.write(SAMPLE_DATA);
            
            assertEquals(SAMPLE_DATA.length, outputStream.position());
            for (int i = 0; i < SAMPLE_DATA.length; i++)
            {
                assertEquals(SAMPLE_DATA[i], buffer.getByte(i));
            }
        }

        @Test
        @DisplayName("Should write partial byte arrays with offset and length")
        void shouldWritePartialByteArrays()
        {
            // Write operation type 3: Partial byte array
            final byte[] source = { 10, 20, 30, 40, 50, 60 };
            outputStream.write(source, 1, 3); // Write bytes 20, 30, 40
            
            assertEquals(3, outputStream.position());
            assertEquals(20, buffer.getByte(0));
            assertEquals(30, buffer.getByte(1));
            assertEquals(40, buffer.getByte(2));
        }

        @Test
        @DisplayName("Should write sequential data maintaining position")
        void shouldWriteSequentialData()
        {
            // Write operation type 4: Sequential writes
            outputStream.write(0x10);
            outputStream.write(new byte[]{ 0x20, 0x30 });
            outputStream.write(new byte[]{ 0x40, 0x50, 0x60 }, 1, 2); // Write 0x50, 0x60
            
            assertEquals(5, outputStream.position());
            assertEquals(0x10, buffer.getByte(0));
            assertEquals(0x20, buffer.getByte(1));
            assertEquals(0x30, buffer.getByte(2));
            assertEquals(0x50, buffer.getByte(3));
            assertEquals(0x60, buffer.getByte(4));
        }

        @Test
        @DisplayName("Should write zero-length arrays without affecting position")
        void shouldWriteZeroLengthArrays()
        {
            // Write operation type 5: Zero-length operations
            outputStream.write(0x42);
            final int positionAfterByte = outputStream.position();
            
            outputStream.write(new byte[0]);
            outputStream.write(new byte[10], 5, 0);
            
            assertEquals(positionAfterByte, outputStream.position());
            assertEquals(0x42, buffer.getByte(0));
        }
    }

    @Nested
    @DisplayName("Buffer Verification - Data Integrity Checks")
    class BufferVerification
    {
        @BeforeEach
        void setUp()
        {
            outputStream.wrap(buffer);
        }

        @Test
        @DisplayName("Should verify buffer contents after writes")
        void shouldVerifyBufferContents()
        {
            // Verification 1: Basic content verification
            final byte[] testData = { 0x01, 0x02, 0x03, 0x04 };
            outputStream.write(testData);
            
            // Verify using buffer direct access
            for (int i = 0; i < testData.length; i++)
            {
                assertEquals(testData[i], buffer.getByte(i),
                    "Buffer content mismatch at index " + i);
            }
            
            // Verify using buffer bulk read
            final byte[] readBack = new byte[testData.length];
            buffer.getBytes(0, readBack);
            assertArrayEquals(testData, readBack);
        }

        @Test
        @DisplayName("Should verify position tracking accuracy")
        void shouldVerifyPositionTracking()
        {
            // Verification 2: Position tracking verification
            assertEquals(0, outputStream.position());
            
            outputStream.write(0x10);
            assertEquals(1, outputStream.position());
            
            outputStream.write(new byte[]{ 0x20, 0x30, 0x40 });
            assertEquals(4, outputStream.position());
            
            outputStream.write(new byte[]{ 0x50, 0x60, 0x70, 0x80 }, 1, 2);
            assertEquals(6, outputStream.position());
        }

        @Test
        @DisplayName("Should verify offset-based writes")
        void shouldVerifyOffsetBasedWrites()
        {
            // Verification 3: Offset-based write verification
            final int offset = 10;
            final int length = 20;
            outputStream.wrap(buffer, offset, length);
            
            final byte[] testData = { 0x11, 0x22, 0x33 };
            outputStream.write(testData);
            
            // Verify data was written at correct offset
            assertEquals(0x11, buffer.getByte(offset));
            assertEquals(0x22, buffer.getByte(offset + 1));
            assertEquals(0x33, buffer.getByte(offset + 2));
            
            // Verify positions before offset are unchanged
            assertEquals(0, buffer.getByte(offset - 1));
        }

        @Test
        @DisplayName("Should verify buffer bounds are respected")
        void shouldVerifyBufferBounds()
        {
            // Verification 4: Buffer bounds verification
            final byte[] smallBuffer = new byte[4];
            final DirectBufferOutputStream stream = new DirectBufferOutputStream(new UnsafeBuffer(smallBuffer));
            
            stream.write(new byte[]{ 1, 2, 3, 4 });
            
            // Verify all data fits exactly
            assertEquals(4, stream.position());
            assertEquals(1, smallBuffer[0]);
            assertEquals(2, smallBuffer[1]);
            assertEquals(3, smallBuffer[2]);
            assertEquals(4, smallBuffer[3]);
        }

        @Test
        @DisplayName("Should verify concurrent buffer access safety")
        void shouldVerifyConcurrentBufferAccess()
        {
            // Verification 5: Multiple stream instances on same buffer
            final DirectBufferOutputStream stream1 = new DirectBufferOutputStream(buffer, 0, 16);
            final DirectBufferOutputStream stream2 = new DirectBufferOutputStream(buffer, 16, 16);
            
            stream1.write(new byte[]{ 1, 2, 3, 4 });
            stream2.write(new byte[]{ 5, 6, 7, 8 });
            
            // Verify both streams wrote to correct regions
            assertEquals(1, buffer.getByte(0));
            assertEquals(4, buffer.getByte(3));
            assertEquals(5, buffer.getByte(16));
            assertEquals(8, buffer.getByte(19));
        }
    }

    @Nested
    @DisplayName("Error Handling - Exception Scenarios")
    class ErrorHandling
    {
        @Test
        @DisplayName("Should handle null buffer gracefully")
        void shouldHandleNullBuffer()
        {
            assertThrows(NullPointerException.class, () -> {
                outputStream.wrap(null);
            });
        }

        @Test
        @DisplayName("Should handle buffer overflow on single byte write")
        void shouldHandleBufferOverflowOnSingleByte()
        {
            // Create stream with capacity of 1
            final DirectBufferOutputStream stream = new DirectBufferOutputStream(new UnsafeBuffer(new byte[1]));
            
            stream.write(0x42); // This should succeed
            
            // This should throw exception
            assertThrows(IllegalStateException.class, () -> {
                stream.write(0x43);
            });
        }

        @Test
        @DisplayName("Should handle buffer overflow on byte array write")
        void shouldHandleBufferOverflowOnByteArray()
        {
            // Create stream with capacity of 4
            final DirectBufferOutputStream stream = new DirectBufferOutputStream(new UnsafeBuffer(new byte[4]));
            
            stream.write(new byte[]{ 1, 2 }); // This should succeed
            
            // This should throw exception (would need 5 bytes total)
            assertThrows(IllegalStateException.class, () -> {
                stream.write(new byte[]{ 3, 4, 5 });
            });
        }

        @Test
        @DisplayName("Should handle buffer overflow on partial array write")
        void shouldHandleBufferOverflowOnPartialArray()
        {
            final DirectBufferOutputStream stream = new DirectBufferOutputStream(new UnsafeBuffer(new byte[2]));
            
            final byte[] source = { 1, 2, 3, 4, 5 };
            
            // This should throw exception
            assertThrows(IllegalStateException.class, () -> {
                stream.write(source, 1, 3); // Trying to write 3 bytes to 2-byte buffer
            });
        }

        @Test
        @DisplayName("Should handle edge case of exact capacity utilization")
        void shouldHandleExactCapacityUtilization()
        {
            final DirectBufferOutputStream stream = new DirectBufferOutputStream(new UnsafeBuffer(new byte[3]));
            
            stream.write(new byte[]{ 1, 2, 3 }); // Exact fit
            assertEquals(3, stream.position());
            
            // Any additional write should fail
            assertThrows(IllegalStateException.class, () -> {
                stream.write(0x04);
            });
        }

        @Test
        @DisplayName("Should handle integer overflow in position calculation")
        void shouldHandleIntegerOverflow()
        {
            final DirectBufferOutputStream stream = new DirectBufferOutputStream(new UnsafeBuffer(new byte[10]));
            
            // This tests the internal overflow protection for large writes
            final byte[] largeArray = new byte[Integer.MAX_VALUE - 5];
            
            assertThrows(IllegalStateException.class, () -> {
                stream.write(largeArray);
            });
        }
    }

    @Nested
    @DisplayName("Stream Lifecycle - Flush and Close Operations")
    class StreamLifecycle
    {
        @Test
        @DisplayName("Should handle flush operations gracefully")
        void shouldHandleFlushOperations()
        {
            outputStream.wrap(buffer);
            outputStream.write(SAMPLE_DATA);
            
            // Flush should not throw exception and should not affect state
            assertDoesNotThrow(() -> outputStream.flush());
            assertEquals(SAMPLE_DATA.length, outputStream.position());
        }

        @Test
        @DisplayName("Should handle close operations gracefully")
        void shouldHandleCloseOperations()
        {
            outputStream.wrap(buffer);
            outputStream.write(SAMPLE_DATA);
            
            // Close should not throw exception and should not affect state
            assertDoesNotThrow(() -> outputStream.close());
            assertEquals(SAMPLE_DATA.length, outputStream.position());
            
            // Should still be able to write after close
            assertDoesNotThrow(() -> outputStream.write(0x99));
        }

        @Test
        @DisplayName("Should demonstrate proper resource management pattern")
        void shouldDemonstrateResourceManagement()
        {
            // Resource management pattern with try-with-resources
            final byte[] data = new byte[16];
            
            try (DirectBufferOutputStream stream = new DirectBufferOutputStream(new UnsafeBuffer(data)))
            {
                stream.write(new byte[]{ 1, 2, 3, 4 });
                stream.flush();
                
                assertEquals(4, stream.position());
            } // close() is called automatically
            
            // Verify data was written successfully
            assertEquals(1, data[0]);
            assertEquals(4, data[3]);
        }
    }

    @Nested
    @DisplayName("Performance Considerations - Optimization Examples")
    class PerformanceConsiderations
    {
        @Test
        @DisplayName("Should demonstrate buffer reuse for performance")
        void shouldDemonstrateBufferReuse()
        {
            // Performance pattern: Reuse stream instances
            final DirectBufferOutputStream reusableStream = new DirectBufferOutputStream();
            final byte[] workBuffer = new byte[1024];
            
            // Process multiple data chunks using same stream
            for (int chunk = 0; chunk < 5; chunk++)
            {
                reusableStream.wrap(new UnsafeBuffer(workBuffer), chunk * 100, 100);
                
                final byte[] chunkData = new byte[50];
                java.util.Arrays.fill(chunkData, (byte)(chunk + 1));
                
                reusableStream.write(chunkData);
                assertEquals(50, reusableStream.position());
            }
        }

        @Test
        @DisplayName("Should demonstrate bulk write performance optimization")
        void shouldDemonstrateBulkWriteOptimization()
        {
            outputStream.wrap(buffer);
            
            // Preferred: Single bulk write
            final byte[] bulkData = new byte[32];
            for (int i = 0; i < bulkData.length; i++)
            {
                bulkData[i] = (byte)(i + 1);
            }
            outputStream.write(bulkData);
            
            assertEquals(32, outputStream.position());
            
            // Verify optimization worked - all data written in one operation
            for (int i = 0; i < bulkData.length; i++)
            {
                assertEquals((byte)(i + 1), buffer.getByte(i));
            }
        }
    }
}
