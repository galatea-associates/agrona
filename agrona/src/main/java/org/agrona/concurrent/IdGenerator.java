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
package org.agrona.concurrent;

/**
 * Abstraction for generating unique 64-bit identifiers within concurrent systems.
 * <p>
 * This interface provides a contract for ID generation strategies used throughout
 * Agrona's concurrent utilities, including ring buffers, agents, and lock-free data
 * structures that require unique correlation IDs or sequence numbers.
 * <p>
 * Implementations must guarantee uniqueness within their intended scope (process-local,
 * node-local, or globally unique depending on the specific implementation strategy).
 * The interface supports various ID generation algorithms including simple atomic counters,
 * timestamp-based approaches, and distributed algorithms like Snowflake.
 * <p>
 * <b>Threading Considerations:</b><br>
 * Implementations must be thread-safe as they will typically be accessed concurrently
 * from multiple producer threads in high-throughput scenarios. The interface design
 * assumes wait-free or lock-free implementations to maintain predictable latency
 * characteristics essential for real-time systems.
 * <p>
 * <b>Performance Requirements:</b><br>
 * Implementations should minimize allocation overhead and provide constant-time
 * operation suitable for microsecond-latency systems. The 64-bit return value
 * provides sufficient range for high-frequency generation without overflow concerns
 * in practical deployments.
 * <p>
 * <b>Usage Examples:</b><br>
 * <pre>
 * // Simple atomic counter implementation
 * IdGenerator generator = new AtomicCounterIdGenerator();
 * long correlationId = generator.nextId();
 * 
 * // Usage in ring buffer messaging
 * long messageId = idGenerator.nextId();
 * ringBuffer.tryClaim(MESSAGE_TYPE, length, messageId);
 * </pre>
 *
 * @see org.agrona.concurrent.AtomicCounter
 * @see org.agrona.concurrent.ringbuffer.RingBuffer
 */
@FunctionalInterface
public interface IdGenerator
{
    /**
     * Generate the next unique identifier.
     * <p>
     * This method must return a unique 64-bit value within the scope guaranteed
     * by the specific implementation. Successive calls to this method must never
     * return the same value during the lifetime of the generator instance.
     * <p>
     * <b>Thread Safety:</b> This method must be safe for concurrent access by
     * multiple threads without external synchronization.
     * <p>
     * <b>Performance Characteristics:</b> Implementations should strive for
     * constant-time complexity and minimal allocation overhead to support
     * high-frequency invocation in latency-sensitive applications.
     * <p>
     * <b>Return Value Range:</b> The full 64-bit signed long range is available,
     * though specific implementations may choose to utilize only a subset for
     * their encoding scheme (e.g., timestamp + sequence + node ID components).
     *
     * @return the next unique 64-bit identifier, guaranteed unique within the
     *         implementation's defined scope
     */
    long nextId();
}
