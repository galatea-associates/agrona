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

/**
 * I/O adapter layer that bridges Agrona's high-performance {@link org.agrona.DirectBuffer} and
 * {@link org.agrona.MutableDirectBuffer} abstractions to the standard Java I/O APIs in a zero-allocation,
 * zero-copy manner.
 * 
 * <h2>Package Overview</h2>
 * This package provides seamless integration between Agrona's off-heap buffer implementations and
 * standard Java I/O streams ({@link java.io.InputStream}, {@link java.io.OutputStream}, and
 * {@link java.io.DataInput}). All implementations are designed for high-performance, low-GC environments
 * where zero-copy operations and direct memory access are critical.
 * 
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link org.agrona.io.DirectBufferDataInput} - A {@link java.io.DataInput} implementation over a
 *     {@link org.agrona.DirectBuffer} that enables sequential reading of primitive types, strings, and
 *     byte arrays with configurable endianness and strict bounds checking.</li>
 * <li>{@link org.agrona.io.DirectBufferInputStream} - A {@link java.io.InputStream} implementation that
 *     provides sequential, zero-copy reading of DirectBuffer memory segments with standard stream operations.</li>
 * <li>{@link org.agrona.io.DirectBufferOutputStream} - A {@link java.io.OutputStream} implementation that
 *     writes raw bytes directly into a preallocated {@link org.agrona.MutableDirectBuffer} region with
 *     boundary checking and overflow protection.</li>
 * <li>{@link org.agrona.io.ExpandableDirectBufferOutputStream} - An {@link java.io.OutputStream} for
 *     writing into expandable off-heap buffers with automatic growth, ideal for dynamic serialization
 *     scenarios.</li>
 * </ul>
 * 
 * <h2>Usage Patterns</h2>
 * <h3>Reading from DirectBuffer as DataInput</h3>
 * <pre>{@code
 * DirectBuffer buffer = new UnsafeBuffer(byteArray);
 * DirectBufferDataInput dataInput = new DirectBufferDataInput();
 * dataInput.wrap(buffer);
 * 
 * int value = dataInput.readInt();
 * String text = dataInput.readStringUTF8();
 * }</pre>
 * 
 * <h3>Reading from DirectBuffer as InputStream</h3>
 * <pre>{@code
 * DirectBuffer buffer = new UnsafeBuffer(byteArray);
 * DirectBufferInputStream inputStream = new DirectBufferInputStream();
 * inputStream.wrap(buffer, 0, buffer.capacity());
 * 
 * int bytesRead = inputStream.read(targetArray);
 * }</pre>
 * 
 * <h3>Writing to MutableDirectBuffer as OutputStream</h3>
 * <pre>{@code
 * MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
 * DirectBufferOutputStream outputStream = new DirectBufferOutputStream();
 * outputStream.wrap(buffer, 0, buffer.capacity());
 * 
 * outputStream.write(sourceArray);
 * }</pre>
 * 
 * <h3>Writing to Expandable Buffer</h3>
 * <pre>{@code
 * ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
 * ExpandableDirectBufferOutputStream outputStream = new ExpandableDirectBufferOutputStream();
 * outputStream.wrap(buffer);
 * 
 * // Buffer will grow automatically as data is written
 * outputStream.write(largeDataArray);
 * }</pre>
 * 
 * <h2>Performance Characteristics</h2>
 * <ul>
 * <li><strong>Zero-Copy Operations</strong>: All implementations operate directly on the underlying buffer
 *     memory without intermediate array allocations.</li>
 * <li><strong>Bounds Checking</strong>: Strict boundary validation prevents buffer overruns and underruns
 *     using {@link org.agrona.BitUtil} constants.</li>
 * <li><strong>Endianness Support</strong>: Configurable byte order (BIG_ENDIAN for JDK compatibility,
 *     LITTLE_ENDIAN for Agrona buffer conventions).</li>
 * <li><strong>No Thread Safety</strong>: All classes are designed for single-threaded use to maximize
 *     performance. External synchronization is required for concurrent access.</li>
 * </ul>
 * 
 * <h2>Integration Context</h2>
 * This package is commonly used by:
 * <ul>
 * <li>Archive readers and protocol codecs that deserialize messages from DirectBuffers</li>
 * <li>Network frame encoders and decoders in messaging systems</li>
 * <li>Serialization utilities that require OutputStream abstraction over off-heap buffers</li>
 * <li>Test harnesses and benchmarks that need standard I/O interfaces over Agrona buffers</li>
 * <li>Integration with third-party libraries expecting standard Java I/O APIs</li>
 * </ul>
 * 
 * <h2>Exception Handling</h2>
 * <ul>
 * <li>{@link java.io.EOFException} - Thrown by DataInput implementations when attempting to read beyond
 *     buffer boundaries, conforming to the DataInput contract.</li>
 * <li>{@link java.lang.IllegalStateException} - Thrown by OutputStream implementations when attempting
 *     to write beyond buffer capacity in non-expandable buffers.</li>
 * <li>{@link java.io.IOException} - Propagated for Appendable-based operations in string reading methods.</li>
 * </ul>
 * 
 * <h2>Dependencies</h2>
 * This package depends on:
 * <ul>
 * <li>Core Agrona interfaces: {@link org.agrona.DirectBuffer}, {@link org.agrona.MutableDirectBuffer}</li>
 * <li>Agrona utilities: {@link org.agrona.BitUtil} for size constants and bounds checking</li>
 * <li>Standard Java I/O: {@link java.io.DataInput}, {@link java.io.InputStream}, {@link java.io.OutputStream}</li>
 * <li>Java NIO: {@link java.nio.ByteOrder} for endianness configuration</li>
 * </ul>
 * 
 * <p><strong>License:</strong> Apache License, Version 2.0</p>
 * <p><strong>Copyright:</strong> 2014-2025 Real Logic Limited</p>
 * 
 * @since 0.3.0
 */
package org.agrona.io;